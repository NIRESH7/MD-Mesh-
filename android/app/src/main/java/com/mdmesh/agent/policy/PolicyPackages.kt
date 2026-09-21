package com.mdmesh.agent.policy

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import com.mdmesh.agent.data.DevicePrefs

/**
 * Decides whether a foreground package is allowed under the current policy,
 * and records app foreground sessions for screen-time upload.
 */
object PolicyPackages {
    private val activeSession = AtomicReference<ActiveSession?>(null)

    data class ActiveSession(
        val packageName: String,
        val appName: String?,
        val startedAtMs: Long
    )

    fun isHomeOrSystem(context: Context, pkg: String): Boolean {
        if (pkg == context.packageName) return true
        // Settings apps are NOT system-exempt — only allowed if on the admin allowlist
        if (isSettingsPackage(pkg)) return false
        if (isPhoneOrDialer(pkg)) return true
        if (ALWAYS_ALLOW.any { pkg == it || pkg.startsWith("$it.") || pkg == it }) return true
        // Competing phone/tablet launchers are NOT allowed while Restricted —
        // Soft Lock returns the user to Pandiyan Home.
        if (isCompetingLauncher(pkg)) return false
        if (isDefaultLauncher(context, pkg) && pkg == context.packageName) return true
        return false
    }

    /** Incoming / outgoing call UI — never block while Restricted. */
    fun isPhoneOrDialer(pkg: String): Boolean {
        val p = pkg.lowercase()
        if (PHONE_DIALER_PACKAGES.any { p == it || p.startsWith("$it.") }) return true
        if (p.contains("incallui") || p.contains("telecom")) return true
        if (p.contains(".dialer") || p.endsWith("dialer")) return true
        if (p.startsWith("com.android.phone") || p.contains("com.android.phone")) return true
        if (p.contains("emergency")) return true
        return false
    }

    fun isCompetingLauncher(pkg: String): Boolean {
        val lower = pkg.lowercase()
        if (LAUNCHER_HINTS.any { lower.contains(it) }) return true
        return false
    }

    fun isAllowed(context: Context, prefs: DevicePrefs, pkg: String): Boolean {
        if (pkg == context.packageName) return true // Pandiyan Agency always openable
        if (!prefs.isLocked) return true
        if (prefs.allowedPackages.contains(pkg)) return true
        if (isSettingsPackage(pkg)) return false
        if (isHomeOrSystem(context, pkg)) return true
        return false
    }

    /** Phone Settings / MIUI security settings — blocked unless admin allowlists them. */
    fun isSettingsPackage(pkg: String): Boolean {
        val p = pkg.lowercase()
        return SETTINGS_PACKAGES.any { p == it || p.startsWith("$it.") }
    }

    fun onForegroundPackage(context: Context, prefs: DevicePrefs, pkg: String?) {
        if (pkg.isNullOrBlank()) return
        if (NOISE.any { pkg == it || pkg.startsWith(it) }) return

        val now = System.currentTimeMillis()
        val prev = activeSession.getAndSet(null)
        if (prev != null && prev.packageName != pkg) {
            finishSession(prefs, prev, now)
        } else if (prev != null && prev.packageName == pkg) {
            activeSession.set(prev)
            return
        }

        if (isHomeOrSystem(context, pkg) && !prefs.allowedPackages.contains(pkg)) {
            activeSession.set(null)
            return
        }

        val name = runCatching {
            val pm = context.packageManager
            val info = pm.getApplicationInfo(pkg, 0)
            pm.getApplicationLabel(info).toString()
        }.getOrNull()

        activeSession.set(ActiveSession(pkg, name, now))
    }

    fun flushOpenSession(prefs: DevicePrefs) {
        val prev = activeSession.getAndSet(null) ?: return
        finishSession(prefs, prev, System.currentTimeMillis())
    }

    private fun finishSession(prefs: DevicePrefs, session: ActiveSession, endedAtMs: Long) {
        var durationSec = ((endedAtMs - session.startedAtMs) / 1000L).toInt()
        if (durationSec < 1) return
        durationSec = durationSec.coerceAtMost(MAX_SESSION_SEC)
        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        val started = fmt.format(Date(session.startedAtMs))
        val ended = fmt.format(Date(endedAtMs))
        val key = "${session.packageName}:${session.startedAtMs}:${endedAtMs}:${UUID.randomUUID().toString().take(8)}"
        prefs.enqueueUsageSession(
            appPackage = session.packageName,
            appName = session.appName,
            startedAt = started,
            endedAt = ended,
            durationSec = durationSec,
            clientKey = key
        )
    }

    private fun isDefaultLauncher(context: Context, pkg: String): Boolean {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val resolve = context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        return resolve?.activityInfo?.packageName == pkg
    }

    private const val MAX_SESSION_SEC = 12 * 60 * 60

    private val ALWAYS_ALLOW = listOf(
        "com.android.systemui",
        "com.android.permissioncontroller",
        "com.google.android.permissioncontroller",
        "com.android.packageinstaller",
        "com.google.android.packageinstaller",
        "com.google.android.gms",
        "com.google.android.gsf",
        "com.android.phone",
        "com.android.server.telecom",
        "com.android.incallui",
        "com.android.emergency",
        "com.android.dialer",
        "com.google.android.dialer",
        "com.android.contacts",
        "com.coloros.dialer",
        "com.oppo.dialer",
        "com.realme.dialer",
        "com.samsung.android.dialer",
        "com.samsung.android.incallui",
        // Soft keyboards — never kick to Home while typing
        "com.google.android.inputmethod",
        "com.android.inputmethod",
        "com.samsung.android.honeyboard",
        "com.touchtype.swiftkey",
        "com.sohu.inputmethod",
        "com.baidu.input",
        "com.iflytek.inputmethod",
        "com.miui.input"
    )

    private val PHONE_DIALER_PACKAGES = listOf(
        "com.android.phone",
        "com.android.server.telecom",
        "com.android.incallui",
        "com.android.emergency",
        "com.android.dialer",
        "com.google.android.dialer",
        "com.android.contacts",
        "com.coloros.dialer",
        "com.oppo.dialer",
        "com.realme.dialer",
        "com.samsung.android.dialer",
        "com.samsung.android.incallui"
    )

    /** Blocked while restricted unless explicitly selected in the admin panel. */
    private val SETTINGS_PACKAGES = listOf(
        "com.android.settings",
        "com.android.settings.intelligence",
        "com.xiaomi.misettings",
        "com.miui.securitycenter",
        "com.miui.permcenter",
        "com.lbe.security.miui"
    )

    private val LAUNCHER_HINTS = listOf(
        ".launcher",
        "launcher3",
        "miui.home",
        "nexuslauncher",
        "trebuchet",
        "globallauncher",
        "microsoftlauncher",
        "lawnchair",
        "nova.launcher",
        "tinylauncher",
        "smartlauncher",
        "poco.launcher",
        "hios.launcher",
        "bbk.launcher", // vivo
        "coloros.launcher", // oppo
        "oppo.launcher",
        "realme.launcher",
        "samsung.android.app.launcher",
        "sec.android.app.launcher",
        "huawei.android.launcher",
        "lenovo.launcher"
    )

    private val NOISE = listOf(
        "com.android.systemui",
        "com.android.permissioncontroller",
        "com.google.android.permissioncontroller",
        "android"
    )
}
