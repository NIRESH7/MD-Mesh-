package com.mdmesh.agent.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.mdmesh.agent.R
import com.mdmesh.agent.data.DevicePrefs
import com.mdmesh.agent.policy.StrictLockHelper
import com.mdmesh.agent.ui.MainActivity

/**
 * Sticky foreground helper. Does NOT put Pandiyan back into Recents
 * (that made Xiaomi Clear All force-stop the app and kill Strict Lock).
 */
class RecentsStickyService : Service() {
    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIFICATION_ID, buildNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification())
        runCatching { StrictLockHelper.tryAutoEnable(this) }
        runCatching { KioskKeepAliveService.start(this) }
        val sync = Intent(this, PollingService::class.java).setAction(PollingService.ACTION_SYNC_NOW)
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(sync)
            else startService(sync)
        }
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        keepAliveAfterClearAll(this, askPinToClose = false)
        super.onTaskRemoved(rootIntent)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(): Notification {
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(getString(R.string.app_name))
            .setContentText("Restriction guard running")
            .setOngoing(true)
            .setContentIntent(open)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    companion object {
        const val CHANNEL_ID = "mdmesh_recents"
        const val ACTION_REATTACH = "com.mdmesh.agent.REATTACH_RECENTS"
        const val ACTION_REATTACH_PIN = "com.mdmesh.agent.REATTACH_RECENTS_PIN"
        const val EXTRA_REATTACHED = "reattached_from_clear"
        const val EXTRA_ASK_PIN_TO_CLOSE = "ask_pin_to_close"
        private const val NOTIFICATION_ID = 44

        fun start(context: Context) {
            val i = Intent(context, RecentsStickyService::class.java)
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(i)
                } else {
                    context.startService(i)
                }
            }
        }

        fun keepAliveAfterClearAll(context: Context, askPinToClose: Boolean = false) {
            val app = context.applicationContext
            start(app)
            runCatching { KioskKeepAliveService.start(app) }
            runCatching { StrictLockHelper.tryAutoEnable(app) }
            val prefs = DevicePrefs(app)
            runCatching { StrictLockHelper.ensureProtectionUi(app, prefs) }
            val sync = Intent(app, PollingService::class.java).setAction(PollingService.ACTION_SYNC_NOW)
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) app.startForegroundService(sync)
                else app.startService(sync)
            }
            // Intentionally do NOT start MainActivity into Recents.
        }
    }
}
