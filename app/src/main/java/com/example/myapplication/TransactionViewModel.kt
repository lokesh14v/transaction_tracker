package com.example.myapplication

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

class TransactionViewModel(private val transactionDao: TransactionDao) : ViewModel() {

    private val _transactions = MutableLiveData<List<Transaction>>()
    val transactions: LiveData<List<Transaction>> = _transactions

    fun loadTransactionsByDateRange(startDate: Long, endDate: Long, bank: String? = null) {
        viewModelScope.launch {
            if (bank == null || bank == "All Banks") {
                transactionDao.getTransactionsBetweenDates(startDate, endDate).observeForever {
                    _transactions.postValue(it)
                }
            } else {
                transactionDao.getTransactionsBetweenDates(startDate, endDate).observeForever {
                    _transactions.postValue(it.filter { transaction -> transaction.bank == bank })
                }
            }
        }
    }

    fun addTransaction(transaction: Transaction) {
        viewModelScope.launch {
            transactionDao.insert(transaction)
        }
    }
}
