package com.mdmesh.agent.policy

import android.app.admin.DevicePolicyManager
import android.app.role.RoleManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Build
import android.provider.Settings
import com.mdmesh.agent.data.DevicePrefs
import com.mdmesh.agent.ui.KioskHomeActivity

/**
 * While Restricted, Pandiyan Agency is the Home launcher (phones + tablets).
 * Shows only admin-allowlisted apps + allowed websites.
 * Device Owner sets a persistent preferred HOME so no manual chooser is required.
 */
object HomeEnforcer {
    fun component(context: Context) = ComponentName(context, KioskHomeActivity::class.java)

    fun enableHomeComponent(context: Context) {
        context.packageManager.setComponentEnabledSetting(
            component(context),
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            PackageManager.DONT_KILL_APP
        )
    }

    fun disableHomeComponent(context: Context) {
        context.packageManager.setComponentEnabledSetting(
            component(context),
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP
        )
    }

    /** Restricted → Pandiyan Home ON (+ DO preferred). Unlocked → clear preferred + disable. */
    fun applyForLockState(context: Context, locked: Boolean) {
        if (locked) {
            val prefs = DevicePrefs(context)
            // PIN exit / service-control window: do not force Pandiyan Home back
            if (prefs.isServiceControlUnlocked) return
            enableHomeComponent(context)
            setPreferredHome(context)
        } else {
            clearPreferredHome(context)
            disableHomeComponent(context)
        }
    }

    fun needsPandiyanHome(@Suppress("UNUSED_PARAMETER") context: Context): Boolean = true

    fun restoreStockHomeChooser(context: Context) {
        clearPreferredHome(context)
        disableHomeComponent(context)
        openHomeSettings(context)
    }

    /** Device Owner: force Pandiyan as the only HOME handler. */
    fun setPreferredHome(context: Context) {
        if (!DeviceOwnerHelper.isDeviceOwner(context)) return
        enableHomeComponent(context)
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val admin = DeviceOwnerHelper.adminComponent(context)
        val filter = IntentFilter(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            addCategory(Intent.CATEGORY_DEFAULT)
        }
        runCatching {
            dpm.addPersistentPreferredActivity(admin, filter, component(context))
        }
    }

    /** Clear DO preferred Home so the stock launcher chooser can work after PIN exit. */
    fun clearPreferredHome(context: Context) {
        if (!DeviceOwnerHelper.isDeviceOwner(context)) return
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val admin = DeviceOwnerHelper.adminComponent(context)
        runCatching {
            dpm.clearPackagePersistentPreferredActivities(admin, context.packageName)
        }
    }

    /** Send user to Pandiyan Home launcher. */
    fun goHome(context: Context) {
        enableHomeComponent(context)
        setPreferredHome(context)
        val home = Intent(context, KioskHomeActivity::class.java)
            .addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            )
        runCatching { context.startActivity(home) }
    }

    fun isOurLauncherDefault(context: Context): Boolean {
        if (DeviceOwnerHelper.isDeviceOwner(context)) {
            // Preferred activity may not always show in resolveActivity; treat DO preferred as ours when locked component is enabled
            val state = context.packageManager.getComponentEnabledSetting(component(context))
            if (state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED ||
                state == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
            ) {
                val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                val resolve: ResolveInfo? =
                    context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
                val pkg = resolve?.activityInfo?.packageName
                if (pkg == context.packageName) return true
            }
        }
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val resolve: ResolveInfo? =
            context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        val pkg = resolve?.activityInfo?.packageName ?: return false
        return pkg == context.packageName
    }

    fun openHomeSettings(context: Context) {
        val settings = Intent(Settings.ACTION_HOME_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(settings) }
    }

    fun openHomeChooser(context: Context) {
        // After PIN exit we disable our Home; only re-enable if still locking
        if (runCatching {
                context.startActivity(
                    Intent(Settings.ACTION_HOME_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
                true
            }.getOrDefault(false)
        ) {
            return
        }
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        home.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching {
            context.startActivity(Intent.createChooser(home, "Select Home app → Always"))
        }
    }

    fun requestHomeRole(activity: android.app.Activity): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        val rm = activity.getSystemService(RoleManager::class.java) ?: return false
        if (!rm.isRoleAvailable(RoleManager.ROLE_HOME)) return false
        if (rm.isRoleHeld(RoleManager.ROLE_HOME)) return true
        enableHomeComponent(activity)
        runCatching {
            activity.startActivityForResult(rm.createRequestRoleIntent(RoleManager.ROLE_HOME), REQ_HOME_ROLE)
            return true
        }
        return false
    }

    const val REQ_HOME_ROLE = 9911
}
