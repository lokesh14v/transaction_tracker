package com.example.ExpenseTracker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.regex.Pattern

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
                        val transaction = parseSms(messageBody, smsMessage.timestampMillis, sender, userCategoryMappingDao)
                        if (transaction != null) {
                            Log.d("SmsReceiver", "Parsed Transaction: $transaction")
                            applicationContext
                                ?.transactionViewModel
                                ?.addTransaction(transaction)

                            // Show Toast on the main thread
                            CoroutineScope(Dispatchers.Main).launch {
                                Toast.makeText(context, "Merchant: ${transaction.merchant}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            }
        }
    }

    private suspend fun parseSms(sms: String, timestamp: Long, senderAddress: String, userCategoryMappingDao: UserCategoryMappingDao): Transaction? {
        val amountPattern = Pattern.compile("""(?:Rs|INR)\.?\s*([\d,]+\.?\d*)""")
        val merchantPattern = Pattern.compile("""(?:at|to|from)\s+([^\s.,]+(?:\s+[^\s.,]+)*)""")
        val typePattern = Pattern.compile("""(credited|received|added|deposit|refund|credit|debited|spent|paid|deducted|purchase|payment|withdrawal)""", Pattern.CASE_INSENSITIVE)
        val bankPattern = Pattern.compile("""(?:from|in|at|with|on)\s+([A-Za-z0-9\s]+?)(?:Bank|bank|BANK|Ltd|Pvt Ltd|A/c|Acct|account|card|- Axis Bank)""")
        val upiPattern = Pattern.compile("""UPI/(?:P2M|P2A)/(?:[^/]+/)*([^/]+)""", Pattern.CASE_INSENSITIVE)
        val accountNumberPattern = Pattern.compile("""A/c(?: no\.)? ([X*\d]+)""")
        val dateTimePattern = Pattern.compile("""(\d{2}-\d{2}-\d{2}(?:,\s*\d{2}:\d{2}:\d{2})?|\d{2}-\w{3}-\d{2})""")
        val infoMerchantPattern = Pattern.compile("""Info - ([^/]+)""")
        val forMerchantPattern = Pattern.compile("""for\s+(.+?)(?=\.\s|$)|""")

        val amountMatcher = amountPattern.matcher(sms)
        val typeMatcher = typePattern.matcher(sms)

        if (amountMatcher.find()) {
            val amount = amountMatcher.group(1).replace(",", "").toDouble()
            if (amount > 0) {
                val type = if (sms.lowercase().contains("credited") || sms.lowercase().contains("deposited") || sms.lowercase().contains("credit") || sms.lowercase().contains("deposit") || sms.lowercase().contains("refund")) {
                    TransactionType.CREDIT
                } else if (sms.lowercase().contains("debited") || sms.lowercase().contains("spent") || sms.lowercase().contains("paid") || sms.lowercase().contains("deducted") || sms.lowercase().contains("purchase") || sms.lowercase().contains("payment") || sms.lowercase().contains("withdrawal")) {
                    TransactionType.DEBIT
                } else {
                    TransactionType.UNKNOWN
                }

            val merchantMatcher = merchantPattern.matcher(sms)
            val upiMatcher = upiPattern.matcher(sms)
            val infoMerchantMatcher = infoMerchantPattern.matcher(sms)
            val forMerchantMatcher = forMerchantPattern.matcher(sms)

            val finalMerchant = when {
                upiMatcher.find() -> upiMatcher.group(1)
                infoMerchantMatcher.find() -> infoMerchantMatcher.group(1)
                forMerchantMatcher.find() -> forMerchantMatcher.group(1)
                merchantMatcher.find() -> merchantMatcher.group(1)
                else -> "Unknown"
            }

            val bankMatcher = bankPattern.matcher(sms)
            val bank = if (bankMatcher.find()) bankMatcher.group(1).trim() else senderAddress

            val accountNumberMatcher = accountNumberPattern.matcher(sms)
            val accountNumber = if (accountNumberMatcher.find()) accountNumberMatcher.group(1) else null

            val dateTimeMatcher = dateTimePattern.matcher(sms)
            val dateTimeString = if (dateTimeMatcher.find()) dateTimeMatcher.group(1) else null
            val transactionDateTime = dateTimeString?.let {
                try {
                    val format = if (it.contains(",")) {
                        SimpleDateFormat("dd-MM-yy, HH:mm:ss", Locale.getDefault())
                    } else {
                        SimpleDateFormat("dd-MMM-yy", Locale.getDefault())
                    }
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

            lowerMessage.contains("upi/p2m") -> TransactionCategory.UPI_TRANSFER

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
