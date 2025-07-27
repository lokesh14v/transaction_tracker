package com.example.myapplication

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "transactions")
data class Transaction(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val amount: Double,
    val merchant: String?,
    val extractedMerchant: String? = null,
    val extractedUpiMerchant: String? = null,
    val smsDate: Long,
    val type: TransactionType,
    val originalMessage: String,
    var category: TransactionCategory = TransactionCategory.UNKNOWN,
    val bank: String? = null,
    val accountNumber: String? = null,
    val transactionDateTime: Long? = null,
    var userDefinedCategoryName: String? = null
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
    ENTERTAINMENT,
    HEALTH,
    TRANSPORT,
    UPI_TRANSFER,
    SPEND_TO_PERSON,
    UNKNOWN
}