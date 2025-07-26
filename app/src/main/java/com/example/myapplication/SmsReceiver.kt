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

class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        if (Telephony.Sms.Intents.SMS_RECEIVED_ACTION == intent?.action) {
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            for (smsMessage in messages) {
                val messageBody = smsMessage.messageBody
                val sender = smsMessage.displayOriginatingAddress
                Log.d("SmsReceiver", "SMS received from: $sender, Body: $messageBody")

                val transaction = parseSms(messageBody, smsMessage.timestampMillis)
                transaction?.let {
                    val category = classifyTransaction(it.merchant ?: "", it.originalMessage)
                    val finalTransaction = it.copy(category = category)
                    Log.d("SmsReceiver", "Parsed Transaction: $finalTransaction")
                    (context?.applicationContext as? MyApplication)
                        ?.transactionViewModel
                        ?.addTransaction(finalTransaction)
                }
            }
        }
    }

    private fun parseSms(messageBody: String, timestamp: Long): Transaction? {
        val amountPattern = Regex("""(?:Rs\.?|INR)\s?([\d,]+(?:\.\d{1,2})?)""")
        val merchantPattern = Pattern.compile("""at\s+([^\s]+)""")
        val bankPattern = Pattern.compile("""(?:from|in)\s+([A-Za-z0-9\s]+?)(?:Bank|bank|BANK|Ltd|Pvt Ltd|A/c|Acct|account|card)""")
        val accountNumberPattern = Pattern.compile("""A/c no\. ([X*\d]+)""")
        val dateTimePattern = Pattern.compile("""(\d{2}-\d{2}-\d{2},\s*\d{2}:\d{2}:\d{2})""")
        val upiPattern = Pattern.compile("""upi/p2m/[^/]+/([^/]+)""")

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
            "debited" in messageBody.lowercase() ||
                    "spent" in messageBody.lowercase() ||
                    "paid" in messageBody.lowercase() -> TransactionType.DEBIT

            "credited" in messageBody.lowercase() ||
                    "received" in messageBody.lowercase() -> TransactionType.CREDIT

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

        val finalMerchant = if (upiMatcher.find()) upiMatcher.group(1) else merchant

        if (amount != null) {
            val transaction = Transaction(
                amount = amount,
                merchant = finalMerchant,
                smsDate = timestamp,
                type = type,
                originalMessage = messageBody,
                bank = bank,
                accountNumber = accountNumber,
                transactionDateTime = transactionDateTime
            )
            transaction.category = classifyTransaction(finalMerchant, messageBody)
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
