package com.paybd.app

import android.content.Context
import android.content.SharedPreferences

class PreferencesManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var webhookUrl: String
        get() = prefs.getString(KEY_WEBHOOK_URL, "") ?: ""
        set(value) { prefs.edit().putString(KEY_WEBHOOK_URL, value).apply() }

    var authToken: String
        get() = prefs.getString(KEY_AUTH_TOKEN, "") ?: ""
        set(value) { prefs.edit().putString(KEY_AUTH_TOKEN, value).apply() }

    var isServiceEnabled: Boolean
        get() = prefs.getBoolean(KEY_SERVICE_ENABLED, false)
        set(value) { prefs.edit().putBoolean(KEY_SERVICE_ENABLED, value).apply() }

    companion object {
        private const val PREFS_NAME        = "paybd_prefs"
        private const val KEY_WEBHOOK_URL   = "webhook_url"
        private const val KEY_AUTH_TOKEN    = "auth_token"
        private const val KEY_SERVICE_ENABLED = "service_enabled"
    }
}
