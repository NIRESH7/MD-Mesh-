package com.mdmesh.agent.policy

import android.content.Context
import com.mdmesh.agent.data.DevicePrefs
import com.mdmesh.agent.data.ReleaseByPinRequest
import com.mdmesh.agent.data.RetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Clears app restrictions locally and asks the server to mark the device Active,
 * so uninstalling MD Mesh leaves the phone fully usable.
 */
object RestrictionClearer {
    fun clearLocal(context: Context, showToast: Boolean = true) {
        RestrictionApplier(context).clearAllRestrictions(showToast)
        val prefs = DevicePrefs(context)
        prefs.isLocked = false
        prefs.allowedPackages = emptySet()
        prefs.allowedUrls = emptyList()
        prefs.blockWebMedia = true
        prefs.lastLockedApp = null
        prefs.lastRestriction = "0:"
        prefs.unlockUninstallFor(30 * 60 * 1000L)
        UninstallGuard.applyBlockedState(context)
    }

    suspend fun clearLocalAndServer(context: Context, pin: String): Boolean {
        clearLocal(context, showToast = false)
        val prefs = DevicePrefs(context)
        return withContext(Dispatchers.IO) {
            runCatching {
                val api = RetrofitClient.create(prefs.serverUrl)
                val res = api.releaseByPin(
                    ReleaseByPinRequest(unique_id = prefs.uniqueId, pin = pin)
                )
                res.success
            }.getOrDefault(false)
        }
    }
}
