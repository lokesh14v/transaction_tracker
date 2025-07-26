package com.example.myapplication

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import android.provider.Telephony
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.regex.Pattern

object SmsManager {

    private const val TAG = "SmsManager"

    suspend fun syncSms(context: Context): Pair<Int, Int> {
        var processedTransactions = 0
        val smsList = readSms(context)
        val transactionDao = AppDatabase.getDatabase(context).transactionDao()
        for (sms in smsList) {
            if (transactionDao.getTransactionByMessage(sms) == null) {
                parseSms(sms)?.let {
                    transactionDao.insert(it)
                    processedTransactions++
                }
            }
        }
        return Pair(smsList.size, processedTransactions)
    }

    private fun readSms(context: Context): List<String> {
        val smsList = mutableListOf<String>()
        val uri = "content://sms/inbox".toUri()
        val cursor = context.contentResolver.query(uri, null, null, null, "date DESC")

        cursor?.use {
            val bodyIndex = it.getColumnIndex(Telephony.Sms.BODY)
            if (bodyIndex >= 0) {
                while (it.moveToNext()) {
                    val body = it.getString(bodyIndex)
                    smsList.add(body)
                }
            } else {
                Log.e(TAG, "SMS body column not found")
            }
        }
        return smsList
    }

    private fun parseSms(sms: String): Transaction? {
        val amountPattern = Pattern.compile("""(?:Rs|INR)\.?\s*([\d,]+\.?\d*)""")
        val merchantPattern = Pattern.compile("""at\s+([^\s]+)""")
        val typePattern = Pattern.compile("""(credited|debited)""", Pattern.CASE_INSENSITIVE)

        val amountMatcher = amountPattern.matcher(sms)
        val merchantMatcher = merchantPattern.matcher(sms)
        val typeMatcher = typePattern.matcher(sms)

        if (amountMatcher.find() && typeMatcher.find()) {
            val amount = amountMatcher.group(1).replace(",", "").toDouble()
            val typeStr = typeMatcher.group(1)
            val type = if (typeStr.equals("credited", ignoreCase = true)) TransactionType.CREDIT else TransactionType.DEBIT
            val merchant = if (merchantMatcher.find()) merchantMatcher.group(1) ?: "Unknown" else "Unknown"

            val transaction = Transaction(
                amount = amount,
                merchant = merchant,
                date = System.currentTimeMillis(),
                type = type,
                originalMessage = sms
            )
            transaction.category = classifyTransaction(merchant)
            return transaction
        }
        return null
    }

    private fun classifyTransaction(merchant: String): TransactionCategory {
        return when {
            merchant.contains("zomato", ignoreCase = true) || merchant.contains("swiggy", ignoreCase = true) -> TransactionCategory.FOOD
            merchant.contains("bar", ignoreCase = true) || merchant.contains("pub", ignoreCase = true) -> TransactionCategory.BAR_ALCOHOL
            merchant.contains("amazon", ignoreCase = true) || merchant.contains("flipkart", ignoreCase = true) -> TransactionCategory.SHOPPING
            merchant.contains("electricity", ignoreCase = true) || merchant.contains("utility", ignoreCase = true) -> TransactionCategory.BILLS_UTILITIES
            merchant.contains("uber", ignoreCase = true) || merchant.contains("ola", ignoreCase = true) -> TransactionCategory.TRAVEL
            else -> TransactionCategory.UNKNOWN
        }
    }
}