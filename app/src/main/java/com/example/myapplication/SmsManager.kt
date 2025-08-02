package com.example.ExpenseTracker
import android.content.Context
import androidx.core.net.toUri
import android.provider.Telephony
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
                // Log.e(TAG, "SMS body, address, or date column not found")
            }
        }
        return smsList
    }
    fun extractBankNameWithRegexValidation(input: String): String? {     // Regex to validate the basic structure: XX-YYYYYY-Z (approx)
        // This regex captures the middle part.
        val formatRegex = Regex("""^[A-Z]{2,}-([A-Z0-9]+?)-[A-Z]$""") // Adjust based on exact rules for prefix/suffix
        val matchResult = formatRegex.find(input)
        if (matchResult != null) {
            val potentialBankCode = matchResult.groups[1]?.value // Get the captured group
            if (potentialBankCode != null && potentialBankCode.length > 2) {
                return potentialBankCode.dropLast(2)
            } else if (potentialBankCode != null && potentialBankCode.isNotEmpty()) {
                return potentialBankCode // Or other logic for short codes
            }
        }
        return null }

    suspend fun parseSms(sms: String, senderAddress: String, timestamp: Long, userCategoryMappingDao: UserCategoryMappingDao): Transaction? {
        val amountPattern = Pattern.compile("""(?:Rs|INR)\.?\s*([\d,]+\.?\d*)""")
        val merchantPattern = Pattern.compile("""(?:at|to)\s+([^\s.,]+(?:\s+[^\s.,]+)*)""")
        val typePattern = Pattern.compile("""(credited|received|added|deposit|refund|credit|debited|spent|paid|deducted|purchase|payment|withdrawal)""", Pattern.CASE_INSENSITIVE)
        val bankPattern = Pattern.compile("""(?:from|in|at|with|on)\s+([A-Za-z0-9\s]+?)(?:Bank|bank|BANK|Ltd|Pvt Ltd|A/c|Acct|account|card|- Axis Bank)""")
        val upiPattern = Pattern.compile("""UPI/(?:P2M|P2A)/(?:[^/]+/)*([^/]+)""", Pattern.CASE_INSENSITIVE)
        val accountNumberPattern = Pattern.compile("""A/c(?: no\.)? ([X*\d]+)""")
        val dateTimePattern = Pattern.compile("""(\d{2}-\d{2}-\d{2}(?:,\s*\d{2}:\d{2}:\d{2})?|\d{2}-\w{3}-\d{2})""")
        val infoMerchantPattern = Pattern.compile("""Info:([^/]+)""")
        val forMerchantPattern = Pattern.compile("""for\s+(.+?)(?=\.\s|$)""")

        val amountMatcher = amountPattern.matcher(sms)

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

            val afterColonMerchantPattern = Pattern.compile(""":\s*after\s+that\s+[A-Z0-9]+-\s*(A-Z)""", Pattern.CASE_INSENSITIVE)
            val finalMerchant = upiPattern.matcher(sms).let {
                if (it.find()) {
                    val merchantGroup = it.group(1)
                    merchantGroup?.split("Not you?")?.firstOrNull()?.trim()
                } else null
            }
                ?: infoMerchantPattern.matcher(sms).let {
                    if (it.find()) {
                        val merchantGroup = it.group(1)
                        val parts = merchantGroup?.split("-")
                        when {
                            (parts?.size ?: 0) > 1 -> parts?.getOrNull(1)?.trim()
                            else -> parts?.firstOrNull()?.trim()
                        }
                    } else null
                }
                ?: forMerchantPattern.matcher(sms).let { if (it.find()) it.group(1)?.trim() else null }
                ?: afterColonMerchantPattern.matcher(sms).let { if (it.find()) it.group(1) else null }
                ?: merchantPattern.matcher(sms).let { if (it.find()) it.group(1) else null }
                ?: "Unknown"

            val bankMatcher = bankPattern.matcher(sms)
            val bank = if (bankMatcher.find()) bankMatcher.group(1).trim() else {
                extractBankNameWithRegexValidation(senderAddress) ?: senderAddress
            }

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

    suspend fun classifyTransaction(merchant: String?, originalMessage: String, userCategoryMappingDao: UserCategoryMappingDao): TransactionCategory {
        val lowerMerchant = merchant?.lowercase() ?: ""
        val lowerMessage = originalMessage.lowercase()

        // First, check user-defined mappings
        val userMapping = userCategoryMappingDao.getMappingForText(lowerMerchant) ?: userCategoryMappingDao.getMappingForText(lowerMessage)
        if (userMapping != null) {
            return userMapping.category
        }

        val detectedCategory = when {
            lowerMerchant.contains("redbus") -> TransactionCategory.TRAVEL
            lowerMessage.contains("upi/p2a") -> TransactionCategory.SPEND_TO_PERSON

            listOf("zomato", "swiggy", "restaurant", "cafe", "pizza", "food", "dine","bake","chicken").any { lowerMerchant.contains(it) } ->
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

            listOf("sumithra").any { lowerMerchant.contains(it) } ->
                TransactionCategory.MAID

            listOf("rd").any { lowerMerchant.contains(it) } ->
                TransactionCategory.RD
            listOf("mutual","mutalfund", "sip", "equity", "debt fund", "hybrid fund", "nav").any { lowerMerchant.contains(it) || lowerMessage.contains(it) } ->
                TransactionCategory.MUTUAL_FUND

            lowerMessage.contains("upi/p2m") -> TransactionCategory.UPI_TRANSFER

            else -> TransactionCategory.UNKNOWN
        }

        if (detectedCategory == TransactionCategory.UNKNOWN) {
            // Placeholder for notification logic
            // Log.d(TAG, "Unknown category detected for: $originalMessage. Prompt user for category.")
            // In a real app, you would trigger a notification here
            // For example: NotificationHelper.showCategoryPromptNotification(context, originalMessage, finalMerchant)
        }
        return detectedCategory
    }
}