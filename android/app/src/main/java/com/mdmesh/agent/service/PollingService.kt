package com.mdmesh.agent.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.mdmesh.agent.R
import com.mdmesh.agent.data.AppScanner
import com.mdmesh.agent.data.DevicePrefs
import com.mdmesh.agent.data.RetrofitClient
import com.mdmesh.agent.data.SyncRequest
import com.mdmesh.agent.data.UsageRequest
import com.mdmesh.agent.policy.PolicyPackages
import com.mdmesh.agent.policy.ProtectionPin
import com.mdmesh.agent.policy.RestrictionApplier
import com.mdmesh.agent.policy.UninstallGuard
import com.mdmesh.agent.ui.MainActivity
import com.mdmesh.agent.util.DeviceInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PollingService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var prefs: DevicePrefs
    private val ticker = object : Runnable {
        override fun run() {
            syncNow()
            handler.postDelayed(this, INTERVAL_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        prefs = DevicePrefs(this)
        startForeground(NOTIFICATION_ID, notification(getString(R.string.notification_text)))
        handler.post(ticker)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_SYNC_NOW) syncNow()
        return START_STICKY
    }

    private fun syncNow() {
        if (!::prefs.isInitialized) prefs = DevicePrefs(this)
        // During uninstall window: stop syncing so we don't re-lock or relaunch
        if (prefs.isUninstallUnlocked) {
            handler.removeCallbacks(ticker)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }
        scope.launch {
            try {
                val api = RetrofitClient.create(prefs.serverUrl)
                val request = SyncRequest(
                    unique_id = prefs.uniqueId,
                    device_name = DeviceInfo.name(),
                    device_model = DeviceInfo.model(),
                    ip_address = DeviceInfo.ipAddress(this@PollingService),
                    installed_apps = AppScanner.installedApps(this@PollingService)
                )
                val response = api.syncDevice(request)
                val restriction = response.restriction
                prefs.lastSyncText = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())

                // If user unlocked uninstall while we were syncing, abort
                if (prefs.isUninstallUnlocked) {
                    handler.removeCallbacks(ticker)
                    stopSelf()
                    return@launch
                }

                val remotePin = response.protection_pin?.trim().orEmpty()
                if (remotePin.matches(Regex("^\\d{4,8}$"))) {
                    val hash = ProtectionPin.hash(remotePin)
                    if (prefs.protectionPinHash != hash) {
                        prefs.protectionPinHash = hash
                        // Do NOT call lockUninstall() here — that canceled uninstall mid-way
                        UninstallGuard.applyBlockedState(this@PollingService)
                    }
                } else if (remotePin.isEmpty() && prefs.hasProtectionPin) {
                    prefs.protectionPinHash = null
                    UninstallGuard.applyBlockedState(this@PollingService)
                }

                if (restriction != null) {
                    RestrictionApplier(this@PollingService).apply(restriction)
                    val locked = restriction.is_locked == 1
                    val count = restriction.allowed_apps.size
                    val text = if (locked) {
                        getString(R.string.restricted_notification, if (count > 0) count else 1)
                    } else {
                        getString(R.string.notification_text)
                    }
                    startForeground(NOTIFICATION_ID, notification(text))
                }

                PolicyPackages.flushOpenSession(prefs)
                val pending = prefs.takeUsageSessions()
                if (pending.isNotEmpty()) {
                    try {
                        val usageRes = api.uploadUsage(
                            UsageRequest(unique_id = prefs.uniqueId, sessions = pending)
                        )
                        if (!usageRes.success) {
                            prefs.requeueUsageSessions(pending)
                        }
                    } catch (_: Exception) {
                        prefs.requeueUsageSessions(pending)
                    }
                }
            } catch (_: Exception) {
                // Retry on the next tick.
            }
        }
    }

    private fun notification(text: String): Notification {
        val open = Intent(this, MainActivity::class.java)
        val pending = PendingIntent.getActivity(
            this,
            0,
            open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setOngoing(true)
            .setContentIntent(pending)
            .build()
    }

    override fun onDestroy() {
        handler.removeCallbacks(ticker)
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val CHANNEL_ID = "mdmesh_sync"
        const val ACTION_SYNC_NOW = "com.mdmesh.agent.SYNC_NOW"
        private const val NOTIFICATION_ID = 42
        private const val INTERVAL_MS = 5000L
    }
}
