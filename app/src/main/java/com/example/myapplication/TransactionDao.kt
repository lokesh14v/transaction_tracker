package com.example.myapplication

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.ExpenseTracker.Transaction
import com.example.ExpenseTracker.TransactionCategory

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions ORDER BY transactionDateTime DESC")
    fun getAllTransactions(): LiveData<List<Transaction>>

    @Query("SELECT * FROM transactions WHERE bank = :bank ORDER BY transactionDateTime DESC")
    fun getTransactionsByBank(bank: String): LiveData<List<Transaction>>

    @Query("SELECT * FROM transactions WHERE bank = :bank AND transactionDateTime BETWEEN :startDate AND :endDate ORDER BY transactionDateTime DESC")
    fun getTransactionsByBankAndDateRange(bank: String, startDate: Long, endDate: Long): LiveData<List<Transaction>>

    @Query("SELECT * FROM transactions WHERE transactionDateTime BETWEEN :startDate AND :endDate ORDER BY transactionDateTime DESC")
    fun getTransactionsBetweenDates(startDate: Long, endDate: Long): LiveData<List<Transaction>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(transaction: Transaction)

    @Query("SELECT * FROM transactions WHERE originalMessage = :originalMessage")
    suspend fun getTransactionByMessage(originalMessage: String): Transaction?

    @Query("SELECT DISTINCT bank FROM transactions WHERE bank IS NOT NULL")
    fun getDistinctBanks(): LiveData<List<String>>

    @Query("UPDATE transactions SET category = :newCategory, userDefinedCategoryName = :userDefinedCategoryName WHERE id = :transactionId")
    suspend fun updateCategory(
        transactionId: Int,
        newCategory: TransactionCategory,
        userDefinedCategoryName: String?
    )

    @Delete
    suspend fun delete(transaction: Transaction)
}