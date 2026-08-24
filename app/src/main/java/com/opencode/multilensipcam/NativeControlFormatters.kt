package com.opencode.multilensipcam

import android.util.Range

object NativeControlFormatters {
    fun zoomToProgress(range: Range<Float>, value: Float): Int {
        val span = range.upper - range.lower
        if (span <= 0f) return 0
        return (((value - range.lower) / span) * 1000f).toInt().coerceIn(0, 1000)
    }

    fun progressToZoom(range: Range<Float>, progress: Int): Float {
        val span = range.upper - range.lower
        return range.lower + (span * progress / 1000f)
    }

    fun focusDistanceToProgress(maxDistance: Float, value: Float): Int {
        if (maxDistance <= 0f) return 0
        return ((value / maxDistance) * 1000f).toInt().coerceIn(0, 1000)
    }

    fun progressToFocusDistance(maxDistance: Float, progress: Int): Float {
        if (maxDistance <= 0f) return 0f
        return maxDistance * progress / 1000f
    }

    fun formatZoom(value: Float?, isChineseUi: Boolean): String {
        return if (value == null) unavailableText(isChineseUi) else String.format("%.2fx", value)
    }

    fun formatFocusDistance(value: Float?, isChineseUi: Boolean): String {
        return if (value == null || value < 0.01f) infinityText(isChineseUi) else String.format("%.2f diopters", value)
    }

    fun unavailableText(isChineseUi: Boolean): String = if (isChineseUi) "\u4e0d\u53ef\u7528" else "Unavailable"

    fun infinityText(isChineseUi: Boolean): String = if (isChineseUi) "\u65e0\u9650\u8fdc" else "Infinity"

    fun formatExposure(value: Int): String {
        return if (value == 0) "0 EV" else "$value EV"
    }
}
