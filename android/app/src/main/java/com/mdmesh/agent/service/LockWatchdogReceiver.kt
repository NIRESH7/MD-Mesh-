package com.mdmesh.agent.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.mdmesh.agent.data.CrossProcessLock
import com.mdmesh.agent.data.DevicePrefs
import com.mdmesh.agent.policy.StrictLockHelper

/**
 * Alarm-driven restart after Clear Recents kills the process.
 */
class LockWatchdogReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val prefs = DevicePrefs(context)
        if (!prefs.setupComplete || prefs.servicesStoppedByPin) return
        val locked = prefs.isLocked || CrossProcessLock.read(context).locked
        if (!locked) return

        runCatching { StrictLockHelper.tryAutoEnable(context) }
        runCatching { KioskKeepAliveService.start(context) }
        runCatching { RecentsStickyService.start(context) }
        runCatching {
            val sync = Intent(context, PollingService::class.java)
                .setAction(PollingService.ACTION_SYNC_NOW)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(sync)
            } else {
                context.startService(sync)
            }
        }
        LockWatchdog.schedule(context)
    }
}
