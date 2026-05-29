package com.paybd.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log

class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isNullOrEmpty()) return

        messages
            .groupBy { it.originatingAddress }
            .forEach { (sender, parts) ->
                if (sender == null) return@forEach
                val body = parts.joinToString("") { it.messageBody }
                Log.d(TAG, "SMS received from $sender")

                if (SmsParser.isBkashSms(sender, body)) {
                    Log.d(TAG, "bKash SMS detected — forwarding to webhook")
                    val smsData = SmsParser.parse(sender, body)
                    forwardToWebhook(context, smsData)
                }
            }
    }

    private fun forwardToWebhook(context: Context, smsData: SmsData) {
        val prefs = PreferencesManager(context)
        val url   = prefs.webhookUrl

        if (url.isBlank()) {
            Log.w(TAG, "Webhook URL not set — skipping forward")
            return
        }

        WebhookSender(url, prefs.authToken).send(smsData) { ok, msg ->
            Log.i(TAG, "Webhook dispatch ${if (ok) "succeeded" else "failed"}: $msg")
        }
    }

    companion object {
        private const val TAG = "SmsReceiver"
    }
}
