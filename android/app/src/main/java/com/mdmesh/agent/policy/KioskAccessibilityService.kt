package com.mdmesh.agent.policy

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import com.mdmesh.agent.data.CrossProcessLock
import com.mdmesh.agent.data.DevicePrefs
import com.mdmesh.agent.service.KioskKeepAliveService
import com.mdmesh.agent.service.RecentsStickyService

/**
 * Soft-locks non-allowed apps while Restricted.
 * Blocks Recents / Clear All (key + UI) so Soft Lock is not killed on Xiaomi.
 * Keeps normal phone/tablet Home. Only allowlisted apps stay open.
 */
class KioskAccessibilityService : AccessibilityService() {
    private var lastUrlBlockMs = 0L
    private var lastKickMs = 0L
    private var lastToastMs = 0L
    private var lastOurAppSeenMs = 0L
    private var lastRecentsHelpMs = 0L
    private var imePackages: Set<String> = emptySet()
    private var cached: CrossProcessLock.Snapshot = CrossProcessLock.Snapshot()
    private var lastCacheMs = 0L
    private val mainHandler = Handler(Looper.getMainLooper())
    private val watchdog = object : Runnable {
        override fun run() {
            refreshCache()
            if (pendingSafeClear) {
                pendingSafeClear = false
                runSafeClearPass()
            }
            enforceForegroundIfNeeded()
            mainHandler.postDelayed(this, 350L)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        StrictLockHelper.serviceConnected = true
        // Ensure key filtering is on (blocks Recents button while Restricted)
        runCatching {
            serviceInfo = serviceInfo?.apply {
                flags = flags or AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
            }
        }
        refreshImePackages()
        refreshCache(force = true)
        // Do NOT publishCrossProcess() here — stale prefs must never overwrite the lock file.
        mainHandler.postDelayed({
            runCatching { KioskKeepAliveService.start(this) }
            runCatching { RecentsStickyService.start(this) }
        }, 800L)
        mainHandler.removeCallbacks(watchdog)
        mainHandler.post(watchdog)
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        refreshCache()
        if (!cached.locked) return false
        if (DeviceOwnerHelper.isDeviceOwner(this)) return false
        val code = event.keyCode
        // Block Recents / App switch so Clear All cannot run
        if (code == KeyEvent.KEYCODE_APP_SWITCH || code == KeyEvent.KEYCODE_ALL_APPS) {
            if (event.action == KeyEvent.ACTION_DOWN) {
                blockSystemRecents()
            }
            return true
        }
        return super.onKeyEvent(event)
    }

    override fun onDestroy() {
        StrictLockHelper.serviceConnected = false
        mainHandler.removeCallbacks(watchdog)
        // Soft Lock died — keep FG guard running so Clear Recents recovery can prompt again
        runCatching { KioskKeepAliveService.start(applicationContext) }
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        refreshCache()

        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName) {
            lastOurAppSeenMs = System.currentTimeMillis()
            return
        }
        if (isInputMethod(pkg)) return

        val snap = cached
        if (snap.locked && RecentsCardGuard.isRecentsPackage(pkg)) {
            val className = event.className?.toString()
            val recentsUi = runCatching {
                rootInActiveWindow?.let { RecentsCardGuard.looksLikeRecentsUi(it) }
            }.getOrNull() == true || RecentsCardGuard.looksLikeRecentsClass(className)
            val deviceOwner = DeviceOwnerHelper.isDeviceOwner(this)

            // Soft Lock: leave Recents immediately so MIUI Clear All (X) cannot kill Soft Lock.
            if (!deviceOwner && recentsUi) {
                blockSystemRecents()
                return
            }

            if (event.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED) {
                if (RecentsCardGuard.isClearAllClick(event) || RecentsCardGuard.isMiuiClearAllControl(event)) {
                    refuseClearAll()
                    return
                }
            }
            // Stock MIUI / other launcher while Restricted → Pandiyan Home
            if (PolicyPackages.isCompetingLauncher(pkg)) {
                kickAway(null)
                return
            }
        }

        if (inOurAppGrace()) return

        if (snap.locked && snap.urls.isNotEmpty() && UrlAllowlist.isBrowserPackage(pkg)) {
            if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
                event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED ||
                event.eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED
            ) {
                enforceBrowserUrl(snap, pkg)
            }
        }

        val isSwitchEvent =
            event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
                event.eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED
        if (!isSwitchEvent) return

        val className = event.className?.toString()
        val prefs = DevicePrefs(this)
        PolicyPackages.onForegroundPackage(this, prefs, pkg)

        if (prefs.hasProtectionPin && UninstallGuard.shouldBlockUninstallUi(this, pkg, className)) {
            kickAway("Open Pandiyan Agency → Unlock uninstall (PIN)")
            return
        }

        if (snap.locked && isAccessibilitySettings(pkg, className) && !serviceUnlocked(snap)) {
            kickAway("Strict Lock is on — open Pandiyan Agency + PIN to change it")
            return
        }

        if (!snap.locked) return

        val foreground = foregroundPackage(pkg)
        if (foreground == packageName) {
            lastOurAppSeenMs = System.currentTimeMillis()
            return
        }
        if (isTemporarilyAllowed(snap, foreground, className)) return
        if (isAllowed(snap, foreground)) return
        kickAway(null)
    }

    private fun enforceForegroundIfNeeded() {
        val snap = cached
        if (!snap.locked) return
        if (inOurAppGrace()) return

        val pkg = topPackage() ?: return
        if (pkg == packageName) {
            lastOurAppSeenMs = System.currentTimeMillis()
            return
        }
        if (isInputMethod(pkg)) return
        if (RecentsCardGuard.isRecentsPackage(pkg)) {
            val recentsUi = runCatching {
                rootInActiveWindow?.let { RecentsCardGuard.looksLikeRecentsUi(it) }
            }.getOrNull() == true
            if (recentsUi && !DeviceOwnerHelper.isDeviceOwner(this)) {
                blockSystemRecents()
                return
            }
            if (recentsUi) return
            // Competing stock launcher while Restricted → Pandiyan Home (throttled)
            if (PolicyPackages.isCompetingLauncher(pkg)) {
                kickAway(null)
                return
            }
            if (recentsUi) return
        }

        if (snap.urls.isNotEmpty() && UrlAllowlist.isBrowserPackage(pkg)) {
            enforceBrowserUrl(snap, pkg)
        }

        if (isTemporarilyAllowed(snap, pkg, null)) return
        if (isAllowed(snap, pkg)) return
        kickAway(null)
    }

    private fun refuseClearAll() {
        blockSystemRecents()
    }

    /** Leave Recents immediately — Soft Lock must stay alive. */
    private fun blockSystemRecents() {
        val now = System.currentTimeMillis()
        if (now - lastKickMs < 1200L) return
        lastKickMs = now
        returnToPandiyanHome()
        runCatching { KioskKeepAliveService.start(this) }
        runCatching { RecentsStickyService.start(this) }
        StrictLockHelper.tryAutoEnable(this)
        if (now - lastRecentsHelpMs < 400L) return
        lastRecentsHelpMs = now
        if (now - lastToastMs > 2500) {
            lastToastMs = now
            mainHandler.post {
                Toast.makeText(
                    applicationContext,
                    "Recents blocked — use Pandiyan → Clear other apps",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    /** Soft clear: go Home, Soft Lock stays on, only allowed apps remain usable. */
    private fun runSafeClearPass() {
        returnToPandiyanHome()
        mainHandler.postDelayed({
            refreshCache(force = true)
            enforceForegroundIfNeeded()
        }, 400L)
        mainHandler.postDelayed({
            refreshCache(force = true)
            enforceForegroundIfNeeded()
        }, 1200L)
    }

    private fun protectFromClose(message: String) {
        returnToPandiyanHome()
        RecentsStickyService.keepAliveAfterClearAll(this, askPinToClose = false)
        val now = System.currentTimeMillis()
        if (now - lastToastMs > 2000) {
            lastToastMs = now
            mainHandler.post {
                Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun kickAway(message: String?) {
        val now = System.currentTimeMillis()
        if (now - lastKickMs < 1200L) return
        if (inOurAppGrace() || isOurUiOnTop()) return
        val top = topPackage()
        if (top != null && isAllowed(cached, top)) return
        lastKickMs = now
        returnToPandiyanHome()
        if (!message.isNullOrBlank() && now - lastToastMs > 2500) {
            lastToastMs = now
            mainHandler.post {
                Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Return to Pandiyan Home without double-launch flicker.
     * Device Owner: one HOME press (preferred activity). Otherwise start KioskHome once.
     */
    private fun returnToPandiyanHome() {
        if (isOurUiOnTop()) return
        HomeEnforcer.enableHomeComponent(this)
        if (DeviceOwnerHelper.isDeviceOwner(this)) {
            HomeEnforcer.setPreferredHome(this)
            performGlobalAction(GLOBAL_ACTION_HOME)
        } else {
            HomeEnforcer.goHome(this)
        }
    }

    private fun refreshCache(force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && now - lastCacheMs < 200L) return
        lastCacheMs = now
        val fromFile = CrossProcessLock.read(this)
        cached = fromFile
        // Prefer live prefs, but never wipe a good allowlist with an empty prefs set
        runCatching {
            val prefs = DevicePrefs(this)
            if (prefs.isLocked) {
                val allowed = prefs.allowedPackages.ifEmpty { fromFile.allowed }
                cached = CrossProcessLock.Snapshot(
                    locked = true,
                    allowed = allowed,
                    urls = prefs.allowedUrls.ifEmpty { fromFile.urls },
                    blockMedia = prefs.blockWebMedia,
                    uninstallUntil = prefs.uninstallUnlockUntilMs,
                    serviceUntil = prefs.serviceControlUnlockUntilMs,
                    pinEntry = prefs.pinEntryActive
                )
            }
        }
    }

    private fun inOurAppGrace(): Boolean =
        System.currentTimeMillis() - lastOurAppSeenMs < 900L

    private fun isAllowed(snap: CrossProcessLock.Snapshot, pkg: String): Boolean {
        if (pkg == packageName) return true
        if (!snap.locked) return true
        if (pkg in snap.allowed) return true
        if (PolicyPackages.isSettingsPackage(pkg)) return false
        return PolicyPackages.isHomeOrSystem(this, pkg)
    }

    private fun isTemporarilyAllowed(
        snap: CrossProcessLock.Snapshot,
        pkg: String,
        className: String?
    ): Boolean {
        val now = System.currentTimeMillis()
        val open = now < snap.uninstallUntil || now < snap.serviceUntil
        if (!open) return false
        return PolicyPackages.isSettingsPackage(pkg) || UninstallGuard.isUninstallRelated(pkg, className)
    }

    private fun serviceUnlocked(snap: CrossProcessLock.Snapshot): Boolean =
        System.currentTimeMillis() < snap.serviceUntil

    private fun foregroundPackage(eventPkg: String): String {
        val rootPkg = runCatching { rootInActiveWindow?.packageName?.toString() }.getOrNull()
        return rootPkg?.takeIf { it.isNotBlank() } ?: eventPkg
    }

    private fun topPackage(): String? {
        val fromRoot = runCatching { rootInActiveWindow?.packageName?.toString() }.getOrNull()
        if (!fromRoot.isNullOrBlank()) return fromRoot
        return runCatching {
            val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            @Suppress("DEPRECATION")
            am.getRunningTasks(1).firstOrNull()?.topActivity?.packageName
        }.getOrNull()
    }

    private fun isOurUiOnTop(): Boolean = topPackage() == packageName

    private fun isAccessibilitySettings(pkg: String, className: String?): Boolean {
        val cls = className.orEmpty()
        if (!PolicyPackages.isSettingsPackage(pkg) && !pkg.contains("settings", ignoreCase = true)) {
            return false
        }
        return cls.contains("Accessibility", ignoreCase = true) ||
            cls.contains("accessibility", ignoreCase = true)
    }

    private fun enforceBrowserUrl(snap: CrossProcessLock.Snapshot, pkg: String) {
        val root = rootInActiveWindow ?: return
        try {
            val rootPkg = root.packageName?.toString()
            if (rootPkg != null && rootPkg != pkg && !UrlAllowlist.isBrowserPackage(rootPkg)) return

            val raw = BrowserUrlReader.readCurrentUrl(root) ?: return
            if (BrowserUrlReader.isBrowserInternal(raw)) return

            if (snap.blockMedia && UrlAllowlist.isBlockedMedia(raw, true)) {
                blockForbiddenSite(snap, "Media blocked on this device")
                return
            }
            if (!UrlAllowlist.isAllowed(raw, snap.urls)) {
                blockForbiddenSite(snap, "Only allowed website(s) can open")
            }
        } finally {
            root.recycle()
        }
    }

    private fun blockForbiddenSite(snap: CrossProcessLock.Snapshot, message: String) {
        val now = System.currentTimeMillis()
        if (now - lastUrlBlockMs < 600) return
        lastUrlBlockMs = now
        performGlobalAction(GLOBAL_ACTION_BACK)
        mainHandler.postDelayed({ performGlobalAction(GLOBAL_ACTION_BACK) }, 180)
        val first = snap.urls.firstOrNull()
        if (!first.isNullOrBlank()) {
            mainHandler.postDelayed({
                runCatching {
                    startActivity(
                        Intent(this, com.mdmesh.agent.ui.LockedBrowserActivity::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                            .putExtra(com.mdmesh.agent.ui.LockedBrowserActivity.EXTRA_URL, first)
                    )
                }
            }, 350)
        }
        if (now - lastToastMs > 2500) {
            lastToastMs = now
            mainHandler.post {
                Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun refreshImePackages() {
        imePackages = runCatching {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.enabledInputMethodList.map { it.packageName }.toSet()
        }.getOrDefault(emptySet())
    }

    private fun isInputMethod(pkg: String): Boolean {
        if (imePackages.isEmpty()) refreshImePackages()
        if (pkg in imePackages) return true
        val p = pkg.lowercase()
        return p.contains("inputmethod") ||
            p.contains("input.method") ||
            p.contains("keyboard") ||
            p.contains("honeyboard") ||
            p.contains("gboard") ||
            p.contains("sogou") ||
            p.contains("baidu.input") ||
            p.contains("iflytek") ||
            p.startsWith("com.google.android.inputmethod") ||
            p.startsWith("com.android.inputmethod") ||
            p.startsWith("com.miui.input") ||
            (p.startsWith("com.xiaomi.") && p.contains("input")) ||
            p.startsWith("com.samsung.android.honeyboard") ||
            p.startsWith("com.touchtype") ||
            p.startsWith("com.sohu.inputmethod")
    }

    override fun onInterrupt() = Unit

    companion object {
        @Volatile
        var pendingSafeClear: Boolean = false

        fun requestSafeClear() {
            pendingSafeClear = true
        }
    }
}
