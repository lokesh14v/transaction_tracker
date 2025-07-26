package com.example.myapplication

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions ORDER BY date DESC")
    fun getAllTransactions(): LiveData<List<Transaction>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(transaction: Transaction)

    @Query("SELECT * FROM transactions WHERE originalMessage = :originalMessage")
    suspend fun getTransactionByMessage(originalMessage: String): Transaction?

    @Query("SELECT DISTINCT bank FROM transactions WHERE bank IS NOT NULL")
    fun getDistinctBanks(): LiveData<List<String>>
}