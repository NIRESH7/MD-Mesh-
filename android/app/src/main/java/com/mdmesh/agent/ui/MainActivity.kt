package com.mdmesh.agent.ui

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.view.accessibility.AccessibilityManager
import android.widget.ScrollView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.mdmesh.agent.policy.DeviceOwnerHelper
import com.mdmesh.agent.policy.HomeEnforcer
import com.mdmesh.agent.policy.KioskAccessibilityService
import com.mdmesh.agent.policy.OemProtectHelper
import com.mdmesh.agent.policy.PinSession
import com.mdmesh.agent.policy.ProtectionPin
import com.mdmesh.agent.policy.SafeRecentsCleaner
import com.mdmesh.agent.policy.StrictLockHelper
import com.mdmesh.agent.policy.UninstallGuard
import com.mdmesh.agent.service.PollingService
import com.mdmesh.agent.service.RecentsStickyService
import com.mdmesh.agent.R
import com.mdmesh.agent.data.DevicePrefs
import com.mdmesh.agent.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    private var binding: ActivityMainBinding? = null
    private lateinit var prefs: DevicePrefs

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = DevicePrefs(this)
        if (!prefs.setupComplete) {
            startActivity(Intent(this, SetupActivity::class.java))
            finish()
            return
        }
        // Pandiyan Home launcher while Restricted; Strict Lock blocks other apps.
        if (!prefs.servicesStoppedByPin) {
            startPolling()
            RecentsStickyService.start(this)
            com.mdmesh.agent.service.KioskKeepAliveService.start(this)
            com.mdmesh.agent.service.LockWatchdog.schedule(this)
        }
        prefs.publishCrossProcess()
        HomeEnforcer.applyForLockState(this, prefs.isLocked)
        OemProtectHelper.requestIgnoreBattery(this)
        if (prefs.isLocked && !HomeEnforcer.isOurLauncherDefault(this) && !prefs.isServiceControlUnlocked) {
            // Only prompt Home chooser when Device Owner is NOT forcing preferred Home
            if (!DeviceOwnerHelper.isDeviceOwner(this)) {
                startActivity(Intent(this, RequireHomeActivity::class.java))
            } else {
                HomeEnforcer.setPreferredHome(this)
                HomeEnforcer.goHome(this)
            }
        } else if (prefs.isLocked && !prefs.servicesStoppedByPin) {
            // Silent backup only — never force Strict Lock full screen
            StrictLockHelper.tryAutoEnable(this)
        }
        showMainUi()
        handleRecentsExtras(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleRecentsExtras(intent)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // Stay in Recents; Home is still available. PIN is required to drop this task.
        moveTaskToBack(true)
    }

    private fun handleRecentsExtras(intent: Intent?) {
        if (intent == null) return
        if (intent.getBooleanExtra(RecentsStickyService.EXTRA_REATTACHED, false) &&
            !intent.getBooleanExtra(RecentsStickyService.EXTRA_ASK_PIN_TO_CLOSE, false)
        ) {
            Toast.makeText(this, "Pandiyan stayed — other apps can be cleared", Toast.LENGTH_SHORT).show()
        }
        if (intent.getBooleanExtra(RecentsStickyService.EXTRA_ASK_PIN_TO_CLOSE, false)) {
            showCloseFromRecentsPin()
        }
        intent.removeExtra(RecentsStickyService.EXTRA_REATTACHED)
        intent.removeExtra(RecentsStickyService.EXTRA_ASK_PIN_TO_CLOSE)
    }

    private fun showMainUi() {
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding!!.root)
        if (!prefs.servicesStoppedByPin) {
            startPolling()
            RecentsStickyService.start(this)
        }
        render()
        handler.post(refresher)
        binding!!.strictLock.setOnClickListener { onStrictLockClicked() }
        binding!!.clearOtherApps.setOnClickListener { onClearOtherApps() }
        binding!!.unlockUninstall.setOnClickListener { onUnlockOrLockUninstall() }
        binding!!.changePin.setOnClickListener {
            Toast.makeText(
                this,
                "PIN is managed in the admin panel (Settings). Change it there.",
                Toast.LENGTH_LONG
            ).show()
        }
        binding!!.openAllowedSite.setOnClickListener { openAllowedSitePicker() }
        binding!!.stopServices.setOnClickListener { onStopOrStartServices() }
        binding!!.deviceOwnerHelp.setOnClickListener {
            if (prefs.isLocked) {
                startActivity(Intent(this, KioskHomeActivity::class.java))
            } else {
                HomeEnforcer.openHomeChooser(this)
            }
        }
        binding!!.deviceOwnerHelp.text = if (prefs.isLocked) {
            "Open Pandiyan Home launcher"
        } else {
            "Home launcher settings"
        }
        StrictLockHelper.ensureProtectionUi(this, prefs)
        HomeEnforcer.applyForLockState(this, prefs.isLocked)
    }

    private fun showCloseFromRecentsPin() {
        if (!prefs.hasProtectionPin) {
            Toast.makeText(
                this,
                "Set Uninstall PIN in the admin panel to close Pandiyan from Recents.",
                Toast.LENGTH_LONG
            ).show()
            return
        }
        requireAdminPin(
            title = "Close Pandiyan Agency",
            message = "Enter the admin PIN to remove Pandiyan from Recents. Restriction stays on in the background."
        ) {
            prefs.allowRecentsCloseFor(15_000L)
            Toast.makeText(this, "PIN OK — you can swipe Pandiyan away now", Toast.LENGTH_LONG).show()
            moveTaskToBack(true)
            handler.postDelayed({
                runCatching { finishAndRemoveTask() }
            }, 400)
        }
    }

    private fun onClearOtherApps() {
        if (!prefs.isLocked) {
            Toast.makeText(this, "Restrict this device from the admin panel first", Toast.LENGTH_LONG).show()
            return
        }
        if (!StrictLockHelper.isWorking(this) && !StrictLockHelper.isListedInSettings(this)) {
            Toast.makeText(
                this,
                "Turn Strict Lock ON from Pandiyan Home (Accessibility → Pandiyan Agency)",
                Toast.LENGTH_LONG
            ).show()
            prefs.unlockServiceControlFor(5 * 60 * 1000L)
            StrictLockHelper.openAccessibilitySettings(this)
            return
        }
        Toast.makeText(this, "Clearing other apps…", Toast.LENGTH_SHORT).show()
        handler.post {
            val result = SafeRecentsCleaner.clearOtherApps(this)
            SafeRecentsCleaner.showToast(this, result.message)
            // Stay in Pandiyan; Soft Lock continues to block non-allowed apps
            render()
        }
    }

    private fun onStrictLockClicked() {
        if (isStrictLockEnabled()) {
            requireAdminPin(
                title = "Turn off Strict Lock",
                message = "Enter the admin panel PIN to disable Strict Lock."
            ) {
                prefs.unlockServiceControlFor(5 * 60 * 1000L)
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                Toast.makeText(
                    this,
                    "PIN OK. Find Pandiyan Agency → turn OFF Strict Lock.",
                    Toast.LENGTH_LONG
                ).show()
                render()
            }
        } else {
            requireAdminPin(
                title = "Turn on Strict Lock",
                message = "Enter the admin panel PIN to enable Strict Lock (app blocking)."
            ) {
                prefs.unlockServiceControlFor(5 * 60 * 1000L)
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                Toast.makeText(
                    this,
                    "PIN OK. Find Pandiyan Agency → turn ON Strict Lock.",
                    Toast.LENGTH_LONG
                ).show()
                render()
            }
        }
    }

    private fun onStopOrStartServices() {
        if (prefs.servicesStoppedByPin) {
            requireAdminPin(
                title = "Start agent services",
                message = "Enter the admin panel PIN to start sync / download services again."
            ) {
                prefs.servicesStoppedByPin = false
                prefs.lockServiceControl()
                startPolling()
                RecentsStickyService.start(this)
                Toast.makeText(this, "Agent services started", Toast.LENGTH_SHORT).show()
                render()
            }
            return
        }
        requireAdminPin(
            title = "Stop agent services",
            message = "Enter the admin panel PIN to stop sync / download services on this device."
        ) {
            prefs.servicesStoppedByPin = true
            prefs.unlockServiceControlFor(5 * 60 * 1000L)
            stopPolling()
            stopService(Intent(this, RecentsStickyService::class.java))
            Toast.makeText(this, "Agent services stopped (PIN verified)", Toast.LENGTH_LONG).show()
            render()
        }
    }

    private fun requireAdminPin(title: String, message: String, onOk: () -> Unit) {
        if (!prefs.hasProtectionPin) {
            Toast.makeText(
                this,
                "No PIN yet. Set Uninstall PIN in the admin panel Settings.",
                Toast.LENGTH_LONG
            ).show()
            return
        }
        PinSession.active = true
        prefs.pinEntryActive = true
        val host = PinPad.build(
            context = this,
            title = title,
            message = message,
            confirmLabel = "Confirm",
            onConfirm = { pin ->
                if (!ProtectionPin.matches(pin, prefs.protectionPinHash)) {
                    Toast.makeText(this, "Wrong PIN", Toast.LENGTH_LONG).show()
                    return@build
                }
                PinSession.active = false
                prefs.pinEntryActive = false
                showMainUi()
                onOk()
            },
            onCancel = {
                PinSession.active = false
                prefs.pinEntryActive = false
                showMainUi()
            }
        )
        setContentView(ScrollView(this).apply { addView(host.root) })
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
        if (binding != null) render()
        HomeEnforcer.applyForLockState(this, prefs.isLocked)
        if (prefs.isLocked && !prefs.isServiceControlUnlocked) {
            if (DeviceOwnerHelper.isDeviceOwner(this)) {
                HomeEnforcer.setPreferredHome(this)
            } else if (!HomeEnforcer.isOurLauncherDefault(this)) {
                startActivity(Intent(this, RequireHomeActivity::class.java))
            }
        }
    }

    override fun onDestroy() {
        handler.removeCallbacks(refresher)
        super.onDestroy()
    }

    private fun startPolling() {
        val service = Intent(this, PollingService::class.java).setAction(PollingService.ACTION_SYNC_NOW)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(service) else startService(service)
    }

    private fun stopPolling() {
        stopService(Intent(this, PollingService::class.java))
    }

    private fun isStrictLockEnabled(): Boolean = StrictLockHelper.isEnabled(this)

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
        b.strictLock.text = when {
            StrictLockHelper.isWorking(this) -> getString(R.string.strict_lock_on)
            StrictLockHelper.isListedInSettings(this) ->
                "Strict Lock CONNECTING — tap → Off then On if stuck"
            else -> getString(R.string.strict_lock_off)
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
        b.stopServices.text = if (prefs.servicesStoppedByPin) {
            getString(R.string.start_services)
        } else {
            getString(R.string.stop_services)
        }
        b.deviceOwnerStatus.text = when {
            DeviceOwnerHelper.isDeviceOwner(this) ->
                "Protection: Device Owner ON"
            prefs.isLocked && HomeEnforcer.isOurLauncherDefault(this) && StrictLockHelper.isWorking(this) ->
                "Protection: Pandiyan Home + Strict Lock ON"
            prefs.isLocked && HomeEnforcer.isOurLauncherDefault(this) ->
                "Protection: Pandiyan Home ON — turn Strict Lock ON"
            prefs.isLocked ->
                "Protection: set Pandiyan as Home (Always) + Strict Lock"
            else ->
                "Protection: unlocked"
        }
        b.deviceOwnerHelp.visibility = View.VISIBLE
        b.deviceOwnerHelp.text = if (prefs.isLocked) {
            "Open Pandiyan Home launcher"
        } else {
            "Home launcher settings"
        }

        b.clearOtherApps.visibility = if (prefs.isLocked) View.VISIBLE else View.GONE

        if (prefs.hasWebAllowlist) {
            b.openAllowedSite.visibility = View.VISIBLE
            b.openAllowedSite.text = getString(R.string.open_allowed_site) + " (${prefs.allowedUrls.size})"
        } else {
            b.openAllowedSite.visibility = View.GONE
        }
        if (prefs.isLocked) {
            b.hint.text = when {
                HomeEnforcer.isOurLauncherDefault(this) && StrictLockHelper.isWorking(this) ->
                    "Pandiyan Home ON. Only allowed apps. Recents blocked. Use Clear other apps here."
                !HomeEnforcer.isOurLauncherDefault(this) ->
                    "Set Pandiyan Agency as Home (Always), then Strict Lock ON."
                else ->
                    "Turn Strict Lock ON. Only allowed apps will open from Pandiyan Home."
            }
        } else {
            b.hint.text =
                "When Restricted, Pandiyan becomes Home. Only selected apps. Use Clear other apps (not phone Clear All)."
        }
    }
}
