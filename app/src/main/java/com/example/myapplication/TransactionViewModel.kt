package com.example.ExpenseTracker

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.TransactionDao
import kotlinx.coroutines.launch

class TransactionViewModel(private val transactionDao: TransactionDao) : ViewModel() {

    private val _transactions = MutableLiveData<List<Transaction>>()
    val transactions: LiveData<List<Transaction>> = _transactions

    private val _totalSpend = MutableLiveData<Double>()
    val totalSpend: LiveData<Double> = _totalSpend

    private val _totalCredit = MutableLiveData<Double>()
    val totalCredit: LiveData<Double> = _totalCredit

    private val _dateRange = MutableLiveData<Pair<Long, Long>?>()
    val dateRange: LiveData<Pair<Long, Long>?> = _dateRange

    private val _selectedBank = MutableLiveData<String?>()
    val selectedBank: LiveData<String?> = _selectedBank

    private var currentLiveData: LiveData<List<Transaction>>? = null

    fun loadTransactions() {
        viewModelScope.launch {
            val currentBank = _selectedBank.value
            val currentDateRange = _dateRange.value

            val newLiveData = if (currentDateRange != null) {
                if (currentBank == null || currentBank == "All Banks") {
                    transactionDao.getTransactionsBetweenDates(currentDateRange.first, currentDateRange.second)
                } else {
                    transactionDao.getTransactionsByBankAndDateRange(currentBank, currentDateRange.first, currentDateRange.second)
                }
            } else {
                if (currentBank == null || currentBank == "All Banks") {
                    transactionDao.getAllTransactions()
                } else {
                    transactionDao.getTransactionsByBank(currentBank)
                }
            }

            // Remove previous observer if it exists
            currentLiveData?.removeObserver(transactionsObserver)

            // Set new LiveData and observe it
            currentLiveData = newLiveData
            currentLiveData?.observeForever(transactionsObserver)
        }
    }

    private val transactionsObserver = Observer<List<Transaction>> { transactionsList ->
        Log.d(
            "TransactionViewModel",
            "loadTransactions: ${transactionsList.size} transactions loaded"
        )
        _transactions.postValue(transactionsList)
        calculateTotals(transactionsList)
    }

    fun setDateRange(startDate: Long, endDate: Long) {
        _dateRange.value = Pair(startDate, endDate)
        loadTransactions() // Trigger load after setting date range
    }

    fun clearDateRange() {
        _dateRange.value = null
        loadTransactions() // Trigger load after clearing date range
    }

    fun setSelectedBank(bank: String?) {
        _selectedBank.value = bank
        loadTransactions() // Trigger load after setting bank
    }

    fun clearSelectedBank() {
        _selectedBank.value = null
        loadTransactions() // Trigger load after clearing bank
    }

    // Remove loadAllTransactions and loadTransactionsByDateRange as they are now handled by loadTransactions

    private fun calculateTotals(transactions: List<Transaction>) {
        var spend = 0.0
        var credit = 0.0
        for (transaction in transactions) {
            if (transaction.type == TransactionType.DEBIT) {
                spend += transaction.amount
            } else if (transaction.type == TransactionType.CREDIT) {
                credit += transaction.amount
            }
        }
        _totalSpend.postValue(spend)
        _totalCredit.postValue(credit)
    }

    fun addTransaction(transaction: Transaction) {
        viewModelScope.launch {
            transactionDao.insert(transaction)
        }
    }

    fun updateTransactionCategory(transactionId: Int, newCategory: TransactionCategory, userDefinedCategoryName: String?) {
        viewModelScope.launch {
            transactionDao.updateCategory(transactionId, newCategory, userDefinedCategoryName)
        }
    }

    fun delete(transaction: Transaction) {
        viewModelScope.launch {
            transactionDao.delete(transaction)
        }
    }
}
