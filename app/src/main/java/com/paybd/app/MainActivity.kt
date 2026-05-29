package com.paybd.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Switch
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var etWebhookUrl: EditText
    private lateinit var etAuthToken: EditText
    private lateinit var switchService: Switch
    private lateinit var btnSave: Button

    private val prefs by lazy { PreferencesManager(this) }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants.all { it.value }) {
            Toast.makeText(this, "All permissions granted", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(
                this,
                "SMS permissions are required for this app to work",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        etWebhookUrl = findViewById(R.id.et_webhook_url)
        etAuthToken  = findViewById(R.id.et_auth_token)
        switchService = findViewById(R.id.switch_service)
        btnSave      = findViewById(R.id.btn_save)

        loadSettings()
        requestMissingPermissions()

        btnSave.setOnClickListener { saveSettings() }

        switchService.setOnCheckedChangeListener { _, checked ->
            if (checked) startMonitoring() else stopMonitoring()
        }
    }

    private fun loadSettings() {
        etWebhookUrl.setText(prefs.webhookUrl)
        etAuthToken.setText(prefs.authToken)
        switchService.isChecked = prefs.isServiceEnabled
    }

    private fun saveSettings() {
        val url   = etWebhookUrl.text.toString().trim()
        val token = etAuthToken.text.toString().trim()

        if (url.isEmpty()) {
            Toast.makeText(this, "Webhook URL cannot be empty", Toast.LENGTH_SHORT).show()
            return
        }

        prefs.webhookUrl = url
        prefs.authToken  = token
        Toast.makeText(this, "Settings saved", Toast.LENGTH_SHORT).show()
    }

    private fun startMonitoring() {
        prefs.isServiceEnabled = true
        val intent = Intent(this, SmsService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun stopMonitoring() {
        prefs.isServiceEnabled = false
        stopService(Intent(this, SmsService::class.java))
    }

    private fun requestMissingPermissions() {
        val needed = mutableListOf<String>()

        fun needsPermission(p: String) =
            ContextCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_GRANTED

        if (needsPermission(Manifest.permission.RECEIVE_SMS)) needed += Manifest.permission.RECEIVE_SMS
        if (needsPermission(Manifest.permission.READ_SMS))    needed += Manifest.permission.READ_SMS

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (needsPermission(Manifest.permission.POST_NOTIFICATIONS))
                needed += Manifest.permission.POST_NOTIFICATIONS
        }

        if (needed.isNotEmpty()) permissionLauncher.launch(needed.toTypedArray())
    }
}
