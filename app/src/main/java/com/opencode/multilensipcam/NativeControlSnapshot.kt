package com.opencode.multilensipcam

import android.util.Range
import android.util.Size

data class ResolutionEntry(
    val label: String,
    val size: Size?,
    val isManual: Boolean
)

data class FpsEntry(
    val label: String,
    val value: Int?,
    val isUnlimited: Boolean
)

data class NativeControlSnapshot(
    val capabilities: CameraCapabilities,
    val resolutionEntries: List<ResolutionEntry>,
    val fpsEntries: List<FpsEntry>,
    val availableFocusModes: List<FocusMode>,
    val selectedOutputSize: Size,
    val selectedTargetFps: Int?,
    val selectedFocusMode: FocusMode,
    val selectedZoomRatio: Float?,
    val selectedFocusDistance: Float?,
    val selectedExposureCompensation: Int,
    val selectedTorchEnabled: Boolean,
    val zoomRange: Range<Float>?,
    val manualFocusSupported: Boolean,
    val exposureRange: Range<Int>?,
    val showFocusDistance: Boolean
)

object NativeControlSnapshots {
    fun resolve(
        repository: CameraLensRepository,
        option: CameraLensOption,
        capabilities: CameraCapabilities,
        allOptions: List<CameraLensOption>,
        selectedOutputSize: Size?,
        selectedTargetFps: Int?,
        selectedFocusMode: FocusMode,
        selectedZoomRatio: Float?,
        selectedFocusDistance: Float?,
        selectedExposureCompensation: Int,
        selectedTorchEnabled: Boolean,
        isUnlimitedFpsSelected: Boolean,
        resetCameraSpecificValues: Boolean,
        manualResolutionLabel: String,
        unlimitedFpsLabel: String
    ): NativeControlSnapshot {
        val selectableSizes = repository.listSelectableSizes(capabilities)
        val userFpsOptions = repository.listTargetFpsOptions(capabilities).filter { fps -> fps < 60 }
        val resolutionEntries = selectableSizes.map { size ->
            ResolutionEntry(label = repository.sizeLabel(size), size = size, isManual = false)
        } + ResolutionEntry(label = manualResolutionLabel, size = null, isManual = true)
        val fpsEntries = buildFpsEntries(repository, capabilities, unlimitedFpsLabel)
        val zoomRange = NativeZoomControls.effectiveRange(repository, option, capabilities, allOptions)
        val manualFocusSupported = repository.hasManualFocus(capabilities)
        val exposureRange = capabilities.exposureCompensationRange
        val availableFocusModes = if (manualFocusSupported) {
            listOf(FocusMode.AUTO, FocusMode.MANUAL)
        } else {
            listOf(FocusMode.AUTO)
        }
        val resolvedFocusMode = selectedFocusMode.takeIf { it in availableFocusModes } ?: FocusMode.AUTO
        val resolvedOutputSize = selectedOutputSize
            ?.takeIf { it in capabilities.outputSizes }
            ?: repository.chooseRecommendedSize(capabilities)
        val resolvedTargetFps = if (!isUnlimitedFpsSelected) {
            selectedTargetFps
                ?.takeIf { it in userFpsOptions }
                ?: userFpsOptions.firstOrNull { it == 30 }
                ?: userFpsOptions.firstOrNull()
                ?: repository.chooseRecommendedFps(capabilities).coerceAtMost(30)
        } else {
            repository.chooseHighestFps(capabilities)
        }
        val resolvedZoomRatio = when {
            resetCameraSpecificValues || zoomRange == null -> NativeZoomControls.defaultRatio(repository, option, capabilities, allOptions)
            selectedZoomRatio != null -> selectedZoomRatio.coerceIn(zoomRange.lower, zoomRange.upper)
            else -> null
        }
        val resolvedFocusDistance = if (resetCameraSpecificValues || selectedFocusDistance == null) {
            0f
        } else {
            selectedFocusDistance.coerceIn(0f, capabilities.minimumFocusDistance)
        }
        val resolvedExposureCompensation = exposureRange?.let { range ->
            selectedExposureCompensation.coerceIn(range.lower, range.upper)
        } ?: 0
        val resolvedTorchEnabled = capabilities.flashAvailable && selectedTorchEnabled

        return NativeControlSnapshot(
            capabilities = capabilities,
            resolutionEntries = resolutionEntries,
            fpsEntries = fpsEntries,
            availableFocusModes = availableFocusModes,
            selectedOutputSize = resolvedOutputSize,
            selectedTargetFps = resolvedTargetFps,
            selectedFocusMode = resolvedFocusMode,
            selectedZoomRatio = resolvedZoomRatio,
            selectedFocusDistance = resolvedFocusDistance,
            selectedExposureCompensation = resolvedExposureCompensation,
            selectedTorchEnabled = resolvedTorchEnabled,
            zoomRange = zoomRange,
            manualFocusSupported = manualFocusSupported,
            exposureRange = exposureRange,
            showFocusDistance = manualFocusSupported && resolvedFocusMode == FocusMode.MANUAL
        )
    }

    private fun buildFpsEntries(
        repository: CameraLensRepository,
        capabilities: CameraCapabilities,
        unlimitedFpsLabel: String
    ): List<FpsEntry> {
        val entries = repository.listTargetFpsOptions(capabilities)
            .sorted()
            .filter { fps -> fps < 60 }
            .map { fps -> FpsEntry(label = "$fps fps", value = fps, isUnlimited = false) }
            .toMutableList()
        entries += FpsEntry(
            label = unlimitedFpsLabel,
            value = repository.chooseHighestFps(capabilities),
            isUnlimited = true
        )
        return entries
    }

}
