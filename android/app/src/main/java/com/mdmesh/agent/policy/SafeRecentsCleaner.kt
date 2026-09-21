package com.mdmesh.agent.policy

import android.app.ActivityManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.mdmesh.agent.data.DevicePrefs
import com.mdmesh.agent.service.KioskKeepAliveService
import com.mdmesh.agent.service.RecentsStickyService

/**
 * Safe "Clear other apps" for phones + tablets.
 * Stops non-allowlisted apps without system Clear All
 * (system Clear All kills Soft Lock on Xiaomi).
 * Pandiyan + allowed apps + Home/system stay.
 */
object SafeRecentsCleaner {
    fun clearOtherApps(context: Context): Result {
        val prefs = DevicePrefs(context)
        if (!prefs.isLocked) {
            return Result(0, "Device is not restricted. Restrict from admin first.")
        }
        if (!StrictLockHelper.isWorking(context) && !StrictLockHelper.isListedInSettings(context)) {
            return Result(0, "Turn Strict Lock ON first, then tap Clear other apps.")
        }

        val keep = buildKeepSet(context, prefs)
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val killed = linkedSetOf<String>()

        runCatching {
            for (proc in am.runningAppProcesses.orEmpty()) {
                val pkg = proc.processName.substringBefore(':')
                if (shouldKeep(pkg, keep)) continue
                runCatching { am.killBackgroundProcesses(pkg) }
                killed += pkg
            }
        }

        runCatching {
            val pm = context.packageManager
            val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            for (app in apps) {
                val pkg = app.packageName
                if (shouldKeep(pkg, keep)) continue
                // Don't try to "kill" pure system packages beyond background kill
                if (app.flags and ApplicationInfo.FLAG_SYSTEM != 0 && isProtectedSystem(pkg)) continue
                runCatching { am.killBackgroundProcesses(pkg) }
                killed += pkg
            }
        }

        KioskAccessibilityService.requestSafeClear()
        runCatching { KioskKeepAliveService.start(context) }
        runCatching { RecentsStickyService.start(context) }
        prefs.publishCrossProcess()

        val count = killed.size
        val msg = if (count > 0) {
            "Cleared $count other app(s). Pandiyan kept. Only allowed apps can open."
        } else {
            "Only allowed apps can open. Use this instead of phone Clear All."
        }
        return Result(count, msg)
    }

    fun showToast(context: Context, message: String) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(context.applicationContext, message, Toast.LENGTH_LONG).show()
        }
    }

    private fun buildKeepSet(context: Context, prefs: DevicePrefs): Set<String> {
        return prefs.allowedPackages + context.packageName + PROTECTED_ALWAYS
    }

    private fun shouldKeep(pkg: String, keep: Set<String>): Boolean {
        if (pkg.isBlank()) return true
        if (pkg in keep) return true
        if (keep.any { pkg == it || pkg.startsWith("$it.") }) return true
        return isProtectedSystem(pkg)
    }

    private fun isProtectedSystem(pkg: String): Boolean {
        val p = pkg.lowercase()
        if (PROTECTED_ALWAYS.any { p == it || p.startsWith("$it.") }) return true
        if (PolicyPackages.isPhoneOrDialer(pkg)) return true
        if (p.contains("systemui") || p.contains("launcher") || p.contains("miui.home")) return true
        if (p.contains("inputmethod") || p.contains("keyboard") || p.contains("honeyboard")) return true
        if (p.startsWith("com.android.systemui")) return true
        if (p.startsWith("com.google.android.gms") || p.startsWith("com.google.android.gsf")) return true
        if (p.startsWith("com.android.phone") || p.startsWith("com.android.server.telecom")) return true
        if (p.startsWith("com.android.permissioncontroller")) return true
        if (p.startsWith("com.google.android.permissioncontroller")) return true
        return false
    }

    private val PROTECTED_ALWAYS = setOf(
        "com.android.systemui",
        "com.miui.home",
        "com.android.launcher",
        "com.android.launcher3",
        "com.google.android.apps.nexuslauncher",
        "com.samsung.android.app.launcher",
        "com.sec.android.app.launcher",
        "com.huawei.android.launcher",
        "com.oppo.launcher",
        "com.android.settings",
        "com.android.phone",
        "com.android.server.telecom",
        "com.android.incallui",
        "com.android.dialer",
        "com.google.android.dialer",
        "com.coloros.dialer",
        "com.oppo.dialer",
        "com.realme.dialer"
    )

    data class Result(val clearedCount: Int, val message: String)
}
