package com.paybd.app

/**
 * Represents a parsed bKash transaction extracted from an SMS body.
 */
data class SmsData(
    val sender: String,
    val rawMessage: String,
    val amount: String?,
    val transactionId: String?,
    val balance: String?,
    val fromPhone: String?,
    val toPhone: String?,
    val type: TransactionType,
    val timestamp: Long = System.currentTimeMillis()
)

enum class TransactionType {
    RECEIVED, SENT, PAYMENT, CASHOUT, CASHIN, UNKNOWN
}

object SmsParser {

    private val BKASH_SENDERS = setOf(
        "bKash", "bkash", "01779-054111", "01779054111"
    )

    private val AMOUNT_REGEX    = Regex("""Tk\s*([\d,]+\.?\d*)""")
    private val TRX_ID_REGEX    = Regex("""TrxID\s+([A-Z0-9]+)""", RegexOption.IGNORE_CASE)
    private val BALANCE_REGEX   = Regex("""Balance\s+Tk\s*([\d,]+\.?\d*)""", RegexOption.IGNORE_CASE)
    private val FROM_PHONE_REGEX = Regex("""from\s+(01[3-9]\d{8})""", RegexOption.IGNORE_CASE)
    private val TO_PHONE_REGEX   = Regex("""to\s+(01[3-9]\d{8})""", RegexOption.IGNORE_CASE)

    fun isBkashSms(sender: String, message: String): Boolean {
        val senderMatch = BKASH_SENDERS.any { it.equals(sender, ignoreCase = true) }
        val messageMatch = message.contains("bKash", ignoreCase = true) &&
                (message.contains("TrxID", ignoreCase = true) ||
                 message.contains("received", ignoreCase = true) ||
                 message.contains("Cash Out", ignoreCase = true) ||
                 message.contains("Cash In", ignoreCase = true) ||
                 message.contains("payment", ignoreCase = true))
        return senderMatch || messageMatch
    }

    fun parse(sender: String, message: String): SmsData {
        val amounts = AMOUNT_REGEX.findAll(message).map { it.groupValues[1] }.toList()
        val amount  = amounts.firstOrNull()
        val balance = BALANCE_REGEX.find(message)?.groupValues?.get(1)
            ?: amounts.lastOrNull()?.takeIf { amounts.size > 1 }

        return SmsData(
            sender        = sender,
            rawMessage    = message,
            amount        = amount,
            transactionId = TRX_ID_REGEX.find(message)?.groupValues?.get(1),
            balance       = balance,
            fromPhone     = FROM_PHONE_REGEX.find(message)?.groupValues?.get(1),
            toPhone       = TO_PHONE_REGEX.find(message)?.groupValues?.get(1),
            type          = detectType(message)
        )
    }

    private fun detectType(message: String): TransactionType = when {
        message.contains("You have received", ignoreCase = true)              -> TransactionType.RECEIVED
        message.contains("sent successfully", ignoreCase = true) ||
            message.contains("You have sent", ignoreCase = true)              -> TransactionType.SENT
        message.contains("Cash Out", ignoreCase = true)                       -> TransactionType.CASHOUT
        message.contains("Cash In", ignoreCase = true)                        -> TransactionType.CASHIN
        message.contains("payment", ignoreCase = true)                        -> TransactionType.PAYMENT
        else                                                                   -> TransactionType.UNKNOWN
    }
}
