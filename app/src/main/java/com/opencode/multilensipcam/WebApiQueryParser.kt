package com.opencode.multilensipcam

object WebApiQueryParser {
    fun controlCommand(query: Map<String, String>): WebControlCommand {
        return WebControlCommand(
            cameraKey = query["camera"],
            resolution = query["resolution"],
            manualWidth = query["manualWidth"]?.toIntOrNull(),
            manualHeight = query["manualHeight"]?.toIntOrNull(),
            fps = query["fps"]?.toIntOrNull(),
            unlimitedFps = parseBoolean(query["unlimitedFps"]),
            quality = query["quality"]?.toIntOrNull(),
            zoom = query["zoom"]?.toFloatOrNull(),
            focusMode = query["focus"],
            focusDistance = query["focusDistance"]?.toFloatOrNull(),
            exposureCompensation = query["exposure"]?.toIntOrNull(),
            torchEnabled = parseBoolean(query["torch"]),
            videoOverlayEnabled = parseBoolean(query["videoOverlay"]),
            videoOverlaySize = query["videoOverlaySize"]?.toIntOrNull(),
            applyRecommended = parseBoolean(query["applyRecommended"]) == true,
            preset = query["preset"],
            previewRotation = query["previewRotation"]?.toIntOrNull(),
            previewScale = query["previewScale"]?.toFloatOrNull(),
            previewStretchX = query["previewStretchX"]?.toFloatOrNull(),
            previewStretchY = query["previewStretchY"]?.toFloatOrNull(),
            jpegPipeline = query["jpegPipeline"] ?: query["jpegPath"],
            mjpegFullSize = parseBoolean(query["mjpegFullSize"]),
            audioEnabled = parseBoolean(query["audio"] ?: query["audioEnabled"]),
            stopStreaming = parseBoolean(query["stopStreaming"]) == true,
            streaming = parseBoolean(query["streaming"])
        )
    }

    fun cameraOpenProbeRequest(query: Map<String, String>): CameraOpenProbeRequest {
        return CameraOpenProbeRequest(
            cameraId = query["camera"] ?: query["cameraId"] ?: "0",
            mode = CameraOpenProbeMode.from(query["mode"]),
            width = query["width"]?.toIntOrNull()?.coerceIn(176, 4096) ?: 640,
            height = query["height"]?.toIntOrNull()?.coerceIn(144, 3072) ?: 480,
            fps = query["fps"]?.toIntOrNull()?.coerceIn(1, 120) ?: 30,
            durationMs = query["durationMs"]?.toIntOrNull()?.coerceIn(250, 5_000) ?: 1_200,
            openTimeoutMs = query["openTimeoutMs"]?.toIntOrNull()?.coerceIn(500, 8_000) ?: 2_500,
            totalTimeoutMs = query["totalTimeoutMs"]?.toIntOrNull()?.coerceIn(1_000, 12_000) ?: 5_000
        )
    }

    private fun parseBoolean(value: String?): Boolean? {
        return when (value?.lowercase()) {
            "1", "true", "yes", "on" -> true
            "0", "false", "no", "off" -> false
            else -> null
        }
    }
}
