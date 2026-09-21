package com.mdmesh.agent.policy

import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.mdmesh.agent.data.DevicePrefs
import com.mdmesh.agent.ui.PinChallengeActivity

class MeshDeviceAdminReceiver : DeviceAdminReceiver() {
    override fun onEnabled(context: Context, intent: Intent) {
        Toast.makeText(context, "Pandiyan Agency device admin enabled", Toast.LENGTH_SHORT).show()
        UninstallGuard.applyBlockedState(context)
    }

    override fun onDisabled(context: Context, intent: Intent) {
        Toast.makeText(context, "Pandiyan Agency device admin disabled", Toast.LENGTH_SHORT).show()
    }

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        val prefs = DevicePrefs(context)
        if (prefs.hasProtectionPin && !prefs.isUninstallUnlocked) {
            runCatching {
                context.startActivity(
                    Intent(context, PinChallengeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
            return "Enter the admin panel PIN in Pandiyan Agency before disabling Device Admin / uninstalling."
        }
        return "Disabling admin allows uninstalling Pandiyan Agency and removes app restrictions."
    }
}

/**
 * Blocks uninstall when Device Owner is available; otherwise Soft protection
 * uses Accessibility + Device Admin deactivate warning + PIN gate.
 */
object UninstallGuard {
    fun applyBlockedState(context: Context) {
        val prefs = DevicePrefs(context)
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val admin = ComponentName(context, MeshDeviceAdminReceiver::class.java)
        if (!dpm.isDeviceOwnerApp(context.packageName)) return
        val block = prefs.hasProtectionPin && !prefs.isUninstallUnlocked
        runCatching { dpm.setUninstallBlocked(admin, context.packageName, block) }
    }

    fun isUninstallRelated(pkg: String, className: String?): Boolean {
        val cls = className.orEmpty()
        if (UNINSTALL_PACKAGES.any { pkg == it || pkg.startsWith(it) }) return true
        if (SETTINGS_PACKAGES.any { pkg == it || pkg.startsWith(it) }) {
            if (cls.contains("InstalledAppDetails", ignoreCase = true)) return true
            if (cls.contains("ApplicationDetails", ignoreCase = true)) return true
            if (cls.contains("AppInfo", ignoreCase = true)) return true
            if (cls.contains("DeviceAdmin", ignoreCase = true)) return true
        }
        if (cls.contains("Uninstaller", ignoreCase = true)) return true
        if (cls.contains("Uninstall", ignoreCase = true)) return true
        if (cls.contains("DeviceAdminAdd", ignoreCase = true)) return true
        return false
    }

    fun shouldBlockUninstallUi(context: Context, pkg: String, className: String?): Boolean {
        val prefs = DevicePrefs(context)
        if (!prefs.hasProtectionPin) return false
        if (prefs.isUninstallUnlocked) return false
        if (pkg == context.packageName) return false
        return isUninstallRelated(pkg, className)
    }

    fun textLooksLikeUninstall(hay: String, ourPackage: String): Boolean {
        val h = hay.lowercase()
        val ours = ourPackage.lowercase()
        if (h.contains("uninstall") && (h.contains("md mesh") || h.contains("mdmesh") || h.contains(ours))) return true
        if (h.contains("deactivate") && h.contains("device admin")) return true
        if (h.contains("deactivate this device admin")) return true
        if (h.contains("remove device admin")) return true
        if (h.contains("uninstall") && h.contains("com.mdmesh")) return true
        return false
    }

    private val UNINSTALL_PACKAGES = listOf(
        "com.android.packageinstaller",
        "com.google.android.packageinstaller",
        "com.miui.packageinstaller",
        "com.samsung.android.packageinstaller"
    )

    private val SETTINGS_PACKAGES = listOf(
        "com.android.settings",
        "com.miui.securitycenter"
    )
}
