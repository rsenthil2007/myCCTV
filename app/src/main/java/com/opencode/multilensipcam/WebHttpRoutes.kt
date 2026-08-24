package com.opencode.multilensipcam

enum class WebHttpRoute {
    INDEX,
    DASHBOARD_BODY_ASSET,
    DASHBOARD_CSS_ASSET,
    DASHBOARD_JS_ASSET,
    SNAPSHOT,
    MJPEG,
    H264,
    AUDIO,
    STATE,
    DEBUG_STREAM,
    DEBUG_DIAGNOSTICS,
    DEBUG_CAMERAS,
    CAMERA_SCAN_CACHE,
    CAMERA_SCAN_CACHE_DELETE,
    CAMERA_SCAN,
    CAMERA_OPEN,
    CAMERA_OPEN_MATRIX,
    CONTROL,
    NOT_FOUND
}

object WebHttpRoutes {
    private val frozenSnapshotPattern = Regex("^/snapshots/(\\d{13}-\\d+)\\.jpg$")
    private val dashboardBodyAssetPattern = Regex("^/assets/dashboard\\.body/(\\d+)$")
    private val dashboardCssAssetPattern = Regex("^/assets/dashboard\\.css/(\\d+)$")
    private val dashboardJsAssetPattern = Regex("^/assets/dashboard\\.js/(\\d+)$")

    fun resolve(target: String): WebHttpRoute {
        return when {
            target == "/" || target == "/index.html" -> WebHttpRoute.INDEX
            dashboardBodyAssetPattern.matches(target) -> WebHttpRoute.DASHBOARD_BODY_ASSET
            dashboardCssAssetPattern.matches(target) -> WebHttpRoute.DASHBOARD_CSS_ASSET
            dashboardJsAssetPattern.matches(target) -> WebHttpRoute.DASHBOARD_JS_ASSET
            target == "/snapshot.jpg" || target == "/shot.jpg" -> WebHttpRoute.SNAPSHOT
            target == "/mjpeg" || target == "/video" -> WebHttpRoute.MJPEG
            target == "/h264" || target == "/video.h264" -> WebHttpRoute.H264
            target == "/audio.aac" -> WebHttpRoute.AUDIO
            target == "/api/state" -> WebHttpRoute.STATE
            target == "/api/debug/stream" -> WebHttpRoute.DEBUG_STREAM
            target == "/api/debug/diagnostics" -> WebHttpRoute.DEBUG_DIAGNOSTICS
            target == "/api/debug/cameras" || target == "/api/debug/camera-report" -> WebHttpRoute.DEBUG_CAMERAS
            target == "/api/debug/camera-scan-cache" -> WebHttpRoute.CAMERA_SCAN_CACHE
            target == "/api/debug/camera-scan-cache/delete" -> WebHttpRoute.CAMERA_SCAN_CACHE_DELETE
            target == "/api/debug/camera-scan" -> WebHttpRoute.CAMERA_SCAN
            target == "/api/debug/camera-open" -> WebHttpRoute.CAMERA_OPEN
            target == "/api/debug/camera-open-matrix" -> WebHttpRoute.CAMERA_OPEN_MATRIX
            target == "/api/control" -> WebHttpRoute.CONTROL
            else -> WebHttpRoute.NOT_FOUND
        }
    }

    fun frozenSnapshotId(target: String): String? {
        return frozenSnapshotPattern.matchEntire(target)?.groupValues?.getOrNull(1)
    }

    fun dashboardBodyChunkIndex(target: String): Int? {
        return dashboardBodyAssetPattern.matchEntire(target)?.groupValues?.getOrNull(1)?.toIntOrNull()
    }

    fun dashboardCssChunkIndex(target: String): Int? {
        return dashboardCssAssetPattern.matchEntire(target)?.groupValues?.getOrNull(1)?.toIntOrNull()
    }

    fun dashboardJsChunkIndex(target: String): Int? {
        return dashboardJsAssetPattern.matchEntire(target)?.groupValues?.getOrNull(1)?.toIntOrNull()
    }
}
