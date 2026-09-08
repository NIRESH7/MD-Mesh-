package com.mdmesh.agent.util

import android.content.Context
import android.net.wifi.WifiManager
import android.os.Build
import java.net.NetworkInterface
import java.util.Collections
import java.util.Locale

object DeviceInfo {
    fun model(): String {
        val manufacturer = Build.MANUFACTURER.replaceFirstChar {
            if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString()
        }
        return "$manufacturer ${Build.MODEL}".trim()
    }

    fun name(): String = Build.MODEL

    fun ipAddress(context: Context): String {
        val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        @Suppress("DEPRECATION")
        val ip = wifi.connectionInfo?.ipAddress ?: 0
        if (ip != 0) {
            return String.format(
                Locale.US,
                "%d.%d.%d.%d",
                ip and 0xff,
                ip shr 8 and 0xff,
                ip shr 16 and 0xff,
                ip shr 24 and 0xff
            )
        }
        val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
        for (intf in interfaces) {
            val addrs = Collections.list(intf.inetAddresses)
            for (addr in addrs) {
                if (!addr.isLoopbackAddress && addr.hostAddress?.contains(":") == false) {
                    return addr.hostAddress ?: ""
                }
            }
        }
        return ""
    }
}
