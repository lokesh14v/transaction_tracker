package com.example.myapplication

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class TransactionViewModel : ViewModel() {

    private val _transactions = MutableLiveData<List<Transaction>>()
    val transactions: LiveData<List<Transaction>> = _transactions

    fun addTransaction(transaction: Transaction) {
        val currentList = _transactions.value.orEmpty().toMutableList()
        currentList.add(transaction)
        _transactions.value = currentList
    }

    fun updateTransactionCategory(transaction: Transaction, newCategory: TransactionCategory) {
        val currentList = _transactions.value.orEmpty().toMutableList()
        val index = currentList.indexOf(transaction)
        if (index != -1) {
            // Create a new Transaction object with the updated category
            val updatedTransaction = transaction.copy(category = newCategory)
            currentList[index] = updatedTransaction
            _transactions.value = currentList
        }
    }
}
