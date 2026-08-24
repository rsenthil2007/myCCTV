package com.opencode.multilensipcam

object NativeStatusBadges {
    fun statusMode(
        message: String,
        isStreaming: Boolean,
        hasLoadedCameraOptions: Boolean
    ): StatusMode {
        return when {
            message.contains("permission", ignoreCase = true) -> StatusMode.PERMISSION
            message.contains("error", ignoreCase = true) ||
                message.contains("failed", ignoreCase = true) -> StatusMode.ERROR
            isStreaming -> StatusMode.STREAMING
            hasLoadedCameraOptions -> StatusMode.READY
            else -> StatusMode.IDLE
        }
    }

    fun modeBadgeText(
        statusMode: StatusMode,
        isChineseUi: Boolean
    ): String {
        return when (statusMode) {
            StatusMode.IDLE -> localizedText(isChineseUi, "IDLE", "\u7a7a\u95f2")
            StatusMode.READY -> localizedText(isChineseUi, "READY", "\u5c31\u7eea")
            StatusMode.STREAMING -> localizedText(isChineseUi, "STREAMING", "\u76f4\u64ad\u4e2d")
            StatusMode.ERROR -> localizedText(isChineseUi, "ERROR", "\u9519\u8bef")
            StatusMode.PERMISSION -> localizedText(isChineseUi, "PERMISSION", "\u6743\u9650")
        }
    }

    fun panelStateText(
        isStreaming: Boolean,
        hasLoadedCameraOptions: Boolean,
        isChineseUi: Boolean
    ): String {
        return when {
            isStreaming -> localizedText(isChineseUi, "LIVE", "\u76f4\u64ad")
            !hasLoadedCameraOptions -> localizedText(isChineseUi, "WAITING", "\u7b49\u5f85")
            else -> localizedText(isChineseUi, "READY", "\u5c31\u7eea")
        }
    }

    private fun localizedText(isChineseUi: Boolean, english: String, chinese: String): String {
        return if (isChineseUi) chinese else english
    }
}
