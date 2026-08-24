package com.opencode.multilensipcam

import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Collections

object AppDisplayInfo {
    fun versionLabel(): String {
        return "v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"
    }

    fun dashboardUrl(port: Int): String {
        val ip = localIpv4Address() ?: "phone-ip"
        return "http://$ip:$port/"
    }

    fun dashboardSummary(port: Int): String {
        return "Dashboard  ${dashboardUrl(port)}"
    }

    fun rtspUrl(port: Int): String {
        val ip = localIpv4Address() ?: "phone-ip"
        return "rtsp://$ip:$port/live"
    }

    fun localIpv4Address(): String? {
        return Collections.list(NetworkInterface.getNetworkInterfaces())
            .flatMap { Collections.list(it.inetAddresses) }
            .firstOrNull { address -> !address.isLoopbackAddress && address is Inet4Address }
            ?.hostAddress
    }
}
