package com.example.myapplication

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.regex.Pattern
import android.widget.Toast

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        if (Telephony.Sms.Intents.SMS_RECEIVED_ACTION == intent?.action) {
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            val applicationContext = context?.applicationContext as? MyApplication
            val userCategoryMappingDao = applicationContext?.appDatabase?.userCategoryMappingDao()

            CoroutineScope(Dispatchers.IO).launch {
                for (smsMessage in messages) {
                    val messageBody = smsMessage.messageBody
                    val sender = smsMessage.displayOriginatingAddress
                    Log.d("SmsReceiver", "SMS received from: $sender, Body: $messageBody")

                    if (userCategoryMappingDao != null) {
                        val transaction = parseSms(messageBody, smsMessage.timestampMillis, sender, context, userCategoryMappingDao)
                        if (transaction != null) {
                            Log.d("SmsReceiver", "Parsed Transaction: $transaction")
                            applicationContext
                                ?.transactionViewModel
                                ?.addTransaction(transaction)
                        }
                    }
                }
            }
        }
    }

    private suspend fun parseSms(messageBody: String, timestamp: Long, senderAddress: String, context: Context?, userCategoryMappingDao: UserCategoryMappingDao): Transaction? {
        val amountPattern = Regex("""(?:Rs\.?|INR)\s?([\d,]+(?:\.\d{1,2})?)""")
        val merchantPattern = Pattern.compile("""at\s+([^\s]+)""")
        val bankPattern = Pattern.compile("""(?:from|in)\s+([A-Za-z0-9\s]+?)(?:Bank|bank|BANK|Ltd|Pvt Ltd|A/c|Acct|account|card)""")
        val accountNumberPattern = Pattern.compile("""A/c no\. ([X*\d]+)""", Pattern.CASE_INSENSITIVE)
        val dateTimePattern = Pattern.compile("""(\d{2}-\d{2}-\d{2},\s*\d{2}:\d{2}:\d{2})""")
        val upiPattern = Pattern.compile("""UPI/(?:P2M|P2A)/(?:[^/]+/)*([^/]+)""", Pattern.CASE_INSENSITIVE)

        val amountMatcher = amountPattern.find(messageBody)
        val merchantMatcher = merchantPattern.matcher(messageBody)
        val bankMatcher = bankPattern.matcher(messageBody)
        val accountNumberMatcher = accountNumberPattern.matcher(messageBody)
        val dateTimeMatcher = dateTimePattern.matcher(messageBody)
        val upiMatcher = upiPattern.matcher(messageBody)

        val amount: Double? = amountMatcher?.groups?.get(1)?.value
            ?.replace(",", "")
            ?.toDoubleOrNull()

        val type = when {
            "debited" in messageBody.lowercase() || "spent" in messageBody.lowercase() || "paid" in messageBody.lowercase() -> TransactionType.DEBIT
            "credited" in messageBody.lowercase() || "received" in messageBody.lowercase() -> TransactionType.CREDIT
            else -> TransactionType.UNKNOWN
        }

        val merchantFoundOnce = merchantMatcher.find()
        val upiFoundOnce = upiMatcher.find()

        val extractedMerchant = if (merchantFoundOnce) merchantMatcher.group(1) else "Unknown"
        val extractedUpiMerchant = if (upiFoundOnce) upiMatcher.group(1) else null
        val finalMerchant = extractedUpiMerchant ?: extractedMerchant

        // ✅ Show Toast for final merchant
        context?.let {
            Toast.makeText(it, "Merchant: $finalMerchant", Toast.LENGTH_SHORT).show()
        }

        val category = classifyTransaction(finalMerchant, messageBody, userCategoryMappingDao)
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

        return if (amount != null) {
            Transaction(
                amount = amount,
                merchant = finalMerchant,
                smsDate = timestamp,
                type = type,
                originalMessage = messageBody,
                bank = bank,
                accountNumber = accountNumber,
                transactionDateTime = transactionDateTime,
                category = category
            )
        } else null
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
            Log.d("SmsReceiver", "Unknown category detected for: $originalMessage. Prompt user for category.")
            // In a real app, you would trigger a notification here
            // For example: NotificationHelper.showCategoryPromptNotification(context, originalMessage, finalMerchant)
        }
        return detectedCategory
    }
}
