package com.opencode.multilensipcam

object NativeStatusSummaries {
    fun parameterSummary(
        cameraLabel: String,
        resolutionLabel: String,
        fpsLabel: String,
        streamQuality: Int
    ): String {
        return listOf(
            cameraLabel,
            compactResolutionLabel(resolutionLabel),
            fpsLabel,
            formatQuality(streamQuality)
        ).joinToString(" \u00b7 ")
    }

    fun fpsLabel(
        selectedTargetFps: Int?,
        unlimitedFpsSelected: Boolean,
        isChineseUi: Boolean
    ): String {
        return when {
            unlimitedFpsSelected -> "Unlimited"
            selectedTargetFps != null -> "${selectedTargetFps}fps"
            else -> "-"
        }
    }

    fun liveStatus(
        parameterSummary: String,
        appVersionLabel: String,
        isChineseUi: Boolean
    ): String {
        val prefix = "Live"
        return "$prefix\n$parameterSummary\n$appVersionLabel"
    }

    fun formatQuality(value: Int): String = "$value%"

    private fun compactResolutionLabel(label: String): String {
        val parts = label.lowercase().split("x")
        if (parts.size != 2) return label
        val height = parts[1].trim().toIntOrNull() ?: return label
        return when (height) {
            2160 -> "4K"
            1440 -> "1440P"
            1080 -> "1080P"
            720 -> "720P"
            480 -> "480P"
            else -> label
        }
    }

    private fun localizedText(isChineseUi: Boolean, english: String, chinese: String): String {
        return english
    }
}
