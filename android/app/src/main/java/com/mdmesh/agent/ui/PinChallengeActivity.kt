package com.mdmesh.agent.ui

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.mdmesh.agent.data.DevicePrefs
import com.mdmesh.agent.policy.MeshDeviceAdminReceiver
import com.mdmesh.agent.policy.PinSession
import com.mdmesh.agent.policy.ProtectionPin
import com.mdmesh.agent.policy.RestrictionClearer
import com.mdmesh.agent.service.PollingService
import kotlinx.coroutines.launch

class PinChallengeActivity : AppCompatActivity() {
    private lateinit var prefs: DevicePrefs

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = DevicePrefs(this)
        beginPinSession()
        if (prefs.isUninstallUnlocked) {
            enterUninstallModeUi()
            return
        }
        showPinForm()
    }

    override fun onResume() {
        super.onResume()
        beginPinSession()
    }

    override fun onDestroy() {
        if (!prefs.isUninstallUnlocked) {
            endPinSession()
        }
        super.onDestroy()
    }

    private fun beginPinSession() {
        PinSession.active = true
        prefs.pinEntryActive = true
    }

    private fun endPinSession() {
        PinSession.active = false
        prefs.pinEntryActive = false
    }

    private fun showPinForm() {
        val host = PinPad.build(
            context = this,
            title = "Enter uninstall PIN",
            message = "This clears app restriction so you can uninstall.\nUse the number pad (no keyboard).\n\nAfter unlock:\n1) Accessibility → Pandiyan Agency → OFF\n2) Device Admin → OFF\n3) Uninstall",
            confirmLabel = "Unlock uninstall",
            onConfirm = { pin -> tryUnlock(pin) },
            onCancel = {
                endPinSession()
                finish()
            }
        )
        setContentView(ScrollView(this).apply { addView(host.root) })
    }

    private fun tryUnlock(pin: String) {
        if (!ProtectionPin.matches(pin, prefs.protectionPinHash)) {
            Toast.makeText(this, "Wrong PIN", Toast.LENGTH_LONG).show()
            return
        }
        lifecycleScope.launch {
            RestrictionClearer.clearLocalAndServer(this@PinChallengeActivity, pin)
            prefs.unlockUninstallFor(60 * 60 * 1000L)
            beginPinSession()
            stopSyncService()
            Toast.makeText(
                this@PinChallengeActivity,
                "Unlocked. Turn OFF Accessibility, then Device Admin, then Uninstall.",
                Toast.LENGTH_LONG
            ).show()
            enterUninstallModeUi()
        }
    }

    private fun enterUninstallModeUi() {
        stopSyncService()
        beginPinSession()
        val title = TextView(this).apply {
            text = "Uninstall mode ON"
            textSize = 22f
            setPadding(0, 0, 0, 16)
        }
        val message = TextView(this).apply {
            text = "Do in this order (required):\n\n" +
                "① Accessibility → MD Mesh → OFF\n" +
                "② Device Admin → MD Mesh → OFF\n" +
                "③ Apps → MD Mesh → Uninstall\n\n" +
                "If Accessibility stays ON, uninstall will keep failing."
            textSize = 15f
            setPadding(0, 0, 0, 16)
        }
        val a11y = Button(this).apply {
            text = "① Turn OFF Accessibility"
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        }
        val admin = Button(this).apply {
            text = "② Turn OFF Device Admin"
            setOnClickListener { openDeviceAdminSettings() }
        }
        val appInfo = Button(this).apply {
            text = "③ Uninstall MD Mesh"
            setOnClickListener {
                startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = android.net.Uri.parse("package:$packageName")
                    }
                )
            }
        }
        val close = Button(this).apply {
            text = "Close"
            setOnClickListener { finish() }
        }
        setContentView(
            ScrollView(this).apply {
                addView(
                    LinearLayout(this@PinChallengeActivity).apply {
                        orientation = LinearLayout.VERTICAL
                        setPadding(48, 64, 48, 48)
                        setBackgroundColor(0xFFF4F7F8.toInt())
                        addView(title)
                        addView(message)
                        addView(a11y)
                        addView(admin)
                        addView(appInfo)
                        addView(close)
                    }
                )
            }
        )
    }

    private fun stopSyncService() {
        runCatching { stopService(Intent(this, PollingService::class.java)) }
    }

    private fun openDeviceAdminSettings() {
        val tries = listOf(
            Intent("android.settings.DEVICE_ADMIN_SETTINGS"),
            Intent(Settings.ACTION_SECURITY_SETTINGS),
            Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                putExtra(
                    DevicePolicyManager.EXTRA_DEVICE_ADMIN,
                    ComponentName(this@PinChallengeActivity, MeshDeviceAdminReceiver::class.java)
                )
            },
            Intent(Settings.ACTION_SETTINGS)
        )
        for (intent in tries) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (runCatching { startActivity(intent); true }.getOrDefault(false)) return
        }
    }
}
