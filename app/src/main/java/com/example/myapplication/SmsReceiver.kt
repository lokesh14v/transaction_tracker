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
                        val transaction = SmsManager.parseSms(messageBody, sender, smsMessage.timestampMillis, userCategoryMappingDao)
                        if (transaction != null) {
                            Log.d("SmsReceiver", "Parsed Transaction: $transaction")
                            applicationContext?.appDatabase?.transactionDao()?.insert(transaction)

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
}
