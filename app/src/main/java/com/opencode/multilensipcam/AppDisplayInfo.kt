package com.opencode.multilensipcam

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
        return LanNetworkInfo.ipv4FromInterfaces()
    }
}
