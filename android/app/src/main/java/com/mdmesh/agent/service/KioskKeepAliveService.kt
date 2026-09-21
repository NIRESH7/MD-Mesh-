package com.mdmesh.agent.service

import android.app.AlarmManager
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.mdmesh.agent.R
import com.mdmesh.agent.data.CrossProcessLock
import com.mdmesh.agent.data.DevicePrefs
import com.mdmesh.agent.policy.RestrictionApplier
import com.mdmesh.agent.policy.StrictLockHelper
import com.mdmesh.agent.ui.MainActivity

/**
 * Foreground keepalive while Restricted.
 * Does not force the Strict Lock setup screen — Pandiyan Home keeps a quiet toggle row.
 */
class KioskKeepAliveService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private var lastConnectToastMs = 0L
    private val watchdog = object : Runnable {
        override fun run() {
            enforceWhileLocked()
            handler.postDelayed(this, 2000L)
        }
    }

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIFICATION_ID, buildNotification())
        handler.removeCallbacks(watchdog)
        handler.post(watchdog)
        LockWatchdog.schedule(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification())
        handler.removeCallbacks(watchdog)
        handler.post(watchdog)
        LockWatchdog.schedule(this)
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        runCatching { StrictLockHelper.tryAutoEnable(applicationContext) }
        runCatching { start(applicationContext) }
        LockWatchdog.schedule(applicationContext)
        runCatching {
            val sync = Intent(applicationContext, PollingService::class.java)
                .setAction(PollingService.ACTION_SYNC_NOW)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                applicationContext.startForegroundService(sync)
            } else {
                applicationContext.startService(sync)
            }
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        handler.removeCallbacks(watchdog)
        LockWatchdog.schedule(applicationContext)
        runCatching { start(applicationContext) }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun enforceWhileLocked() {
        val prefs = DevicePrefs(this)
        if (!prefs.setupComplete || prefs.servicesStoppedByPin) return
        val snap = CrossProcessLock.read(this)
        if (!snap.locked && !prefs.isLocked) return

        runCatching { StrictLockHelper.tryAutoEnable(this) }
        LockWatchdog.schedule(this)

        if (prefs.isLocked && prefs.allowedPackages.isNotEmpty()) {
            runCatching { RestrictionApplier(this).reapplyIfLocked() }
        }

        val listed = StrictLockHelper.isListedInSettings(this)
        val working = StrictLockHelper.isWorking(this)
        val allowed = if (snap.allowed.isNotEmpty()) snap.allowed else prefs.allowedPackages

        // Never interrupt an allowlisted app (BNCI / JCI) — that was blocking work
        if (isAllowedAppOnTop(allowed)) return

        // Quiet reminder only — never open RequireStrictLockActivity
        if ((!listed || !working) && !isAllowedAppOnTop(allowed)) {
            val now = System.currentTimeMillis()
            if (now - lastConnectToastMs > 60_000L) {
                lastConnectToastMs = now
                handler.post {
                    Toast.makeText(
                        applicationContext,
                        if (!listed) {
                            "Strict Lock OFF — enable on Pandiyan Home if other apps stay open"
                        } else {
                            "Strict Lock connecting… If stuck: Accessibility → Pandiyan → Off then On"
                        },
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun isAllowedAppOnTop(allowed: Set<String>): Boolean {
        if (allowed.isEmpty()) return false
        val top = runCatching {
            val am = getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            @Suppress("DEPRECATION")
            am.getRunningTasks(1).firstOrNull()?.topActivity?.packageName
        }.getOrNull() ?: return false
        return top in allowed
    }

    private fun buildNotification(): Notification {
        val snap = CrossProcessLock.read(this)
        val text = if (snap.locked) {
            getString(R.string.restricted_notification, snap.allowed.size.coerceAtLeast(1))
        } else {
            getString(R.string.notification_text)
        }
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setOngoing(true)
            .setContentIntent(open)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    companion object {
        const val CHANNEL_ID = "mdmesh_kiosk"
        private const val NOTIFICATION_ID = 43

        fun start(context: Context) {
            val i = Intent(context, KioskKeepAliveService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(i)
            } else {
                context.startService(i)
            }
        }
    }
}

/** Restarts keepalive after Xiaomi Clear Recents / process death. */
object LockWatchdog {
    private const val REQ = 4501
    private const val INTERVAL_MS = 20_000L

    fun schedule(context: Context) {
        val prefs = DevicePrefs(context)
        if (!prefs.setupComplete || prefs.servicesStoppedByPin) return
        if (!prefs.isLocked && !CrossProcessLock.read(context).locked) return

        val app = context.applicationContext
        val am = app.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(app, LockWatchdogReceiver::class.java)
        val pi = PendingIntent.getBroadcast(
            app,
            REQ,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val at = SystemClock.elapsedRealtime() + INTERVAL_MS
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                am.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, at, pi)
            } else {
                am.setExact(AlarmManager.ELAPSED_REALTIME_WAKEUP, at, pi)
            }
        }.onFailure {
            am.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, at, pi)
        }
    }
}
