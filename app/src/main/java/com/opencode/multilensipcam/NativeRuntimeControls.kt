package com.opencode.multilensipcam

data class NativeRuntimeControlValues(
    val zoomRatio: Float?,
    val focusDistance: Float?,
    val exposureCompensation: Int,
    val torchEnabled: Boolean
)

object NativeRuntimeControls {
    fun resolve(
        repository: CameraLensRepository,
        option: CameraLensOption,
        capabilities: CameraCapabilities,
        allOptions: List<CameraLensOption>,
        selectedZoomRatio: Float?,
        selectedFocusMode: FocusMode,
        selectedFocusDistance: Float?,
        selectedExposureCompensation: Int,
        selectedTorchEnabled: Boolean
    ): NativeRuntimeControlValues {
        val zoomRange = NativeZoomControls.effectiveRange(repository, option, capabilities, allOptions)
        val zoomRatio = zoomRange?.let { range ->
            (selectedZoomRatio ?: range.lower).coerceIn(range.lower, range.upper)
        }
        val focusDistance = if (selectedFocusMode == FocusMode.MANUAL && repository.hasManualFocus(capabilities)) {
            (selectedFocusDistance ?: 0f).coerceIn(0f, capabilities.minimumFocusDistance)
        } else {
            null
        }
        val exposureRange = capabilities.exposureCompensationRange
        val exposureCompensation = exposureRange?.let { range ->
            selectedExposureCompensation.coerceIn(range.lower, range.upper)
        } ?: 0
        return NativeRuntimeControlValues(
            zoomRatio = zoomRatio,
            focusDistance = focusDistance,
            exposureCompensation = exposureCompensation,
            torchEnabled = capabilities.flashAvailable && selectedTorchEnabled
        )
    }
}
