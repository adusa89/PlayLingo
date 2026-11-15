package cz.testpreklad

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import cz.testpreklad.core.Ps5

/** Jak je toto zařízení připojené k domácí síti (kabel, Wi-Fi 5 GHz, Wi-Fi 2,4 GHz). */
object NetInfo {
    fun kind(ctx: Context): Ps5.Net = try {
        val cm = ctx.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork)
        when {
            caps == null -> Ps5.Net.UNKNOWN
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> Ps5.Net.CABLE
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> {
                val wm = ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                @Suppress("DEPRECATION")
                val mhz = wm.connectionInfo?.frequency ?: 0
                when {
                    mhz in 2400..2500 -> Ps5.Net.WIFI_24
                    mhz >= 4900 -> Ps5.Net.WIFI_5
                    else -> Ps5.Net.WIFI_5
                }
            }
            else -> Ps5.Net.UNKNOWN
        }
    } catch (_: Throwable) { Ps5.Net.UNKNOWN }
}
