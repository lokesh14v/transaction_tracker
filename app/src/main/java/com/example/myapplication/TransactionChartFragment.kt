package com.example.myapplication

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import com.example.myapplication.databinding.FragmentTransactionChartBinding
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.github.mikephil.charting.utils.ColorTemplate

class TransactionChartFragment : Fragment() {

    private var _binding: FragmentTransactionChartBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentTransactionChartBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val transactionDao = AppDatabase.getDatabase(requireContext()).transactionDao()

        // Observe distinct banks and populate the spinner
        transactionDao.getDistinctBanks().observe(viewLifecycleOwner, Observer {
            val banks = mutableListOf("All Banks")
            banks.addAll(it)
            val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, banks)
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            binding.bankSpinner.adapter = adapter
        })

        // Observe selected bank and update chart
        binding.bankSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selectedBank = parent?.getItemAtPosition(position).toString()
                if (selectedBank == "All Banks") {
                    transactionDao.getAllTransactions().observe(viewLifecycleOwner, Observer {
                        val categoryAmounts = it.groupingBy { transaction -> transaction.category }.fold(0.0) { acc, transaction -> acc + transaction.amount }
                        setupPieChart(categoryAmounts)
                    })
                } else {
                    transactionDao.getAllTransactions().observe(viewLifecycleOwner, Observer {
                        val filteredTransactions = it.filter { transaction -> transaction.bank == selectedBank }
                        val categoryAmounts = filteredTransactions.groupingBy { transaction -> transaction.category }.fold(0.0) { acc, transaction -> acc + transaction.amount }
                        setupPieChart(categoryAmounts)
                    })
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                // Do nothing
            }
        }
    }

    private fun setupPieChart(categoryAmounts: Map<TransactionCategory, Double>) {
        val entries = ArrayList<PieEntry>()
        for ((category, amount) in categoryAmounts) {
            entries.add(PieEntry(amount.toFloat(), category.name))
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