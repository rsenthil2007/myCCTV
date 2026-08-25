package com.opencode.multilensipcam

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Collections

data class LanNetworkSnapshot(
    val wifiConnected: Boolean,
    val ssid: String?,
    val ipv4: String?,
    val httpPort: Int
) {
    val dashboardUrl: String
        get() = ipv4?.let { "http://$it:$httpPort/" } ?: "http://phone-ip:$httpPort/"

    val wifiStatusLabel: String
        get() {
            val networkName = ssid?.takeIf { it.isNotBlank() }
            return when {
                wifiConnected && networkName != null -> "Wi-Fi connected ($networkName)"
                wifiConnected -> "Wi-Fi connected"
                else -> "Wi-Fi not connected"
            }
        }

    fun displaySummary(rtspLine: String): String {
        return buildString {
            append(wifiStatusLabel)
            append('\n')
            append("LAN IP  ${ipv4 ?: "unavailable"}")
            append('\n')
            append("Dashboard  $dashboardUrl")
            append('\n')
            append(rtspLine)
        }
    }
}

object LanNetworkInfo {
    fun snapshot(context: Context, httpPort: Int): LanNetworkSnapshot {
        val appContext = context.applicationContext
        val connectivity = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val wifiNetwork = wifiNetwork(connectivity)
        return LanNetworkSnapshot(
            wifiConnected = wifiNetwork != null,
            ssid = currentSsid(appContext),
            ipv4 = ipv4FromNetwork(connectivity, wifiNetwork) ?: ipv4FromInterfaces(),
            httpPort = httpPort
        )
    }

    fun ipv4FromInterfaces(): String? {
        val interfaces = runCatching {
            Collections.list(NetworkInterface.getNetworkInterfaces()).filter { iface ->
                runCatching { iface.isUp && !iface.isLoopback }.getOrDefault(false)
            }
        }.getOrDefault(emptyList())

        val preferred = interfaces.filter { iface ->
            val name = iface.name.lowercase()
            name.startsWith("wlan") || name.startsWith("ap") || name.contains("wifi")
        }
        val searchOrder = preferred + interfaces.filter { it !in preferred }
        return searchOrder.asSequence()
            .flatMap { Collections.list(it.inetAddresses).asSequence() }
            .filterIsInstance<Inet4Address>()
            .firstOrNull { !it.isLoopbackAddress && !it.isLinkLocalAddress }
            ?.hostAddress
    }

    private fun wifiNetwork(connectivity: ConnectivityManager): Network? {
        connectivity.activeNetwork?.let { network ->
            val capabilities = connectivity.getNetworkCapabilities(network)
            if (capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true) {
                return network
            }
        }
        return connectivity.allNetworks.firstOrNull { network ->
            connectivity.getNetworkCapabilities(network)
                ?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        }
    }

    private fun ipv4FromNetwork(connectivity: ConnectivityManager, network: Network?): String? {
        val addresses = connectivity.getLinkProperties(network)?.linkAddresses.orEmpty()
        return addresses.map { it.address }
            .filterIsInstance<Inet4Address>()
            .firstOrNull { !it.isLoopbackAddress && !it.isLinkLocalAddress }
            ?.hostAddress
    }

    private fun currentSsid(context: Context): String? {
        return runCatching {
            val wifi = context.getSystemService(Context.WIFI_SERVICE) as WifiManager
            wifi.connectionInfo?.ssid
                ?.removeSurrounding("\"")
                ?.takeIf { it.isNotBlank() && it != "<unknown ssid>" && it != "0x" }
        }.getOrNull()
    }
}
