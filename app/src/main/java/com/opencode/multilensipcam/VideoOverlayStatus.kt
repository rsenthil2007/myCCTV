package com.opencode.multilensipcam

data class VideoOverlayStatus(
    val enabled: Boolean,
    val sizePercent: Int,
    val batteryPercent: Int?,
    val charging: Boolean
) {
    val targetWidthPx: Float
        get() = videoOverlayTargetWidthPx(sizePercent)
}

const val VIDEO_OVERLAY_SIZE_MIN_PERCENT = 1
const val VIDEO_OVERLAY_SIZE_MAX_PERCENT = 100
private const val VIDEO_OVERLAY_WIDTH_MIN_PX = 400f
private const val VIDEO_OVERLAY_WIDTH_MAX_PX = 2600f

fun videoOverlayTargetWidthPx(sizePercent: Int): Float {
    val normalized = (sizePercent.coerceIn(VIDEO_OVERLAY_SIZE_MIN_PERCENT, VIDEO_OVERLAY_SIZE_MAX_PERCENT) - 1) / 99f
    return VIDEO_OVERLAY_WIDTH_MIN_PX + ((VIDEO_OVERLAY_WIDTH_MAX_PX - VIDEO_OVERLAY_WIDTH_MIN_PX) * normalized)
}
