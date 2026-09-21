package com.mdmesh.agent

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.os.Build
import com.mdmesh.agent.data.DevicePrefs
import com.mdmesh.agent.policy.StrictLockHelper
import com.mdmesh.agent.service.KioskKeepAliveService
import com.mdmesh.agent.service.LockWatchdog
import com.mdmesh.agent.service.PollingService
import com.mdmesh.agent.service.RecentsStickyService

class MdMeshApp : Application() {
    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel(
                    PollingService.CHANNEL_ID,
                    getString(R.string.notification_channel),
                    NotificationManager.IMPORTANCE_LOW
                )
            )
            nm.createNotificationChannel(
                NotificationChannel(
                    KioskKeepAliveService.CHANNEL_ID,
                    "Pandiyan Agency lock",
                    NotificationManager.IMPORTANCE_LOW
                )
            )
            nm.createNotificationChannel(
                NotificationChannel(
                    RecentsStickyService.CHANNEL_ID,
                    "Pandiyan Recents guard",
                    NotificationManager.IMPORTANCE_LOW
                )
            )
        }
        val prefs = DevicePrefs(this)
        if (prefs.setupComplete && !prefs.servicesStoppedByPin && !prefs.isUninstallUnlocked) {
            // After Clear Recents / force-stop: try to turn Soft Lock back on (needs WRITE_SECURE_SETTINGS)
            runCatching { StrictLockHelper.tryAutoEnable(this) }
            runCatching { KioskKeepAliveService.start(this) }
            runCatching { RecentsStickyService.start(this) }
            runCatching { LockWatchdog.schedule(this) }
            val sync = Intent(this, PollingService::class.java)
                .setAction(PollingService.ACTION_SYNC_NOW)
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(sync)
                else startService(sync)
            }
            // Do not force RequireStrictLockActivity — Pandiyan Home shows a quiet Strict Lock row.
        }
    }
}
