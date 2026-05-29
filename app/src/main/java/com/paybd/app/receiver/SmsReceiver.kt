package com.paybd.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import com.paybd.app.data.db.TransactionDbHelper
import com.paybd.app.data.model.Transaction
import com.paybd.app.utils.*

class SmsReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "SmsReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        try {
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            if (messages.isNullOrEmpty()) return

            val prefsManager = PrefsManager(context)
            val dbHelper = TransactionDbHelper(context)
            val webhookSender = WebhookSender(context)
            val notificationHelper = TransactionNotificationHelper(context)
            val balanceValidator = BalanceValidator(prefsManager)

            val simSlot = getSimSlot(intent)

            val sender = messages[0].displayOriginatingAddress ?: return
            val fullMessage = messages.joinToString("") { it.displayMessageBody ?: "" }

            val parsed = SmsParser.parseSms(sender, fullMessage, simSlot) ?: return

            val commission = prefsManager.calculateCommission(parsed.amount)
            val expectedNewBalance = prefsManager.getBalance(parsed.service) + parsed.amount + commission

            val validationResult = balanceValidator.validateBalance(
                parsed.service, parsed.amount, parsed.extractedBalance
            )

            val initialStatus = when {
                validationResult.isSuspicious -> "SUSPICIOUS"
                validationResult.isVerified -> "AUTO_APPROVED"
                parsed.hasAmountError -> "PENDING_APPROVAL"
                prefsManager.autoApproveEnabled -> "SENT"
                else -> "PENDING_APPROVAL"
            }

            val transaction = Transaction(
                service = parsed.service,
                amount = parsed.amount,
                sender = parsed.sender,
                transactionId = parsed.transactionId,
                simSlot = simSlot,
                timestamp = System.currentTimeMillis(),
                rawMessage = fullMessage,
                syncStatus = if (initialStatus == "AUTO_APPROVED" || initialStatus == "SENT") "pending_sync" else "pending_approval",
                commission = commission,
                newBalance = expectedNewBalance,
                status = initialStatus,
                balanceVerified = validationResult.isVerified,
                extractedBalance = parsed.extractedBalance ?: 0.0,
                expectedBalance = validationResult.expectedBalance
            )

            val id = dbHelper.insertTransaction(transaction)
            val savedTransaction = transaction.copy(id = id)

            try {
                val voiceManager = VoiceAlertManager(context.applicationContext)
                voiceManager.announceTransaction(parsed.amount, parsed.service)
            } catch (e: Exception) {
                Log.e(TAG, "Voice alert failed: ${e.message}")
            }

            when (initialStatus) {
                "SUSPICIOUS" -> notificationHelper.showSecurityAlertNotification(savedTransaction, validationResult.message)
                "AUTO_APPROVED", "SENT" -> {
                    val syncBalance = if (parsed.extractedBalance != null && parsed.extractedBalance > 0.0) parsed.extractedBalance else expectedNewBalance
                    prefsManager.updateBalance(parsed.service, syncBalance)
                    webhookSender.sendTransaction(savedTransaction)
                    notificationHelper.showVerifiedNotification(savedTransaction)
                }
                "PENDING_APPROVAL" -> {
                    if (parsed.hasAmountError) notificationHelper.showAmountErrorNotification(savedTransaction)
                    else notificationHelper.showApprovalNotification(savedTransaction, validationResult.isVerified)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "CRITICAL ERROR in SmsReceiver: ${e.message}")
        }
    }

    private fun getSimSlot(intent: Intent): Int {
        val extras = intent.extras ?: return 0
        return when {
            extras.containsKey("slot") -> extras.getInt("slot")
            extras.containsKey("simId") -> extras.getInt("simId")
            extras.containsKey("phone") -> extras.getInt("phone")
            extras.containsKey("subscription") -> extras.getInt("subscription")
            extras.containsKey("slot_id") -> extras.getInt("slot_id")
            else -> extras.getInt("android.telephony.extra.SLOT_INDEX", 0)
        }
    }
}
