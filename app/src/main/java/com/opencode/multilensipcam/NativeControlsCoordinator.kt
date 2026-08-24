package com.opencode.multilensipcam

import android.view.View
import android.widget.ArrayAdapter
import com.opencode.multilensipcam.databinding.ActivityMainBinding

object NativeControlsCoordinator {
    data class Request(
        val option: CameraLensOption,
        val capabilities: CameraCapabilities,
        val allOptions: List<CameraLensOption>,
        val selectedOutputSize: android.util.Size?,
        val selectedTargetFps: Int?,
        val selectedStreamQuality: Int,
        val selectedFocusMode: FocusMode,
        val selectedZoomRatio: Float?,
        val selectedFocusDistance: Float?,
        val selectedExposureCompensation: Int,
        val selectedTorchEnabled: Boolean,
        val isUnlimitedFpsSelected: Boolean,
        val isManualResolutionMode: Boolean,
        val resetCameraSpecificValues: Boolean,
        val isChineseUi: Boolean,
        val manualResolutionLabel: String,
        val unlimitedFpsLabel: String
    )

    data class Result(
        val snapshot: NativeControlSnapshot,
        val selectedZoomRatio: Float?,
        val selectedFocusDistance: Float?,
        val selectedExposureCompensation: Int,
        val selectedTorchEnabled: Boolean
    )

    fun refresh(
        binding: ActivityMainBinding,
        repository: CameraLensRepository,
        request: Request,
        stringAdapterFactory: (List<String>) -> ArrayAdapter<String>,
        focusModeLabel: (FocusMode) -> String
    ): Result {
        val snapshot = NativeControlSnapshots.resolve(
            repository = repository,
            option = request.option,
            capabilities = request.capabilities,
            allOptions = request.allOptions,
            selectedOutputSize = request.selectedOutputSize,
            selectedTargetFps = request.selectedTargetFps,
            selectedFocusMode = request.selectedFocusMode,
            selectedZoomRatio = request.selectedZoomRatio,
            selectedFocusDistance = request.selectedFocusDistance,
            selectedExposureCompensation = request.selectedExposureCompensation,
            selectedTorchEnabled = request.selectedTorchEnabled,
            isUnlimitedFpsSelected = request.isUnlimitedFpsSelected,
            resetCameraSpecificValues = request.resetCameraSpecificValues,
            manualResolutionLabel = request.manualResolutionLabel,
            unlimitedFpsLabel = request.unlimitedFpsLabel
        )

        binding.resolutionSpinner.adapter = stringAdapterFactory(snapshot.resolutionEntries.map { it.label })
        binding.resolutionSpinner.setSelection(resolveResolutionSelectionIndex(repository, snapshot, request.isManualResolutionMode), false)
        updateManualResolutionUi(binding, snapshot, request.isManualResolutionMode)

        binding.fpsSpinner.adapter = stringAdapterFactory(snapshot.fpsEntries.map { it.label })
        binding.fpsSpinner.setSelection(resolveFpsSelectionIndex(snapshot, request.isUnlimitedFpsSelected), false)

        binding.focusModeSpinner.adapter = stringAdapterFactory(snapshot.availableFocusModes.map(focusModeLabel))
        binding.focusModeSpinner.setSelection(snapshot.availableFocusModes.indexOf(snapshot.selectedFocusMode).coerceAtLeast(0), false)

        binding.qualitySeekBar.progress = StreamControlOptions.qualityOptions.indexOf(request.selectedStreamQuality).coerceAtLeast(0)
        binding.qualityValueText.text = NativeStatusSummaries.formatQuality(request.selectedStreamQuality)

        var selectedZoomRatio = snapshot.selectedZoomRatio
        if (snapshot.zoomRange != null) {
            binding.zoomContainer.visibility = View.VISIBLE
            val zoomRange = snapshot.zoomRange
            val zoomValue = (selectedZoomRatio ?: zoomRange.lower).coerceIn(zoomRange.lower, zoomRange.upper)
            selectedZoomRatio = zoomValue
            binding.zoomSeekBar.progress = NativeControlFormatters.zoomToProgress(zoomRange, zoomValue)
            binding.zoomValueText.text = NativeControlFormatters.formatZoom(zoomValue, request.isChineseUi)
        } else {
            binding.zoomContainer.visibility = View.GONE
            selectedZoomRatio = null
        }

        var selectedFocusDistance = snapshot.selectedFocusDistance
        if (snapshot.showFocusDistance) {
            binding.focusDistanceContainer.visibility = View.VISIBLE
            val focusDistance = (selectedFocusDistance ?: 0f).coerceIn(0f, request.capabilities.minimumFocusDistance)
            selectedFocusDistance = focusDistance
            binding.focusDistanceSeekBar.progress = NativeControlFormatters.focusDistanceToProgress(request.capabilities.minimumFocusDistance, focusDistance)
            binding.focusDistanceValueText.text = NativeControlFormatters.formatFocusDistance(focusDistance, request.isChineseUi)
        } else {
            binding.focusDistanceContainer.visibility = View.GONE
            binding.focusDistanceValueText.text = NativeControlFormatters.infinityText(request.isChineseUi)
        }

        if (snapshot.exposureRange != null) {
            val exposureRange = snapshot.exposureRange
            binding.exposureContainer.visibility = View.VISIBLE
            binding.exposureSeekBar.max = exposureRange.upper - exposureRange.lower
            binding.exposureSeekBar.progress = snapshot.selectedExposureCompensation - exposureRange.lower
            binding.exposureValueText.text = NativeControlFormatters.formatExposure(snapshot.selectedExposureCompensation)
        } else {
            binding.exposureContainer.visibility = View.GONE
            binding.exposureValueText.text = NativeControlFormatters.unavailableText(request.isChineseUi)
        }

        if (snapshot.capabilities.flashAvailable) {
            binding.torchContainer.visibility = View.VISIBLE
            binding.torchSwitch.isChecked = snapshot.selectedTorchEnabled
        } else {
            binding.torchContainer.visibility = View.GONE
            binding.torchSwitch.isChecked = false
        }

        return Result(
            snapshot = snapshot,
            selectedZoomRatio = selectedZoomRatio,
            selectedFocusDistance = selectedFocusDistance,
            selectedExposureCompensation = snapshot.selectedExposureCompensation,
            selectedTorchEnabled = snapshot.selectedTorchEnabled
        )
    }

    private fun updateManualResolutionUi(
        binding: ActivityMainBinding,
        snapshot: NativeControlSnapshot,
        isManualResolutionMode: Boolean
    ) {
        binding.manualResolutionContainer.visibility = if (isManualResolutionMode) View.VISIBLE else View.GONE
        if (isManualResolutionMode) {
            binding.manualWidthInput.setText(snapshot.selectedOutputSize.width.toString())
            binding.manualHeightInput.setText(snapshot.selectedOutputSize.height.toString())
        }
    }

    private fun resolveResolutionSelectionIndex(
        repository: CameraLensRepository,
        snapshot: NativeControlSnapshot,
        isManualResolutionMode: Boolean
    ): Int {
        if (isManualResolutionMode) {
            return snapshot.resolutionEntries.indexOfFirst { it.isManual }.coerceAtLeast(0)
        }
        val selectedLabel = repository.sizeLabel(snapshot.selectedOutputSize)
        return snapshot.resolutionEntries.indexOfFirst { it.label == selectedLabel }.coerceAtLeast(0)
    }

    private fun resolveFpsSelectionIndex(
        snapshot: NativeControlSnapshot,
        isUnlimitedFpsSelected: Boolean
    ): Int {
        if (isUnlimitedFpsSelected) {
            return snapshot.fpsEntries.indexOfFirst { it.isUnlimited }.coerceAtLeast(0)
        }
        return snapshot.fpsEntries.indexOfFirst { it.value == snapshot.selectedTargetFps && !it.isUnlimited }.coerceAtLeast(0)
    }
}
