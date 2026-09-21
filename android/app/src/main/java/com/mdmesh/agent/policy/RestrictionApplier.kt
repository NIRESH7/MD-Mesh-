package com.mdmesh.agent.policy

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
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

        if (shouldLock) {
            prefs.lockUninstall()
            prefs.lockServiceControl()
            prefs.pinEntryActive = false
            PinSession.active = false
        }

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
            // Always re-apply hide/suspend while locked (Clear Recents safe with Device Owner)
            applyAllowlist(allowed, showToast = changed)
            HomeEnforcer.applyForLockState(context, locked = true)
            if (changed) HomeEnforcer.goHome(context)
        } else if (changed) {
            unlockDevice(showToast = true)
            HomeEnforcer.applyForLockState(context, locked = false)
        }

        prefs.lastRestriction = key
        prefs.isLocked = shouldLock
        prefs.publishCrossProcess()
    }

    /** Re-hide apps after Clear Recents when Device Owner is on (no toast). */
    fun reapplyIfLocked() {
        if (!prefs.isLocked) return
        val allowed = prefs.allowedPackages
        if (allowed.isEmpty()) return
        applyAllowlist(allowed, showToast = false)
        HomeEnforcer.applyForLockState(context, locked = true)
        prefs.publishCrossProcess()
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
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    runCatching { dpm.setPackagesSuspended(admin, arrayOf(pkg), true) }
                }
            }
            for (pkg in allowed) {
                runCatching { dpm.setApplicationHidden(admin, pkg, false) }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    runCatching { dpm.setPackagesSuspended(admin, arrayOf(pkg), false) }
                }
            }
            runCatching { dpm.setApplicationHidden(admin, context.packageName, false) }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                runCatching { dpm.setPackagesSuspended(admin, arrayOf(context.packageName), false) }
            }
            val lockTask = (allowed + context.packageName).toTypedArray()
            runCatching { dpm.setLockTaskPackages(admin, lockTask) }
            if (showToast) {
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(
                        context,
                        "Device Owner: ${allowed.size} app(s). Normal Home. Clear Recents OK.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        } else if (showToast) {
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(
                    context,
                    "Restricted: normal Home + Strict Lock. Only allowed apps. Set Device Owner for Clear Recents.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
        prefs.hiddenPackages = if (canHide) hidden else emptySet()
    }

    fun clearAllRestrictions(showToast: Boolean = true) {
        prefs.isLocked = false
        prefs.allowedPackages = emptySet()
        prefs.allowedUrls = emptyList()
        prefs.blockWebMedia = true
        prefs.lastLockedApp = null
        prefs.lastRestriction = "0:"
        unlockDevice(showToast)
        HomeEnforcer.applyForLockState(context, locked = false)
    }

    private fun unlockDevice(showToast: Boolean = true) {
        if (dpm.isDeviceOwnerApp(context.packageName)) {
            val toClear = prefs.hiddenPackages.toMutableSet()
            runCatching {
                val packages = context.packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
                for (app in packages) toClear += app.packageName
            }
            for (pkg in toClear) {
                runCatching { dpm.setApplicationHidden(admin, pkg, false) }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    runCatching { dpm.setPackagesSuspended(admin, arrayOf(pkg), false) }
                }
            }
            runCatching { dpm.setLockTaskPackages(admin, emptyArray()) }
        }
        prefs.hiddenPackages = emptySet()
        prefs.allowedPackages = emptySet()
        stopLockTaskIfPossible()
        HomeEnforcer.applyForLockState(context, locked = false)
        prefs.publishCrossProcess()
        if (showToast) {
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(context, "All apps unlocked", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun shouldProtect(pkg: String, allowed: Set<String>, info: ApplicationInfo): Boolean {
        if (pkg in allowed || pkg == context.packageName) return true
        if (PolicyPackages.isCompetingLauncher(pkg)) return true // never hide stock Home
        if (PolicyPackages.isPhoneOrDialer(pkg)) return true
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
        // Keep system launchers visible when Device Owner hides other apps
        private val PROTECTED = listOf(
            "android",
            "com.android.systemui",
            "com.android.phone",
            "com.android.server.telecom",
            "com.android.incallui",
            "com.android.dialer",
            "com.google.android.dialer",
            "com.android.contacts",
            "com.coloros.dialer",
            "com.oppo.dialer",
            "com.realme.dialer",
            "com.android.providers",
            "com.google.android.gms",
            "com.google.android.gsf",
            "com.google.android.gsf.login",
            "com.google.android.permissioncontroller",
            "com.android.permissioncontroller",
            "com.android.settings",
            "com.android.vending",
            "com.google.android.packageinstaller",
            "com.android.packageinstaller",
            "com.google.android.apps.wellbeing",
            "com.miui.securitycenter",
            "com.miui.home",
            "com.android.launcher",
            "com.android.launcher3",
            "com.google.android.apps.nexuslauncher",
            "com.samsung.android.app.launcher",
            "com.sec.android.app.launcher",
            "com.huawei.android.launcher",
            "com.oppo.launcher",
            "com.bbk.launcher2",
            "com.vivo.launcher",
            "com.lenovo.launcher",
            "com.teslacoilsw.launcher"
        )
    }
}
