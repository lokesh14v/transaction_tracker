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
import java.text.SimpleDateFormat
import java.text.ParseException
import java.util.Locale

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
        val typePattern = Pattern.compile("""(credited|received|added|deposit|refund|credit|debited|spent|paid|deducted|purchase|payment|withdrawal)""", Pattern.CASE_INSENSITIVE)
        val bankPattern = Pattern.compile("""from\s+([A-Za-z0-9\s]+?)(?:Bank|bank|BANK|Ltd|Pvt Ltd|A/c|Acct|account|card)""")
        val upiPattern = Pattern.compile("""upi/p2m/[^/]+/([^/]+)""")
        val accountNumberPattern = Pattern.compile("""A/c no\. ([X*\d]+)""")
        val dateTimePattern = Pattern.compile("""(\d{2}-\d{2}-\d{2},\s*\d{2}:\d{2}:\d{2})""")

        val amountMatcher = amountPattern.matcher(sms)
        val merchantMatcher = merchantPattern.matcher(sms)
        val typeMatcher = typePattern.matcher(sms)
        val bankMatcher = bankPattern.matcher(sms)
        val upiMatcher = upiPattern.matcher(sms)
        val accountNumberMatcher = accountNumberPattern.matcher(sms)
        val dateTimeMatcher = dateTimePattern.matcher(sms)

        if (amountMatcher.find() && typeMatcher.find()) {
            val amount = amountMatcher.group(1).replace(",", "").toDouble()
            val typeStr = typeMatcher.group(1)
            val type = when (typeStr.lowercase()) {
                "credited", "received", "added", "deposit", "refund", "credit" -> TransactionType.CREDIT
                "debited", "spent", "paid", "deducted", "purchase", "payment", "withdrawal" -> TransactionType.DEBIT
                else -> TransactionType.UNKNOWN
            }
            val merchant = if (merchantMatcher.find()) merchantMatcher.group(1) ?: "Unknown" else "Unknown"
            val bank = if (bankMatcher.find()) bankMatcher.group(1) else null
            val accountNumber = if (accountNumberMatcher.find()) accountNumberMatcher.group(1) else null
            val dateTimeString = if (dateTimeMatcher.find()) dateTimeMatcher.group(1) else null
            val transactionDateTime = dateTimeString?.let { 
                try {
                    val format = SimpleDateFormat("dd-MM-yy, HH:mm:ss", Locale.getDefault())
                    format.parse(it)?.time
                } catch (e: ParseException) {
                    null
                }
            }

            val upiMatcher = upiPattern.matcher(sms)
            val finalMerchant = if (upiMatcher.find()) upiMatcher.group(1) else merchant

            val transaction = Transaction(
                amount = amount,
                merchant = finalMerchant,
                smsDate = System.currentTimeMillis(),
                type = type,
                originalMessage = sms,
                bank = bank,
                accountNumber = accountNumber,
                transactionDateTime = transactionDateTime
            )
            transaction.category = classifyTransaction(finalMerchant, sms)
            return transaction
        }
        return null
    }

    private fun classifyTransaction(merchant: String, originalMessage: String): TransactionCategory {
        val upiCategoryPattern = Pattern.compile("""upi/p2m/[^/]+/([^/]+)""")
        val upiCategoryMatcher = upiCategoryPattern.matcher(originalMessage)

        if (upiCategoryMatcher.find()) {
            val upiMerchant = upiCategoryMatcher.group(1)
            if (upiMerchant.equals("REDBUS", ignoreCase = true)) {
                return TransactionCategory.TRAVEL
            }
            return TransactionCategory.UPI_TRANSFER
        }

        return when {
            merchant.contains("zomato", ignoreCase = true) || merchant.contains("swiggy", ignoreCase = true) || merchant.contains("restaurant", ignoreCase = true) || merchant.contains("cafe", ignoreCase = true) || merchant.contains("pizza", ignoreCase = true) || merchant.contains("food", ignoreCase = true) || merchant.contains("dine", ignoreCase = true) -> TransactionCategory.FOOD
            merchant.contains("bar", ignoreCase = true) || merchant.contains("pub", ignoreCase = true) -> TransactionCategory.BAR_ALCOHOL
            merchant.contains("uber", ignoreCase = true) || merchant.contains("ola", ignoreCase = true) || merchant.contains("taxi", ignoreCase = true) || merchant.contains("cab", ignoreCase = true) || merchant.contains("flight", ignoreCase = true) || merchant.contains("hotel", ignoreCase = true) || merchant.contains("travel", ignoreCase = true) || merchant.contains("redbus", ignoreCase = true) || merchant.contains("irctc", ignoreCase = true) -> TransactionCategory.TRAVEL
            merchant.contains("amazon", ignoreCase = true) || merchant.contains("flipkart", ignoreCase = true) || merchant.contains("store", ignoreCase = true) || merchant.contains("shop", ignoreCase = true) || merchant.contains("mall", ignoreCase = true) || merchant.contains("online", ignoreCase = true) || merchant.contains("myntra", ignoreCase = true) || merchant.contains("shopify", ignoreCase = true) -> TransactionCategory.SHOPPING
            merchant.contains("electricity", ignoreCase = true) || merchant.contains("utility", ignoreCase = true) || merchant.contains("bill", ignoreCase = true) || merchant.contains("water", ignoreCase = true) || merchant.contains("gas", ignoreCase = true) || merchant.contains("rent", ignoreCase = true) || merchant.contains("emi", ignoreCase = true) || merchant.contains("broadband", ignoreCase = true) || merchant.contains("recharge", ignoreCase = true) -> TransactionCategory.BILLS_UTILITIES
            merchant.contains("movie", ignoreCase = true) || merchant.contains("cinema", ignoreCase = true) || merchant.contains("ticket", ignoreCase = true) || merchant.contains("event", ignoreCase = true) || merchant.contains("netflix", ignoreCase = true) || merchant.contains("spotify", ignoreCase = true) -> TransactionCategory.ENTERTAINMENT
            merchant.contains("pharmacy", ignoreCase = true) || merchant.contains("hospital", ignoreCase = true) || merchant.contains("clinic", ignoreCase = true) || merchant.contains("doctor", ignoreCase = true) || merchant.contains("medical", ignoreCase = true) -> TransactionCategory.HEALTH
            merchant.contains("fuel", ignoreCase = true) || merchant.contains("petrol", ignoreCase = true) || merchant.contains("bus", ignoreCase = true) || merchant.contains("train", ignoreCase = true) -> TransactionCategory.TRANSPORT
            else -> TransactionCategory.UNKNOWN
        }
    }
}