package com.mdmesh.agent.policy

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast

/**
 * Opens OEM screens so users can lock Pandiyan in Recents / allow autostart.
 * Production path without USB Device Owner wipe.
 */
object OemProtectHelper {
    fun openRecentsLockHelp(context: Context) {
        Toast.makeText(
            context,
            "Recents → find Pandiyan Agency → pull down → Lock (padlock). Clear All will keep it.",
            Toast.LENGTH_LONG
        ).show()
        // Try MIUI security / autostart pages; fall back to app details
        val tries = listOf(
            Intent("miui.intent.action.OP_AUTO_START").putExtra("packageName", context.packageName),
            Intent("miui.intent.action.POWER_HIDE_MODE_APP_LIST"),
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).setData(
                Uri.parse("package:${context.packageName}")
            ),
            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        )
        for (intent in tries) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (runCatching { context.startActivity(intent); true }.getOrDefault(false)) return
        }
    }

    fun requestIgnoreBattery(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
            .setData(Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }
}
