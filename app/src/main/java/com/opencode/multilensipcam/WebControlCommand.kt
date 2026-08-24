package com.opencode.multilensipcam

data class WebControlCommand(
    val cameraKey: String? = null,
    val resolution: String? = null,
    val manualWidth: Int? = null,
    val manualHeight: Int? = null,
    val fps: Int? = null,
    val unlimitedFps: Boolean? = null,
    val quality: Int? = null,
    val zoom: Float? = null,
    val focusMode: String? = null,
    val focusDistance: Float? = null,
    val exposureCompensation: Int? = null,
    val torchEnabled: Boolean? = null,
    val videoOverlayEnabled: Boolean? = null,
    val videoOverlaySize: Int? = null,
    val applyRecommended: Boolean = false,
    val preset: String? = null,
    val previewRotation: Int? = null,
    val previewScale: Float? = null,
    val previewStretchX: Float? = null,
    val previewStretchY: Float? = null,
    val jpegPipeline: String? = null,
    val mjpegFullSize: Boolean? = null,
    val audioEnabled: Boolean? = null,
    val stopStreaming: Boolean = false,
    val streaming: Boolean? = null
)
