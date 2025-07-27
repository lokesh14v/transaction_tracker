package com.example.ExpenseTracker

import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.example.ExpenseTracker.databinding.FragmentTransactionListBinding
import com.google.android.material.datepicker.MaterialDatePicker
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class TransactionListFragment : Fragment(), CategorySelectionDialogFragment.CategorySelectedListener, AddNewCategoryDialogFragment.AddNewCategoryListener {

    private var _binding: FragmentTransactionListBinding? = null
    private val binding get() = _binding!!
    private lateinit var transactionAdapter: TransactionAdapter
    private lateinit var viewModel: TransactionViewModel
    private var currentTransactionForCategorySelection: Transaction? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentTransactionListBinding.inflate(inflater, container, false)
        setHasOptionsMenu(true)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        transactionAdapter = TransactionAdapter { transaction ->
            // Handle category click
            currentTransactionForCategorySelection = transaction
            val dialog = CategorySelectionDialogFragment.newInstance(transaction.id, transaction.category, this)
            dialog.show(parentFragmentManager, "CategorySelectionDialogFragment")
        }
        binding.transactionRecyclerView.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = transactionAdapter
        }

        val transactionDao = AppDatabase.getDatabase(requireContext()).transactionDao()
        val viewModelFactory = TransactionViewModelFactory(transactionDao)
        viewModel = ViewModelProvider(this, viewModelFactory).get(TransactionViewModel::class.java)

        viewModel.transactions.observe(viewLifecycleOwner) { transactions ->
            transactionAdapter.submitList(transactions)
        }

        viewModel.totalSpend.observe(viewLifecycleOwner) { totalSpend ->
            binding.totalSpendTextView.text = String.format(Locale.getDefault(), "₹ %.2f", totalSpend)
        }

        viewModel.totalCredit.observe(viewLifecycleOwner) { totalCredit ->
            binding.totalCreditTextView.text = String.format(Locale.getDefault(), "₹ %.2f", totalCredit)
        }

        // Default to last 30 days
        val calendar = Calendar.getInstance()
        val endDate = calendar.timeInMillis
        calendar.add(Calendar.DAY_OF_YEAR, -30)
        val startDate = calendar.timeInMillis
        viewModel.loadTransactionsByDateRange(startDate, endDate)
        updateDateRangeText(startDate, endDate)
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.menu_transaction_list, menu)
        super.onCreateOptionsMenu(menu, inflater)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_date_range -> {
                showDateRangePicker()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun showDateRangePicker() {
        val dateRangePicker =
            MaterialDatePicker.Builder.dateRangePicker()
                .setTitleText("Select dates")
                .build()

        dateRangePicker.addOnPositiveButtonClickListener { selection ->
            var startDate = selection.first
            var endDate = selection.second

            // If only one day is selected, adjust endDate to be the end of that day
            if (startDate == endDate) {
                val calendar = Calendar.getInstance()
                calendar.timeInMillis = endDate
                calendar.set(Calendar.HOUR_OF_DAY, 23)
                calendar.set(Calendar.MINUTE, 59)
                calendar.set(Calendar.SECOND, 59)
                calendar.set(Calendar.MILLISECOND, 999)
                endDate = calendar.timeInMillis
            }
            viewModel.loadTransactionsByDateRange(startDate, endDate)
            updateDateRangeText(startDate, endDate)
        }

        dateRangePicker.show(parentFragmentManager, "dateRangePicker")
    }

    private fun updateDateRangeText(startDate: Long, endDate: Long) {
        val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        binding.dateRangeTextView.text = "${sdf.format(Date(startDate))} - ${sdf.format(Date(endDate))}"
    }

    override fun onCategorySelected(transactionId: Int, newCategory: TransactionCategory, userDefinedCategoryName: String?) {
        viewModel.updateTransactionCategory(transactionId, newCategory, userDefinedCategoryName)
    }

    override fun onAddNewCategoryRequested(transactionId: Int) {
        val dialog = AddNewCategoryDialogFragment.newInstance(transactionId, this)
        dialog.show(parentFragmentManager, "AddNewCategoryDialogFragment")
    }

    override fun onNewCategoryAdded(transactionId: Int, newCategoryName: String) {
        viewModel.updateTransactionCategory(transactionId, TransactionCategory.UNKNOWN, newCategoryName)
        // Re-show the category selection dialog to refresh the list after a short delay
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Main) {
            kotlinx.coroutines.delay(200) // Small delay to allow database update to propagate
            currentTransactionForCategorySelection?.let {
                val dialog = CategorySelectionDialogFragment.newInstance(it.id, it.category, this@TransactionListFragment)
                dialog.show(parentFragmentManager, "CategorySelectionDialogFragment")
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
