package com.example.ExpenseTracker

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
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

    fun setDateRange(startDate: Long, endDate: Long) {
        _dateRange.value = Pair(startDate, endDate)
    }

    fun clearDateRange() {
        _dateRange.value = null
    }

    fun loadAllTransactions(bank: String? = null) {
        viewModelScope.launch {
            val liveData = if (bank == null || bank == "All Banks") {
                transactionDao.getAllTransactions()
            } else {
                transactionDao.getTransactionsByBank(bank)
            }
            liveData.observeForever { transactionsList ->
                _transactions.postValue(transactionsList)
                calculateTotals(transactionsList)
            }
        }
    }

    fun loadTransactionsByDateRange(startDate: Long, endDate: Long, bank: String? = null) {
        viewModelScope.launch {
            val liveData = if (bank == null || bank == "All Banks") {
                transactionDao.getTransactionsBetweenDates(startDate, endDate)
            } else {
                transactionDao.getTransactionsByBankAndDateRange(bank, startDate, endDate)
            }
            liveData.observeForever { transactionsList ->
                _transactions.postValue(transactionsList)
                calculateTotals(transactionsList)
            }
        }
    }

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
