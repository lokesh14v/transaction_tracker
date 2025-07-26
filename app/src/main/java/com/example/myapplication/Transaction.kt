package com.example.myapplication

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "transactions")
data class Transaction(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val amount: Double,
    val merchant: String?,
    val date: Long,
    val type: TransactionType,
    val originalMessage: String,
    var category: TransactionCategory = TransactionCategory.UNKNOWN
)

enum class TransactionType {
    CREDIT,
    DEBIT,
    UNKNOWN
}

enum class TransactionCategory {
    FOOD,
    BAR_ALCOHOL,
    TRAVEL,
    SHOPPING,
    BILLS_UTILITIES,
    UNKNOWN
}
