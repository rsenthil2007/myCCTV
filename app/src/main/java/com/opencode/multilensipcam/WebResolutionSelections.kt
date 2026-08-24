package com.opencode.multilensipcam

import android.util.Size

data class WebResolutionSelection(
    val outputSize: Size,
    val manualResolutionMode: Boolean
)

object WebResolutionSelections {
    fun resolve(
        repository: CameraLensRepository,
        capabilities: CameraCapabilities,
        command: WebControlCommand,
        selectedOutputSize: Size?
    ): WebResolutionSelection? {
        command.resolution?.let { label ->
            val size = if (isManualResolutionLabel(label)) {
                command.manualWidth?.let { width ->
                    command.manualHeight?.let { height ->
                        repository.findSupportedOrNearestSize(capabilities, width, height)
                    }
                }
            } else {
                repository.findSizeByLabel(capabilities, label)
            }
            if (size != null && size != selectedOutputSize) {
                return WebResolutionSelection(
                    outputSize = size,
                    manualResolutionMode = isManualResolutionLabel(label)
                )
            }
        }

        if (command.resolution == null && command.manualWidth != null && command.manualHeight != null) {
            val manualSize = repository.findSupportedOrNearestSize(
                capabilities,
                command.manualWidth,
                command.manualHeight
            )
            if (manualSize != null && manualSize != selectedOutputSize) {
                return WebResolutionSelection(
                    outputSize = manualSize,
                    manualResolutionMode = true
                )
            }
        }

        return null
    }

    private fun isManualResolutionLabel(label: String): Boolean {
        return label.equals("Manual input", ignoreCase = true) ||
            label == "\u624b\u52a8\u8f93\u5165"
    }
}
