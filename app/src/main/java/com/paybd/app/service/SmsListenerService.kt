package com.paybd.app.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.paybd.app.utils.PrefsManager

class SmsListenerService : Service() {
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val prefsManager = PrefsManager(this)
        prefsManager.isServiceRunning = true
        return START_STICKY
    }
    override fun onBind(intent: Intent?): IBinder? = null
}