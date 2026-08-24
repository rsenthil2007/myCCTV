package com.opencode.multilensipcam

data class CameraStreamDebugState(
    val cameraId: String?,
    val physicalCameraId: String?,
    val outputSize: String?,
    val mjpegSize: String?,
    val targetFps: Int?,
    val targetFpsRange: String?,
    val h264Enabled: Boolean,
    val mjpegPipeline: String?,
    val highSpeedRequested: Boolean,
    val highSpeedActive: Boolean,
    val sessionMode: String,
    val sessionModeReason: String?,
    val requestTemplate: String?,
    val targetSurfaces: List<String>,
    val requestedZoomRatio: Float?,
    val appliedZoomRatio: Float?,
    val cropRegion: String?,
    val activeArraySize: String?,
    val videoStabilizationMode: String?
) {
    companion object {
        fun stopped(): CameraStreamDebugState = CameraStreamDebugState(
            cameraId = null,
            physicalCameraId = null,
            outputSize = null,
            mjpegSize = null,
            targetFps = null,
            targetFpsRange = null,
            h264Enabled = false,
            mjpegPipeline = null,
            highSpeedRequested = false,
            highSpeedActive = false,
            sessionMode = "stopped",
            sessionModeReason = null,
            requestTemplate = null,
            targetSurfaces = emptyList(),
            requestedZoomRatio = null,
            appliedZoomRatio = null,
            cropRegion = null,
            activeArraySize = null,
            videoStabilizationMode = null
        )
    }
}
