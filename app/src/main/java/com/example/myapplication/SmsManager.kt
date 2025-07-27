package com.example.ExpenseTracker

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
        val smsListWithSender = readSms(context)
        val transactionDao = AppDatabase.getDatabase(context).transactionDao()
        val userCategoryMappingDao = AppDatabase.getDatabase(context).userCategoryMappingDao()

        for ((smsBody, sender, timestamp) in smsListWithSender) {
            if (transactionDao.getTransactionByMessage(smsBody) == null) {
                parseSms(smsBody, sender, timestamp, userCategoryMappingDao)?.let {
                    transactionDao.insert(it)
                    processedTransactions++
                }
            }
        }
        return Pair(smsListWithSender.size, processedTransactions)
    }

    private fun readSms(context: Context): List<Triple<String, String, Long>> {
        val smsList = mutableListOf<Triple<String, String, Long>>()
        val uri = "content://sms/inbox".toUri()
        val cursor = context.contentResolver.query(uri, null, null, null, "date DESC")

        cursor?.use {
            val bodyIndex = it.getColumnIndex(Telephony.Sms.BODY)
            val addressIndex = it.getColumnIndex(Telephony.Sms.ADDRESS)
            val dateIndex = it.getColumnIndex(Telephony.Sms.DATE)
            if (bodyIndex >= 0 && addressIndex >= 0 && dateIndex >= 0) {
                while (it.moveToNext()) {
                    val body = it.getString(bodyIndex)
                    val sender = it.getString(addressIndex)
                    val timestamp = it.getLong(dateIndex)
                    smsList.add(Triple(body, sender, timestamp))
                }
            } else {
                Log.e(TAG, "SMS body, address, or date column not found")
            }
        }
        return smsList
    }

    private suspend fun parseSms(sms: String, senderAddress: String, timestamp: Long, userCategoryMappingDao: UserCategoryMappingDao): Transaction? {
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

            val bank = if (bankMatcher.find()) bankMatcher.group(1) else senderAddress
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

            val finalCategory = classifyTransaction(finalMerchant, sms, userCategoryMappingDao)

            val transaction = Transaction(
                amount = amount,
                merchant = finalMerchant,
                smsDate = timestamp,
                type = type,
                originalMessage = sms,
                bank = bank,
                accountNumber = accountNumber,
                transactionDateTime = transactionDateTime,
                category = finalCategory
            )
            return transaction
        }
        return null
    }

    private suspend fun classifyTransaction(merchant: String, originalMessage: String, userCategoryMappingDao: UserCategoryMappingDao): TransactionCategory {
        val lowerMerchant = merchant.lowercase()
        val lowerMessage = originalMessage.lowercase()

        // First, check user-defined mappings
        val userMapping = userCategoryMappingDao.getMappingForText(lowerMerchant) ?: userCategoryMappingDao.getMappingForText(lowerMessage)
        if (userMapping != null) {
            return userMapping.category
        }

        val detectedCategory = when {
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

        if (detectedCategory == TransactionCategory.UNKNOWN) {
            // Placeholder for notification logic
            Log.d(TAG, "Unknown category detected for: $originalMessage. Prompt user for category.")
            // In a real app, you would trigger a notification here
            // For example: NotificationHelper.showCategoryPromptNotification(context, originalMessage, finalMerchant)
        }
        return detectedCategory
    }
}