package com.mdmesh.agent.policy

import android.accessibilityservice.AccessibilityService
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import com.mdmesh.agent.data.DevicePrefs
import com.mdmesh.agent.ui.PinChallengeActivity

/**
 * Soft-locks non-allowed apps while restricted.
 * When website allowlist is set, Chrome/Brave off-list navigations get Back.
 */
class KioskAccessibilityService : AccessibilityService() {
    private var lastPinPromptMs = 0L
    private var lastUrlBlockMs = 0L
    private var lastCheckedUrl: String? = null
    private var imePackages: Set<String> = emptySet()
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onServiceConnected() {
        super.onServiceConnected()
        refreshImePackages()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (PinSession.active) return
        val prefs = DevicePrefs(this)
        if (prefs.isUninstallUnlocked) return
        if (prefs.pinEntryActive) return
        if (isPinChallengeOnTop()) return

        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName) return
        if (isInputMethod(pkg)) return

        // Browser URL guard — also on content changes
        if (prefs.isLocked && prefs.hasWebAllowlist && UrlAllowlist.isBrowserPackage(pkg)) {
            if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
                event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED ||
                event.eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED
            ) {
                enforceBrowserUrl(prefs, pkg)
            }
        }

        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            return
        }

        val className = event.className?.toString()
        PolicyPackages.onForegroundPackage(this, prefs, pkg)

        if (prefs.hasProtectionPin && UninstallGuard.shouldBlockUninstallUi(this, pkg, className)) {
            openPinScreenOnly()
            return
        }

        if (!prefs.isLocked) return
        if (PolicyPackages.isAllowed(this, prefs, pkg)) return
        performGlobalAction(GLOBAL_ACTION_HOME)
    }

    private fun enforceBrowserUrl(prefs: DevicePrefs, pkg: String) {
        val root = rootInActiveWindow ?: return
        try {
            if (root.packageName?.toString() != pkg) return
            val raw = BrowserUrlReader.readCurrentUrl(root) ?: return
            if (BrowserUrlReader.isBrowserInternal(raw)) return

            if (prefs.blockWebMedia && UrlAllowlist.isBlockedMedia(raw, true)) {
                blockNavigation("Media blocked")
                return
            }
            if (!UrlAllowlist.isAllowed(raw, prefs.allowedUrls)) {
                if (raw == lastCheckedUrl) return
                lastCheckedUrl = raw
                blockNavigation("Only admin-allowed links can open")
            } else {
                lastCheckedUrl = raw
            }
        } finally {
            root.recycle()
        }
    }

    private fun blockNavigation(message: String) {
        val now = System.currentTimeMillis()
        if (now - lastUrlBlockMs < 900) return
        lastUrlBlockMs = now
        performGlobalAction(GLOBAL_ACTION_BACK)
        mainHandler.post {
            Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()
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

    private fun isPinChallengeOnTop(): Boolean {
        return runCatching {
            val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            @Suppress("DEPRECATION")
            val tasks = am.getRunningTasks(1)
            val top = tasks.firstOrNull()?.topActivity?.className ?: return false
            top.contains("PinChallengeActivity")
        }.getOrDefault(false)
    }

    private fun openPinScreenOnly() {
        val now = System.currentTimeMillis()
        if (now - lastPinPromptMs < 8000) return
        if (isPinChallengeOnTop()) return
        lastPinPromptMs = now
        PinSession.active = true
        runCatching {
            startActivity(
                Intent(this, PinChallengeActivity::class.java).addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                )
            )
        }
    }

    override fun onInterrupt() = Unit
}
