package com.mdmesh.agent.policy

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings

object DeviceOwnerHelper {
    fun adminComponent(context: Context): ComponentName =
        ComponentName(context, MeshDeviceAdminReceiver::class.java)

    fun isDeviceOwner(context: Context): Boolean {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        return dpm.isDeviceOwnerApp(context.packageName)
    }

    fun isAdminActive(context: Context): Boolean {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        return dpm.isAdminActive(adminComponent(context))
    }

    fun statusLine(context: Context): String {
        return when {
            isDeviceOwner(context) -> "Device Owner: ON — Clear Recents keeps only allowed apps"
            isAdminActive(context) -> "Device Owner: OFF (admin only) — Clear Recents can unlock apps"
            else -> "Device Owner: OFF — set Device Owner for Clear Recents protection"
        }
    }

    fun setPreferredHome(context: Context) = HomeEnforcer.setPreferredHome(context)

    fun clearPreferredHome(context: Context) = HomeEnforcer.clearPreferredHome(context)

    fun openDevOptionsHint(context: Context) {
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }
}
