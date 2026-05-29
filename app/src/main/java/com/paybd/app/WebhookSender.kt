package com.paybd.app

import android.util.Log
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Sends parsed bKash transaction data as a JSON POST to a configurable webhook URL.
 *
 * Usage:
 *   WebhookSender(webhookUrl, authToken).send(smsData) { ok, msg -> ... }
 */
class WebhookSender(
    private val webhookUrl: String,
    private val authToken: String = ""
) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    fun send(smsData: SmsData, callback: (success: Boolean, message: String) -> Unit) {
        if (webhookUrl.isBlank()) {
            callback(false, "Webhook URL is not configured")
            return
        }

        val body = buildPayload(smsData).toRequestBody(JSON_TYPE)

        val reqBuilder = Request.Builder()
            .url(webhookUrl)
            .post(body)
            .header("Content-Type", "application/json")
            .header("User-Agent", "PayBD-SMS-Forwarder/1.0")

        if (authToken.isNotBlank()) {
            reqBuilder.header("Authorization", "Bearer $authToken")
        }

        client.newCall(reqBuilder.build()).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "Webhook request failed: ${e.message}", e)
                callback(false, e.message ?: "Network error")
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    val ok  = response.isSuccessful
                    val msg = if (ok) "HTTP ${response.code} OK" else "HTTP ${response.code} Error"
                    Log.d(TAG, "Webhook → $msg")
                    callback(ok, msg)
                }
            }
        })
    }

    private fun buildPayload(smsData: SmsData): String =
        JSONObject().apply {
            put("sender",    smsData.sender)
            put("message",   smsData.rawMessage)
            put("type",      smsData.type.name)
            put("timestamp", smsData.timestamp)
            smsData.amount?.let        { put("amount",         it) }
            smsData.transactionId?.let { put("transaction_id", it) }
            smsData.balance?.let       { put("balance",        it) }
            smsData.fromPhone?.let     { put("from_phone",     it) }
            smsData.toPhone?.let       { put("to_phone",       it) }
        }.toString()

    companion object {
        private const val TAG = "WebhookSender"
        private val JSON_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
