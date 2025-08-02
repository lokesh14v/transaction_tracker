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

        transactionAdapter = TransactionAdapter(
            onCategoryClick = { transaction ->
                // Handle category click
                currentTransactionForCategorySelection = transaction
                val dialog = CategorySelectionDialogFragment.newInstance(transaction.id, transaction.category, this)
                dialog.show(parentFragmentManager, "CategorySelectionDialogFragment")
            },
            onDeleteClick = { transaction ->
                viewModel.delete(transaction)
            }
        )
        binding.transactionRecyclerView.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = transactionAdapter
        }

        viewModel = ViewModelProvider(requireActivity()).get(TransactionViewModel::class.java)

        viewModel.transactions.observe(viewLifecycleOwner) { transactions ->
            transactionAdapter.submitList(transactions)
        }

        viewModel.totalSpend.observe(viewLifecycleOwner) { totalSpend ->
            binding.totalSpendTextView.text = String.format(Locale.getDefault(), "₹ %.2f", totalSpend)
        }

        viewModel.totalCredit.observe(viewLifecycleOwner) { totalCredit ->
            binding.totalCreditTextView.text = String.format(Locale.getDefault(), "₹ %.2f", totalCredit)
        }

        viewModel.dateRange.observe(viewLifecycleOwner) { dateRange ->
            if (dateRange != null) {
                viewModel.loadTransactionsByDateRange(dateRange.first, dateRange.second)
                updateDateRangeText(dateRange.first, dateRange.second)
            } else {
                viewModel.loadAllTransactions()
                binding.dateRangeTextView.text = "All Transactions"
            }
        }
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
