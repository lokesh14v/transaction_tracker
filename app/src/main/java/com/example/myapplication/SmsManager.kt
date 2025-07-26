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
        val upiPattern = Pattern.compile("""UPI/(?:P2M|P2A)/(?:[^/]+/)*([^/]+)""", Pattern.CASE_INSENSITIVE)
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
            val merchantFromGeneralPattern = if (merchantMatcher.find()) merchantMatcher.group(1) else null
            val upiMatcherForMerchant = upiPattern.matcher(sms) // Create a new matcher for this specific use
            val merchantFromUpiPattern = if (upiMatcherForMerchant.find()) upiMatcherForMerchant.group(1) else null

            val finalMerchant = merchantFromUpiPattern ?: merchantFromGeneralPattern ?: "Unknown"

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

            val transaction = Transaction(
                amount = amount,
                merchant = finalMerchant,
                smsDate = System.currentTimeMillis(),
                type = type,
                originalMessage = sms,
                bank = bank,
                accountNumber = accountNumber,
                transactionDateTime = transactionDateTime,
                category = classifyTransaction(finalMerchant, sms)
            )
            return transaction
        }
        return null
    }

    private fun classifyTransaction(merchant: String, originalMessage: String): TransactionCategory {
        val lowerMerchant = merchant.lowercase()
        val lowerMessage = originalMessage.lowercase()

        return when {
            lowerMerchant.contains("redbus") -> TransactionCategory.TRAVEL
            lowerMessage.contains("upi/p2m") -> TransactionCategory.UPI_TRANSFER
            lowerMessage.contains("upi/p2a") -> TransactionCategory.SPEND_TO_PERSON

            listOf("zomato", "swiggy", "restaurant", "cafe", "pizza", "food", "dine").any { lowerMerchant.contains(it) } ->
                TransactionCategory.FOOD

            listOf("bar", "pub").any { lowerMerchant.contains(it) } ->
                TransactionCategory.BAR_ALCOHOL

            listOf("uber", "ola", "taxi", "cab", "flight", "hotel", "travel", "irctc", "redbus", "bus", "train").any { lowerMerchant.contains(it) } ->
                TransactionCategory.TRAVEL

            listOf("amazon", "flipkart", "store", "shop", "mall", "online", "myntra", "shopify").any { lowerMerchant.contains(it) } ->
                TransactionCategory.SHOPPING

            listOf("electricity", "utility", "bill", "water", "gas", "rent", "emi", "broadband", "recharge").any { lowerMerchant.contains(it) } ->
                TransactionCategory.BILLS_UTILITIES

            listOf("movie", "cinema", "ticket", "event", "netflix", "spotify").any { lowerMerchant.contains(it) } ->
                TransactionCategory.ENTERTAINMENT

            listOf("pharmacy", "hospital", "clinic", "doctor", "medical").any { lowerMerchant.contains(it) } ->
                TransactionCategory.HEALTH

            else -> TransactionCategory.UNKNOWN
        }
    }
}