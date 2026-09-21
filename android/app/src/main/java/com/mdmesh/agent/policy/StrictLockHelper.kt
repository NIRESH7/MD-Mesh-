package com.mdmesh.agent.policy

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import com.mdmesh.agent.data.DevicePrefs

object StrictLockHelper {
    @Volatile
    var serviceConnected: Boolean = false

    private fun serviceFlattened(context: Context): String =
        ComponentName(context, KioskAccessibilityService::class.java).flattenToString()

    private fun shortComponent(context: Context): String =
        "${context.packageName}/.policy.KioskAccessibilityService"

    /** Truly working Soft Lock (bound and receiving events). */
    fun isWorking(context: Context): Boolean {
        if (serviceConnected) return true
        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val enabled = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        val target = KioskAccessibilityService::class.java.canonicalName
        return enabled.any { it.resolveInfo.serviceInfo.name == target }
    }

    /** User turned Pandiyan ON in Accessibility settings (may still be Binding / DEAD). */
    fun isListedInSettings(context: Context): Boolean {
        val flat = serviceFlattened(context)
        val short = shortComponent(context)
        val raw = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return raw.split(':').any {
            it.equals(flat, ignoreCase = true) || it.equals(short, ignoreCase = true)
        }
    }

    /** UI + enforcement: Soft Lock is usable only when actually working. */
    fun isEnabled(context: Context): Boolean = isWorking(context)

    /**
     * Re-enable our accessibility service when WRITE_SECURE_SETTINGS is granted.
     */
    fun tryAutoEnable(context: Context): Boolean {
        if (isListedInSettings(context) && isWorking(context)) return true
        val flat = serviceFlattened(context)
        return runCatching {
            val cr = context.contentResolver
            val cur = Settings.Secure.getString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            val next = when {
                cur.isNullOrBlank() -> flat
                cur.split(':').any {
                    it.equals(flat, ignoreCase = true) ||
                        it.equals(shortComponent(context), ignoreCase = true)
                } -> cur
                else -> "$cur:$flat"
            }
            val ok1 = Settings.Secure.putString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, next)
            val ok2 = Settings.Secure.putInt(cr, Settings.Secure.ACCESSIBILITY_ENABLED, 1)
            ok1 && ok2
        }.getOrDefault(false)
    }

    fun openAccessibilitySettings(context: Context) {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    fun ensureProtectionUi(context: Context, prefs: DevicePrefs) {
        tryAutoEnable(context)
        if (prefs.isLocked && !isWorking(context)) {
            if (!prefs.isServiceControlUnlocked) {
                prefs.unlockServiceControlFor(2 * 60 * 1000L)
            }
        }
    }
}
