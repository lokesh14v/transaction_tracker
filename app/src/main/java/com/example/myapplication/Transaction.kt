package com.example.myapplication

data class Transaction(
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
