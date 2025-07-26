package com.example.myapplication

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log

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
                    val category = classifyTransaction(it)
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
        val matchResult = amountPattern.find(messageBody)

        val amount: Double? = matchResult?.groups?.get(1)?.value
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

        val merchantKeywords = listOf("at", "to", "for", "on")
        val messageLower = messageBody.lowercase()
        var merchant: String? = null

        for (keyword in merchantKeywords) {
            val keywordIndex = messageLower.indexOf(keyword)
            if (keywordIndex != -1) {
                val startIndex = keywordIndex + keyword.length
                val remaining = messageBody.substring(startIndex).trim()
                val firstWord = remaining.split(" ", limit = 2).firstOrNull()
                merchant = firstWord?.replace("[^a-zA-Z0-9]".toRegex(), "")?.takeIf { it.isNotBlank() }
                if (!merchant.isNullOrEmpty()) break
            }
        }

        if (amount != null) {
            return Transaction(
                amount = amount,
                merchant = merchant,
                date = timestamp,
                type = type,
                originalMessage = messageBody
            )
        }
        return null
    }


    private fun classifyTransaction(transaction: Transaction): TransactionCategory {
        val message = transaction.originalMessage.lowercase()
        val merchant = transaction.merchant?.lowercase()

        return when {
            message.contains("swiggy") || message.contains("zomato") || message.contains("restaurant") ||
                    message.contains("cafe") || merchant?.contains("food") == true -> TransactionCategory.FOOD

            message.contains("bar") || message.contains("alcohol") || message.contains("pub") -> TransactionCategory.BAR_ALCOHOL

            message.contains("uber") || message.contains("ola") || message.contains("travel") ||
                    message.contains("flight") || message.contains("hotel") -> TransactionCategory.TRAVEL

            message.contains("amazon") || message.contains("flipkart") || message.contains("shop") ||
                    message.contains("store") || merchant?.contains("shopping") == true -> TransactionCategory.SHOPPING

            message.contains("bill") || message.contains("utility") || message.contains("electricity") ||
                    message.contains("water") || message.contains("rent") -> TransactionCategory.BILLS_UTILITIES

            else -> TransactionCategory.UNKNOWN
        }
    }
}
