package com.example.ExpenseTracker

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope

import androidx.lifecycle.ViewModelProvider
import com.example.ExpenseTracker.databinding.ActivityMainBinding
import com.google.android.material.tabs.TabLayoutMediator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.google.android.material.datepicker.MaterialDatePicker
import java.util.Calendar

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var viewModel: TransactionViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)

        val transactionDao = AppDatabase.getDatabase(this).transactionDao()
        val viewModelFactory = TransactionViewModelFactory(transactionDao)
        viewModel = ViewModelProvider(this, viewModelFactory).get(TransactionViewModel::class.java)

        val viewPagerAdapter = ViewPagerAdapter(this)
        binding.viewPager.adapter = viewPagerAdapter

        TabLayoutMediator(binding.tabLayout, binding.viewPager) {
            tab, position ->
            tab.text = when (position) {
                0 -> "Transactions"
                1 -> "Chart"
                else -> ""
            }
        }.attach()

        viewModel.dateRange.observe(this) { dateRange ->
            binding.chipLast30Days.isChecked = dateRange != null
        }

        binding.chipLast30Days.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                val calendar = Calendar.getInstance()
                val endDate = calendar.timeInMillis
                calendar.add(Calendar.DAY_OF_YEAR, -30)
                val startDate = calendar.timeInMillis
                viewModel.setDateRange(startDate, endDate)
            } else {
                viewModel.clearDateRange()
            }
        }

        binding.dateRangeIcon.setOnClickListener {
            showDateRangePicker()
        }

        checkAndRequestPermissions()
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
            viewModel.setDateRange(startDate, endDate)
        }

        dateRangePicker.show(supportFragmentManager, "dateRangePicker")
    }

    private fun checkAndRequestPermissions() {
        val requiredPermissions = listOf(
            Manifest.permission.READ_SMS,
            Manifest.permission.RECEIVE_SMS
        )

        val permissionsToRequest = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (permissionsToRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                this,
                permissionsToRequest.toTypedArray(),
                PERMISSION_REQUEST_CODE
            )
        } else {
            syncSmsAndDisplayCounts()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                syncSmsAndDisplayCounts()
            } else {
                println("SMS permissions denied.")
                // Show a user-facing message if needed
            }
        }
    }

    private fun syncSmsAndDisplayCounts() {
        lifecycleScope.launch(Dispatchers.IO) {
            // This method must ensure:
            // 1. If the app was just installed, fetch all transaction-related SMS.
            // 2. If already installed before, only fetch NEW ones.
            // 3. Check for duplicates in the database and avoid reprocessing.
            val (smsCount, processedTransactions) = SmsManager.syncSms(this@MainActivity)

            // Removed UI updates for smsCountTextView and transactionsProcessedTextView
            // as they are no longer in the layout.
        }
    }

    companion object {
        private const val PERMISSION_REQUEST_CODE = 100
    }
}

