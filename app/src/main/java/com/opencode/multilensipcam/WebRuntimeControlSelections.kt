package com.opencode.multilensipcam

data class WebRuntimeControlSelection(
    val streamQuality: Int,
    val focusMode: FocusMode,
    val zoomRatio: Float?,
    val focusDistance: Float?,
    val exposureCompensation: Int,
    val torchEnabled: Boolean
)

object WebRuntimeControlSelections {
    fun resolve(
        repository: CameraLensRepository,
        option: CameraLensOption,
        capabilities: CameraCapabilities,
        allOptions: List<CameraLensOption>,
        command: WebControlCommand,
        availableFocusModes: List<FocusMode>,
        selectedStreamQuality: Int,
        selectedFocusMode: FocusMode,
        selectedZoomRatio: Float?,
        selectedFocusDistance: Float?,
        selectedExposureCompensation: Int,
        selectedTorchEnabled: Boolean,
        cameraChanged: Boolean
    ): WebRuntimeControlSelection? {
        var nextStreamQuality = selectedStreamQuality
        var nextFocusMode = selectedFocusMode
        var nextZoomRatio = selectedZoomRatio
        var nextFocusDistance = selectedFocusDistance
        var nextExposureCompensation = selectedExposureCompensation
        var nextTorchEnabled = selectedTorchEnabled
        var changed = false

        command.quality?.let { quality ->
            val clamped = quality.coerceIn(StreamControlOptions.qualityOptions.first(), StreamControlOptions.qualityOptions.last())
            if (clamped != selectedStreamQuality) {
                nextStreamQuality = StreamControlOptions.nearestQuality(clamped)
                changed = true
            }
        }

        command.focusMode?.let { raw ->
            val mode = FocusMode.values().firstOrNull { it.wireValue.equals(raw, ignoreCase = true) }
            if (mode != null && mode in availableFocusModes && mode != selectedFocusMode) {
                nextFocusMode = mode
                changed = true
            }
        }

        NativeZoomControls.effectiveRange(repository, option, capabilities, allOptions)?.let { zoomRange ->
            if (!cameraChanged && command.zoom != null) {
                nextZoomRatio = command.zoom.coerceIn(zoomRange.lower, zoomRange.upper)
                changed = true
            }
        }

        if (!cameraChanged &&
            command.focusDistance != null &&
            nextFocusMode == FocusMode.MANUAL &&
            repository.hasManualFocus(capabilities)
        ) {
            nextFocusDistance = command.focusDistance.coerceIn(0f, capabilities.minimumFocusDistance)
            changed = true
        }

        capabilities.exposureCompensationRange?.let { exposureRange ->
            if (!cameraChanged && command.exposureCompensation != null) {
                nextExposureCompensation = command.exposureCompensation.coerceIn(exposureRange.lower, exposureRange.upper)
                changed = true
            }
        }

        command.torchEnabled?.let { torchEnabled ->
            val resolved = capabilities.flashAvailable && torchEnabled
            if (resolved != selectedTorchEnabled) {
                nextTorchEnabled = resolved
                changed = true
            }
        }

        return if (changed) {
            WebRuntimeControlSelection(
                streamQuality = nextStreamQuality,
                focusMode = nextFocusMode,
                zoomRatio = nextZoomRatio,
                focusDistance = nextFocusDistance,
                exposureCompensation = nextExposureCompensation,
                torchEnabled = nextTorchEnabled
            )
        } else {
            null
        }
    }
}
