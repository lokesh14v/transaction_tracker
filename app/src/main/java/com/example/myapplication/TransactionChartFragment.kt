import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import com.example.myapplication.AppDatabase
import com.example.myapplication.TransactionCategory
import com.example.myapplication.TransactionViewModel
import com.example.myapplication.TransactionViewModelFactory
import com.example.myapplication.databinding.FragmentTransactionChartBinding
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.github.mikephil.charting.utils.ColorTemplate
import java.util.Calendar

class TransactionChartFragment : Fragment() {

    private var _binding: FragmentTransactionChartBinding? = null
    private val binding get() = _binding!!
    private lateinit var viewModel: TransactionViewModel

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTransactionChartBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val transactionDao = AppDatabase.getDatabase(requireContext()).transactionDao()
        val viewModelFactory = TransactionViewModelFactory(transactionDao)
        viewModel = ViewModelProvider(this, viewModelFactory).get(TransactionViewModel::class.java)

        // Observe transactions
        viewModel.transactions.observe(viewLifecycleOwner, Observer { transactions ->
            val selectedBank = binding.bankSpinner.selectedItem?.toString()
            val filteredTransactions = if (selectedBank == null || selectedBank == "All Banks") {
                transactions
            } else {
                transactions.filter { transaction -> transaction.bank == selectedBank }
            }

            val categoryAmounts = filteredTransactions.groupingBy { it.category }
                .fold(0.0) { acc, transaction -> acc + transaction.amount }

            setupPieChart(categoryAmounts)
        })

        // Load transactions for the last 30 days
        val calendar = Calendar.getInstance()
        val endDate = calendar.timeInMillis
        calendar.add(Calendar.DAY_OF_YEAR, -30)
        val startDate = calendar.timeInMillis
        viewModel.loadTransactionsByDateRange(startDate, endDate)

        // Populate bank spinner
        transactionDao.getDistinctBanks().observe(viewLifecycleOwner, Observer { banksList ->
            val banks = mutableListOf("All Banks")
            banks.addAll(banksList)
            val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, banks)
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            binding.bankSpinner.adapter = adapter
        })

        // Spinner listener
        binding.bankSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selectedBank = parent?.getItemAtPosition(position).toString()
                val calendar = Calendar.getInstance()
                val endDate = calendar.timeInMillis
                calendar.add(Calendar.DAY_OF_YEAR, -30)
                val startDate = calendar.timeInMillis
                viewModel.loadTransactionsByDateRange(startDate, endDate, selectedBank)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                // Do nothing
            }
        }
    }

    private fun setupPieChart(categoryAmounts: Map<TransactionCategory, Double>) {
        val entries = ArrayList<PieEntry>()
        for ((category, amount) in categoryAmounts) {
            entries.add(PieEntry(amount.toFloat(), category))
        }

        val dataSet = PieDataSet(entries, "Transaction Categories")
        dataSet.colors = ColorTemplate.MATERIAL_COLORS.toList()
        dataSet.valueTextSize = 12f
        dataSet.valueTextColor = Color.BLACK

        val data = PieData(dataSet)
        binding.pieChart.data = data
        binding.pieChart.description.isEnabled = false
        binding.pieChart.setEntryLabelColor(Color.BLACK)
        binding.pieChart.animateY(1000)
        binding.pieChart.invalidate()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
