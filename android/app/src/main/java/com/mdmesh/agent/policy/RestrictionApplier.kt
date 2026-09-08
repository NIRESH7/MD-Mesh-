package com.mdmesh.agent.policy

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.mdmesh.agent.data.DevicePrefs
import com.mdmesh.agent.data.Restriction

class RestrictionApplier(private val context: Context) {
    private val prefs = DevicePrefs(context)
    private val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    private val admin = ComponentName(context, MeshDeviceAdminReceiver::class.java)

    fun apply(restriction: Restriction) {
        val allowed = restriction.allowed_apps
            .map { it.app_package.trim() }
            .filter { it.isNotEmpty() }
            .toSet()
            .ifEmpty {
                val legacy = restriction.locked_to_app?.trim().orEmpty()
                if (legacy.contains('.') && !legacy.contains(' ')) setOf(legacy) else emptySet()
            }

        val urls = UrlAllowlist.normalizeList(restriction.allowed_urls)
        val blockMedia = restriction.block_web_media != 0

        val shouldLock = restriction.is_locked == 1 && allowed.isNotEmpty()
        val key = "${if (shouldLock) 1 else 0}:${allowed.sorted().joinToString(",")}:${urls.joinToString("|")}:$blockMedia"
        val changed = key != prefs.lastRestriction

        prefs.isLocked = shouldLock
        prefs.allowedPackages = if (shouldLock) allowed else emptySet()
        prefs.allowedUrls = if (shouldLock) urls else emptyList()
        prefs.blockWebMedia = if (shouldLock) blockMedia else true
        prefs.lastLockedApp = if (shouldLock) {
            restriction.locked_to_app ?: "${allowed.size} apps"
        } else {
            null
        }

        if (shouldLock) {
            if (changed) {
                applyAllowlist(allowed, showToast = true)
            }
        } else if (changed || prefs.isLocked) {
            // Always clear local soft-lock leftovers when server says free
            unlockDevice(showToast = changed)
        }

        prefs.lastRestriction = key
        prefs.isLocked = shouldLock
    }

    private fun applyAllowlist(allowed: Set<String>, showToast: Boolean) {
        val hidden = mutableSetOf<String>()
        val packages = context.packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
        val canHide = dpm.isDeviceOwnerApp(context.packageName)
        if (canHide) {
            for (app in packages) {
                val pkg = app.packageName
                if (shouldProtect(pkg, allowed, app)) continue
                val hiddenOk = runCatching {
                    dpm.setApplicationHidden(admin, pkg, true)
                    true
                }.getOrDefault(false)
                if (hiddenOk) hidden += pkg
            }
            for (pkg in allowed) {
                runCatching { dpm.setApplicationHidden(admin, pkg, false) }
            }
            val lockTask = (allowed + context.packageName).toTypedArray()
            runCatching { dpm.setLockTaskPackages(admin, lockTask) }
        } else if (showToast) {
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(
                    context,
                    "Restricted to ${allowed.size} app(s). Home stays available.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
        prefs.hiddenPackages = hidden
    }

    /** Fully clear allowlist / soft-lock so every app works again. */
    fun clearAllRestrictions(showToast: Boolean = true) {
        prefs.isLocked = false
        prefs.allowedPackages = emptySet()
        prefs.allowedUrls = emptyList()
        prefs.blockWebMedia = true
        prefs.lastLockedApp = null
        prefs.lastRestriction = "0:"
        unlockDevice(showToast)
    }

    private fun unlockDevice(showToast: Boolean = true) {
        if (dpm.isDeviceOwnerApp(context.packageName)) {
            for (pkg in prefs.hiddenPackages) {
                runCatching { dpm.setApplicationHidden(admin, pkg, false) }
            }
            // Also unhide any apps we might have hidden before prefs were cleared
            runCatching {
                val packages = context.packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
                for (app in packages) {
                    runCatching { dpm.setApplicationHidden(admin, app.packageName, false) }
                }
            }
            runCatching { dpm.setLockTaskPackages(admin, emptyArray()) }
        }
        prefs.hiddenPackages = emptySet()
        prefs.allowedPackages = emptySet()
        stopLockTaskIfPossible()
        if (showToast) {
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(context, "All apps unlocked", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun shouldProtect(pkg: String, allowed: Set<String>, info: ApplicationInfo): Boolean {
        if (pkg in allowed || pkg == context.packageName) return true
        if (PolicyPackages.isHomeOrSystem(context, pkg)) return true
        if (PROTECTED.any { pkg == it || pkg.startsWith(it) }) return true
        if (info.flags and ApplicationInfo.FLAG_SYSTEM != 0 && pkg.startsWith("com.android.systemui")) return true
        return false
    }

    private fun stopLockTaskIfPossible() {
        val activity = context as? Activity ?: return
        runCatching { activity.stopLockTask() }
    }

    companion object {
        private val PROTECTED = listOf(
            "android",
            "com.android.systemui",
            "com.android.phone",
            "com.android.server.telecom",
            "com.android.providers",
            "com.android.settings.intelligence",
            "com.google.android.gms",
            "com.google.android.gsf",
            "com.google.android.permissioncontroller",
            "com.android.permissioncontroller"
        )
    }
}
