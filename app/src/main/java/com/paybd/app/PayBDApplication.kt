package com.paybd.app

import android.app.Application
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

class PayBDApplication : Application() {

    companion object {
        const val CHANNEL_ID = "paybd_service_channel"
        const val CHANNEL_NAME = "PayBD SMS Listener"
        const val APPROVAL_CHANNEL_ID = "paybd_approval_channel"
        const val APPROVAL_CHANNEL_NAME = "Transaction Approvals"
        const val ERROR_CHANNEL_ID = "paybd_error_channel"
        const val ERROR_CHANNEL_NAME = "Transaction Errors"
        const val SECURITY_CHANNEL_ID = "paybd_security_channel"
        const val SECURITY_CHANNEL_NAME = "Security Alerts"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)

            // v4.7: Service channel upgraded to HIGH priority for Infinix/Tecno device support
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "PayBD SMS Gateway Service - Keep alive for transaction monitoring"
                setShowBadge(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            manager.createNotificationChannel(serviceChannel)

            // Approval channel (high priority for action buttons)
            val approvalChannel = NotificationChannel(
                APPROVAL_CHANNEL_ID,
                APPROVAL_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Approve or cancel incoming transactions"
                setShowBadge(true)
                enableVibration(true)
            }
            manager.createNotificationChannel(approvalChannel)

            // Error channel (high priority for amount errors)
            val errorChannel = NotificationChannel(
                ERROR_CHANNEL_ID,
                ERROR_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Transaction parsing errors"
                setShowBadge(true)
                enableVibration(true)
            }
            manager.createNotificationChannel(errorChannel)

            // Security alert channel (max priority for fake SMS detection)
            val securityChannel = NotificationChannel(
                SECURITY_CHANNEL_ID,
                SECURITY_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Security alerts for balance mismatches and fake SMS detection"
                setShowBadge(true)
                enableVibration(true)
                enableLights(true)
            }
            manager.createNotificationChannel(securityChannel)
        }
    }
}
