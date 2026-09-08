package com.mdmesh.agent.ui
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import android.view.View
import android.widget.ScrollView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.mdmesh.agent.R
import com.mdmesh.agent.databinding.ActivityMainBinding
import com.mdmesh.agent.data.DevicePrefs
import com.mdmesh.agent.policy.KioskAccessibilityService
import com.mdmesh.agent.policy.PinSession
import com.mdmesh.agent.policy.ProtectionPin
import com.mdmesh.agent.policy.UninstallGuard
import com.mdmesh.agent.service.PollingService

class MainActivity : AppCompatActivity() {
    private var binding: ActivityMainBinding? = null
    private lateinit var prefs: DevicePrefs
    private var showingAppGate = false
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = DevicePrefs(this)
        if (!prefs.setupComplete) {
            startActivity(Intent(this, SetupActivity::class.java))
            finish()
            return
        }
        if (needsAppPin() && !prefs.isAppSessionUnlocked) {
            showAppGate()
            return
        }
        showMainUi()
    }
    private fun needsAppPin(): Boolean = prefs.hasProtectionPin
    private fun showAppGate() {
        showingAppGate = true
        PinSession.active = true
        prefs.pinEntryActive = true
        val host = PinPad.build(
            context = this,
            title = "MD Mesh PIN",
            message = "Enter the admin panel PIN to open MD Mesh.\nUse the number pad (no keyboard).",
            confirmLabel = "Open MD Mesh",
            onConfirm = { pin ->
                if (!ProtectionPin.matches(pin, prefs.protectionPinHash)) {
                    Toast.makeText(this, "Wrong PIN", Toast.LENGTH_LONG).show()
                    return@build
                }
                prefs.unlockAppSessionFor(30 * 60 * 1000L)
                PinSession.active = false
                prefs.pinEntryActive = false
                showingAppGate = false
                showMainUi()
            },
            onCancel = {
                PinSession.active = false
                prefs.pinEntryActive = false
                finish()
            }
        )
        setContentView(ScrollView(this).apply { addView(host.root) })
    }
    private fun showMainUi() {
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding!!.root)
        startPolling()
        render()
        handler.post(refresher)
        binding!!.strictLock.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            Toast.makeText(
                this,
                "Find MD Mesh → turn ON. This enables full app blocking + uninstall protection.",
                Toast.LENGTH_LONG
            ).show()
        }
        binding!!.unlockUninstall.setOnClickListener { onUnlockOrLockUninstall() }
        binding!!.changePin.setOnClickListener {
            Toast.makeText(
                this,
                "PIN is managed in the admin panel (Settings). Change it there.",
                Toast.LENGTH_LONG
            ).show()
        }
        binding!!.openAllowedSite.setOnClickListener { openAllowedSitePicker() }
    }

    private fun openAllowedSitePicker() {
        val urls = prefs.allowedUrls
        if (urls.isEmpty()) {
            Toast.makeText(this, getString(R.string.no_allowed_sites), Toast.LENGTH_LONG).show()
            return
        }
        if (urls.size == 1) {
            startActivity(
                Intent(this, LockedBrowserActivity::class.java)
                    .putExtra(LockedBrowserActivity.EXTRA_URL, urls[0])
            )
            return
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.open_allowed_site)
            .setItems(urls.toTypedArray()) { _, which ->
                startActivity(
                    Intent(this, LockedBrowserActivity::class.java)
                        .putExtra(LockedBrowserActivity.EXTRA_URL, urls[which])
                )
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    private fun onUnlockOrLockUninstall() {
        if (!prefs.hasProtectionPin) {
            Toast.makeText(
                this,
                "No PIN yet. Set Uninstall PIN in the admin panel Settings.",
                Toast.LENGTH_LONG
            ).show()
            return
        }
        if (prefs.isUninstallUnlocked) {
            prefs.lockUninstall()
            UninstallGuard.applyBlockedState(this)
            Toast.makeText(this, "Uninstall locked again", Toast.LENGTH_SHORT).show()
            render()
            return
        }
        // Number-pad PIN screen — never open soft keyboard dialog
        startActivity(Intent(this, PinChallengeActivity::class.java))
    }
    private val handler = Handler(Looper.getMainLooper())
    private val refresher = object : Runnable {
        override fun run() {
            if (binding != null) render()
            handler.postDelayed(this, 2000)
        }
    }
    override fun onResume() {
        super.onResume()
        if (showingAppGate) {
            PinSession.active = true
            prefs.pinEntryActive = true
            return
        }
        if (needsAppPin() && !prefs.isAppSessionUnlocked && binding != null) {
            binding = null
            handler.removeCallbacks(refresher)
            showAppGate()
            return
        }
        if (binding != null) render()
        if (prefs.isLocked && !isStrictLockEnabled()) {
            Toast.makeText(this, "Turn ON Strict Lock (Accessibility) for full restriction", Toast.LENGTH_LONG).show()
        }
    }
    override fun onDestroy() {
        handler.removeCallbacks(refresher)
        if (showingAppGate && !prefs.isUninstallUnlocked) {
            PinSession.active = false
            prefs.pinEntryActive = false
        }
        super.onDestroy()
    }
    private fun startPolling() {
        val service = Intent(this, PollingService::class.java).setAction(PollingService.ACTION_SYNC_NOW)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(service) else startService(service)
    }
    private fun isStrictLockEnabled(): Boolean {
        val am = getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val enabled = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        val target = KioskAccessibilityService::class.java.canonicalName
        return enabled.any { it.resolveInfo.serviceInfo.name == target }
    }
    private fun render() {
        val b = binding ?: return
        b.deviceId.text = "Device ID  ${prefs.uniqueId}"
        b.lastSync.text = "Last sync  ${prefs.lastSyncText}"
        if (prefs.isLocked) {
            val count = prefs.allowedPackages.size.coerceAtLeast(1)
            b.statusTitle.text = getString(R.string.status_locked, count)
            b.lockedApp.text = prefs.lastLockedApp ?: ""
        } else {
            b.statusTitle.text = getString(R.string.status_unlocked)
            b.lockedApp.text = ""
        }
        b.strictLock.text = if (isStrictLockEnabled()) {
            getString(R.string.strict_lock_on)
        } else {
            getString(R.string.strict_lock_off)
        }
        if (!prefs.hasProtectionPin) {
            b.protectStatus.text = "Uninstall PIN: waiting for admin panel"
            b.unlockUninstall.text = getString(R.string.unlock_uninstall)
            b.changePin.text = "PIN set in admin panel"
        } else if (prefs.isUninstallUnlocked) {
            b.protectStatus.text = getString(R.string.protect_unlocked)
            b.unlockUninstall.text = getString(R.string.lock_uninstall_again)
            b.changePin.text = getString(R.string.change_pin)
        } else {
            b.protectStatus.text = getString(R.string.protect_locked)
            b.unlockUninstall.text = getString(R.string.unlock_uninstall)
            b.changePin.text = getString(R.string.change_pin)
        }
        if (prefs.hasWebAllowlist) {
            b.openAllowedSite.visibility = View.VISIBLE
            b.openAllowedSite.text = getString(R.string.open_allowed_site) + " (${prefs.allowedUrls.size})"
        } else {
            b.openAllowedSite.visibility = View.GONE
        }
    }
}
