package com.example.ExpenseTracker

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_category_mappings")
data class UserCategoryMapping(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val pattern: String, // e.g., merchant name or a keyword from the SMS
    val category: TransactionCategory
)