package com.paybd.app.utils

import android.content.Context
import com.paybd.app.data.model.Transaction

class WebhookSender(private val context: Context) {
    fun sendTransaction(transaction: Transaction) {
        // Logic to send webhook
    }
    fun sendCancellation(transaction: Transaction) {
        // Logic to send cancellation
    }
}