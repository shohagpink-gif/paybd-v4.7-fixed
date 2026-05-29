package com.paybd.app.utils

data class ParsedSms(
    val service: String,
    val amount: Double,
    val sender: String,
    val transactionId: String,
    val hasAmountError: Boolean = false,
    val extractedBalance: Double? = null
)

object SmsParser {

    private val WHITELISTED_SENDERS = setOf(
        "bkash", "16247", "247", "nagad", "NAGAD", "16167", "rocket",
        "16216", "dbbl", "8247", "ibbl", "upay", "16301",
        "bKash", "Nagad", "Rocket", "DBBL", "16222"
    )

    private val SENDER_SERVICE_MAP = mapOf(
        "bkash" to "bKash",
        "16247" to "bKash",
        "247" to "bKash",
        "8247" to "bKash",
        "nagad" to "Nagad",
        "NAGAD" to "Nagad",
        "16167" to "Nagad",
        "rocket" to "Rocket",
        "16216" to "Rocket",
        "dbbl" to "Rocket"
    )

    private val PERSONAL_NUMBER_PATTERN = Regex("^0?1[3-9]\\\\d{8}$")
    private val TRXID_ONLY_PATTERN = Regex("""(?i)(?:ref|trxid|txnid|TrxID)[:\s]+([A-Z0-9]{6,})""")
    private val BALANCE_PATTERNS = listOf(
        Regex("""(?i)(?:current\s+balance|new\s+balance|balance)[:\s]*(?:tk|taka|bdt|৳)[\s.]*([0-9,]+\.?\d*)"""),
        Regex("""(?i)(?:tk|taka|bdt|৳)[\s.]*([0-9,]+\.?\d*)\s*(?:is your (?:current|new) balance)"""),
        Regex("""(?i)balance\s*(?:tk|taka|bdt|৳)[\s.]*([0-9,]+\.?\d*)""")
    )

    fun isWhitelistedSender(sender: String): Boolean {
        val cleanSender = sender.replace("+88", "").replace("+880", "").trim()
        if (PERSONAL_NUMBER_PATTERN.matches(cleanSender)) return false
        return WHITELISTED_SENDERS.any {
            cleanSender.equals(it, ignoreCase = true) || cleanSender.contains(it, ignoreCase = true)
        }
    }

    fun extractBalance(message: String): Double? {
        for (pattern in BALANCE_PATTERNS) {
            pattern.find(message)?.let { match ->
                val amount = parseAmount(match.groupValues[1])
                if (amount > 0) return amount
            }
        }
        return null
    }

    fun parseSms(sender: String, message: String, simSlot: Int): ParsedSms? {
        if (!isWhitelistedSender(sender)) return null
        val service = detectService(sender)
        val extractedBalance = extractBalance(message)
        val result = parseGenericMfsMessage(message, service)
        if (result != null) return result.copy(extractedBalance = extractedBalance)

        val trxIdMatch = TRXID_ONLY_PATTERN.find(message)
        if (trxIdMatch != null) {
            val txnId = trxIdMatch.groupValues[1]
            val looseAmount = extractLooseAmount(message)
            val senderNum = extractSenderNumber(message)
            return ParsedSms(
                service = service,
                amount = looseAmount,
                sender = senderNum,
                transactionId = txnId,
                hasAmountError = true,
                extractedBalance = extractedBalance
            )
        }
        return null
    }

    private fun detectService(sender: String): String {
        val cleanSender = sender.replace("+88", "").replace("+880", "").trim().lowercase()
        for ((key, service) in SENDER_SERVICE_MAP) {
            if (cleanSender.contains(key, ignoreCase = true)) return service
        }
        return when {
            cleanSender.contains("bkash", ignoreCase = true) -> "bKash"
            cleanSender.contains("nagad", ignoreCase = true) -> "Nagad"
            cleanSender.contains("rocket", ignoreCase = true) || cleanSender.contains("dbbl", ignoreCase = true) -> "Rocket"
            else -> "Unknown"
        }
    }

    private fun parseGenericMfsMessage(message: String, service: String): ParsedSms? {
        val patterns = listOf(
            Regex("""(?is)cash\s*in\s+(?:tk|taka|bdt|৳)[\s.]*([0-9,]+\.?\d*).*?from\s+(\d+).*?(?:TrxID)[:\s]+([A-Z0-9]+)"""),
            Regex("""(?is)(?:received|পেয়েছেন)\s+(?:tk|taka|bdt|৳)[\s.]*([0-9,]+\.?\d*)\s+from\s+(\d+).*?(?:ref|trxid|txnid|TrxID)[:\s]*([A-Z0-9]+)"""),
            Regex("""(?is)(?:tk|taka|bdt|৳)[\s.]*([0-9,]+\.?\d*)\s+(?:has been\s+)?(?:received|credited|added).*?from\s+(\d+).*?(?:ref|trxid|txnid|TrxID)[:\s]*([A-Z0-9]+)"""),
            Regex("""(?is)cash\s*in\s+(?:tk|taka|bdt|৳)[\s.]*([0-9,]+\.?\d*)\s+from\s+(\d+).*?(?:ref|trxid|txnid|TrxID)[:\s]*([A-Z0-9]+)"""),
            Regex("""(?is)(?:received|cash\s*in|payment|add\s*money|credited).*?(?:tk|taka|bdt|৳)[\s.]*([0-9,]+\.?\d*).*?(?:from|sender)[\s:]*(\d+).*?(?:ref|trxid|txnid|id)[:\s]*([A-Z0-9]+)"""),
            Regex("""(?is)(?:tk|taka|bdt|৳)[\s.]*([0-9,]+\.?\d*)\s+(?:received|credited).*?(?:from|sender)\s+(\d+).*?(?:ref|trxid|txnid|id)[:\s]*([A-Z0-9]+)""")
        )

        for (pattern in patterns) {
            pattern.find(message)?.let { match ->
                val amount = parseAmount(match.groupValues[1])
                val senderNum = match.groupValues[2]
                val txnId = match.groupValues[3]
                if (amount > 0) return ParsedSms(service, amount, senderNum, txnId, hasAmountError = false)
            }
        }
        return null
    }

    private fun extractLooseAmount(message: String): Double {
        val amountPattern = Regex("""(?i)(?:tk|taka|bdt|৳)[\s.]*([0-9,]+\.?\d*)""")
        amountPattern.find(message)?.let { match -> return parseAmount(match.groupValues[1]) }
        return 0.0
    }

    private fun extractSenderNumber(message: String): String {
        val phonePattern = Regex("""(?:from|sender)[\s:]*(\d{10,11})""", RegexOption.IGNORE_CASE)
        phonePattern.find(message)?.let { match -> return match.groupValues[1] }
        val fallbackPattern = Regex("""(01\d{9})""")
        fallbackPattern.find(message)?.let { match -> return match.groupValues[1] }
        return "Unknown"
    }

    private fun parseAmount(amountStr: String): Double {
        return try { amountStr.replace(",", "").toDouble() } catch (e: Exception) { 0.0 }
    }
}
