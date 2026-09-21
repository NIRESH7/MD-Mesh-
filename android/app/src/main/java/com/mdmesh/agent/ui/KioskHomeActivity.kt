package com.mdmesh.agent.ui

import android.content.Intent
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.mdmesh.agent.data.CrossProcessLock
import com.mdmesh.agent.data.DevicePrefs
import com.mdmesh.agent.policy.HomeEnforcer
import com.mdmesh.agent.policy.PinSession
import com.mdmesh.agent.policy.ProtectionPin
import com.mdmesh.agent.policy.StrictLockHelper
import com.mdmesh.agent.service.PollingService
import com.mdmesh.agent.service.RecentsStickyService

/**
 * Default Home launcher while Restricted.
 * Shows only admin-allowlisted apps + allowed website links.
 * Refresh pulls the latest allowlist from the admin panel.
 */
class KioskHomeActivity : AppCompatActivity() {
    private lateinit var prefs: DevicePrefs
    private var bindingRoot: ScrollView? = null
    private var refreshing = false
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = DevicePrefs(this)
        if (!prefs.setupComplete) {
            startActivity(Intent(this, SetupActivity::class.java))
            finish()
            return
        }
        HomeEnforcer.enableHomeComponent(this)
        if (!prefs.servicesStoppedByPin) {
            requestSyncFromAdmin()
            RecentsStickyService.start(this)
        }
        prefs.publishCrossProcess()
        render()
    }

    override fun onResume() {
        super.onResume()
        prefs.publishCrossProcess()
        // Device Owner preferred Home — never bounce into RequireHome (that caused open/close loop)
        if (prefs.isLocked &&
            !HomeEnforcer.isOurLauncherDefault(this) &&
            !prefs.isServiceControlUnlocked &&
            !com.mdmesh.agent.policy.DeviceOwnerHelper.isDeviceOwner(this)
        ) {
            startActivity(Intent(this, RequireHomeActivity::class.java))
        }
        if (bindingRoot != null && !PinSession.active) {
            // Do not sync+full re-render every resume — that flickered the launcher
            render()
        }
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (CrossProcessLock.read(this).locked || prefs.isLocked) return
        super.onBackPressed()
    }

    private fun requestSyncFromAdmin() {
        if (prefs.servicesStoppedByPin) return
        val sync = Intent(this, PollingService::class.java).setAction(PollingService.ACTION_SYNC_NOW)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(sync) else startService(sync)
    }

    private fun refreshFromAdmin() {
        if (refreshing) return
        refreshing = true
        Toast.makeText(this, "Refreshing from admin panel…", Toast.LENGTH_SHORT).show()
        render()
        requestSyncFromAdmin()
        val before = prefs.lastSyncText
        var tries = 0
        val poll = object : Runnable {
            override fun run() {
                tries++
                val updated = prefs.lastSyncText != before || tries >= 8
                if (updated) {
                    refreshing = false
                    prefs.publishCrossProcess()
                    render()
                    val count = prefs.allowedPackages.size
                    val sites = prefs.allowedUrls.size
                    Toast.makeText(
                        this@KioskHomeActivity,
                        if (prefs.isLocked) {
                            "Updated — $count app(s), $sites site(s)"
                        } else {
                            "Updated — device unlocked from admin"
                        },
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    handler.postDelayed(this, 500L)
                }
            }
        }
        handler.postDelayed(poll, 600L)
    }

    private fun currentSnapshot(): CrossProcessLock.Snapshot {
        if (prefs.isLocked) {
            return CrossProcessLock.Snapshot(
                locked = true,
                allowed = prefs.allowedPackages,
                urls = prefs.allowedUrls,
                blockMedia = prefs.blockWebMedia,
                uninstallUntil = prefs.uninstallUnlockUntilMs,
                serviceUntil = prefs.serviceControlUnlockUntilMs,
                pinEntry = prefs.pinEntryActive
            )
        }
        return CrossProcessLock.read(this)
    }

    private fun render() {
        val snap = currentSnapshot()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(36, 48, 36, 36)
            setBackgroundColor(0xFFF4F7F8.toInt())
        }

        root.addView(TextView(this).apply {
            text = "PANDIYAN AGENCY"
            textSize = 24f
            setTextColor(0xFF0B1F2A.toInt())
            setPadding(0, 0, 0, 8)
        })
        root.addView(TextView(this).apply {
            text = "Home launcher — only apps & sites from admin"
            textSize = 14f
            setTextColor(0xFF1F6FEB.toInt())
            setPadding(0, 0, 0, 8)
        })
        root.addView(TextView(this).apply {
            text = "Last sync  ${prefs.lastSyncText}"
            textSize = 12f
            setTextColor(0xFF5A6A72.toInt())
            setPadding(0, 0, 0, 12)
        })

        root.addView(Button(this).apply {
            text = if (refreshing) "Refreshing…" else "Refresh from admin"
            isEnabled = !refreshing
            setOnClickListener { refreshFromAdmin() }
        })

        if (!snap.locked) {
            root.addView(TextView(this).apply {
                text = "Device is unlocked from admin. Use PIN to restore stock phone/tablet Home."
                textSize = 15f
                setPadding(0, 16, 0, 16)
            })
            root.addView(Button(this).apply {
                text = "Open agent settings"
                setOnClickListener {
                    startActivity(Intent(this@KioskHomeActivity, MainActivity::class.java))
                }
            })
            root.addView(Button(this).apply {
                text = "Restore phone Home (PIN)"
                setOnClickListener { askExitLauncherPin() }
            })
            val scroll = ScrollView(this).apply { addView(root) }
            bindingRoot = scroll
            setContentView(scroll)
            return
        }

        // Device Owner prefers Pandiyan Home; only prompt chooser when DO is off
        if (!HomeEnforcer.isOurLauncherDefault(this) &&
            !com.mdmesh.agent.policy.DeviceOwnerHelper.isDeviceOwner(this)
        ) {
            root.addView(TextView(this).apply {
                text = "Set Pandiyan Agency as Home (Always) — required on phones and tablets."
                textSize = 15f
                setTextColor(0xFFB00020.toInt())
                setPadding(0, 8, 0, 12)
            })
            root.addView(Button(this).apply {
                text = "Set as Home (Always)"
                setOnClickListener {
                    if (!HomeEnforcer.requestHomeRole(this@KioskHomeActivity)) {
                        HomeEnforcer.openHomeChooser(this@KioskHomeActivity)
                    }
                }
            })
        } else {
            // Ensure DO preferred Home stays applied
            HomeEnforcer.setPreferredHome(this)
        }

        if (!StrictLockHelper.isEnabled(this)) {
            root.addView(TextView(this).apply {
                text = "Strict Lock OFF — optional backup. Tap to enable if other apps stay open."
                textSize = 14f
                setTextColor(0xFFB00020.toInt())
                setPadding(0, 8, 0, 12)
            })
            root.addView(Button(this).apply {
                text = "Turn Strict Lock ON"
                setOnClickListener {
                    prefs.unlockServiceControlFor(5 * 60 * 1000L)
                    StrictLockHelper.openAccessibilitySettings(this@KioskHomeActivity)
                }
            })
        }

        root.addView(TextView(this).apply {
            text = "Allowed apps (${snap.allowed.size})"
            textSize = 16f
            setPadding(0, 16, 0, 8)
        })

        val grid = GridLayout(this).apply {
            columnCount = 3
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        val pm = packageManager
        if (snap.allowed.isEmpty()) {
            root.addView(TextView(this).apply {
                text = "No apps from admin yet. Tap Refresh after you add apps in the panel."
                textSize = 14f
                setPadding(0, 0, 0, 12)
            })
        } else {
            for (pkg in snap.allowed.toList().sorted()) {
                val label = runCatching {
                    val info = pm.getApplicationInfo(pkg, 0)
                    pm.getApplicationLabel(info).toString()
                }.getOrDefault(pkg.substringAfterLast('.'))
                val icon = runCatching { pm.getApplicationIcon(pkg) }.getOrNull()
                grid.addView(appTile(label, icon) {
                    val launch = pm.getLaunchIntentForPackage(pkg)
                    if (launch != null) startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    else Toast.makeText(this, "Cannot open $label", Toast.LENGTH_SHORT).show()
                })
            }
            root.addView(grid)
        }

        if (snap.urls.isNotEmpty()) {
            root.addView(TextView(this).apply {
                text = "Allowed websites (${snap.urls.size})"
                textSize = 16f
                setPadding(0, 20, 0, 8)
            })
            val webGrid = GridLayout(this).apply {
                columnCount = 2
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }
            for (url in snap.urls) {
                val title = runCatching {
                    Uri.parse(if (url.startsWith("http")) url else "https://$url").host ?: url
                }.getOrDefault(url)
                webGrid.addView(appTile(title, null) {
                    startActivity(
                        Intent(this@KioskHomeActivity, LockedBrowserActivity::class.java)
                            .putExtra(LockedBrowserActivity.EXTRA_URL, url)
                    )
                })
            }
            root.addView(webGrid)
        }

        root.addView(Button(this).apply {
            text = "Agent settings (PIN)"
            setOnClickListener { askOpenSettingsPin() }
        })
        root.addView(Button(this).apply {
            text = "Exit launcher (PIN)"
            setOnClickListener { askExitLauncherPin() }
        })

        val scroll = ScrollView(this).apply { addView(root) }
        bindingRoot = scroll
        setContentView(scroll)
    }

    private fun askOpenSettingsPin() {
        if (!prefs.hasProtectionPin) {
            Toast.makeText(
                this,
                "Set Uninstall PIN in the admin panel to open agent settings.",
                Toast.LENGTH_LONG
            ).show()
            return
        }
        PinSession.active = true
        prefs.pinEntryActive = true
        val host = PinPad.build(
            context = this,
            title = "Agent settings",
            message = "Enter the admin PIN to open Pandiyan agent settings.",
            confirmLabel = "Open settings",
            onConfirm = { pin ->
                if (!ProtectionPin.matches(pin, prefs.protectionPinHash)) {
                    Toast.makeText(this, "Wrong PIN", Toast.LENGTH_LONG).show()
                    return@build
                }
                PinSession.active = false
                prefs.pinEntryActive = false
                prefs.unlockServiceControlFor(10 * 60 * 1000L)
                startActivity(Intent(this, MainActivity::class.java))
                render()
            },
            onCancel = {
                PinSession.active = false
                prefs.pinEntryActive = false
                render()
            }
        )
        setContentView(ScrollView(this).apply { addView(host.root) })
        bindingRoot = null
    }

    private fun askExitLauncherPin() {
        if (!prefs.hasProtectionPin) {
            Toast.makeText(
                this,
                "Set Uninstall PIN in the admin panel to exit this launcher.",
                Toast.LENGTH_LONG
            ).show()
            return
        }
        PinSession.active = true
        prefs.pinEntryActive = true
        val host = PinPad.build(
            context = this,
            title = "Exit Pandiyan launcher",
            message = "Enter the admin PIN to leave this Home launcher and restore the phone/tablet Home picker.",
            confirmLabel = "Exit launcher",
            onConfirm = { pin ->
                if (!ProtectionPin.matches(pin, prefs.protectionPinHash)) {
                    Toast.makeText(this, "Wrong PIN", Toast.LENGTH_LONG).show()
                    return@build
                }
                PinSession.active = false
                prefs.pinEntryActive = false
                prefs.unlockServiceControlFor(10 * 60 * 1000L)
                HomeEnforcer.clearPreferredHome(this)
                HomeEnforcer.disableHomeComponent(this)
                Toast.makeText(
                    this,
                    "PIN OK — choose your phone Home app (Always)",
                    Toast.LENGTH_LONG
                ).show()
                HomeEnforcer.openHomeChooser(this)
                render()
            },
            onCancel = {
                PinSession.active = false
                prefs.pinEntryActive = false
                render()
            }
        )
        setContentView(ScrollView(this).apply { addView(host.root) })
        bindingRoot = null
    }

    private fun appTile(title: String, icon: Drawable?, onClick: () -> Unit): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(12, 20, 12, 20)
            layoutParams = GridLayout.LayoutParams().apply {
                width = 0
                height = ViewGroup.LayoutParams.WRAP_CONTENT
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
            }
            isClickable = true
            setOnClickListener { onClick() }
            addView(ImageView(this@KioskHomeActivity).apply {
                layoutParams = LinearLayout.LayoutParams(120, 120)
                if (icon != null) setImageDrawable(icon)
                else setBackgroundColor(0xFF1F6FEB.toInt())
            })
            addView(TextView(this@KioskHomeActivity).apply {
                text = title
                textSize = 12f
                gravity = Gravity.CENTER
                setPadding(0, 8, 0, 0)
                maxLines = 2
            })
        }
    }
}
