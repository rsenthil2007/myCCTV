package com.opencode.multilensipcam

import com.opencode.multilensipcam.databinding.ActivityMainBinding

object NativeLanguageTexts {
    data class Labels(
        val languageToggle: String,
        val scanCameras: String,
        val refreshList: String,
        val exportReport: String,
        val rotation: String,
        val fit: String,
        val scale: String,
        val startStreaming: String,
        val stopStreaming: String,
        val applyManualResolution: String,
        val preset1080p30: String,
        val preset4k30: String,
        val preset720p60: String,
        val exposure: String,
        val manualResolutionHint: String
    )

    fun apply(
        binding: ActivityMainBinding,
        labels: Labels
    ) {
        binding.languageToggleButton.text = labels.languageToggle
        binding.nativeCameraScanButton.text = labels.scanCameras
        binding.nativeCameraCacheRefreshButton.text = labels.refreshList
        binding.nativeCameraReportButton.text = labels.exportReport
        binding.previewRotationMinusButton.text = labels.rotation + " -"
        binding.previewRotationResetButton.text = labels.fit
        binding.previewRotationPlusButton.text = labels.rotation + " +"
        binding.previewScaleDownButton.text = labels.scale + " -"
        binding.previewScaleResetButton.text = labels.fit
        binding.previewScaleUpButton.text = labels.scale + " +"
        binding.startButton.text = labels.startStreaming
        binding.stopButton.text = labels.stopStreaming
        binding.applyManualResolutionButton.text = labels.applyManualResolution
        binding.preset1080p30Button.text = labels.preset1080p30
        binding.preset4k30Button.text = labels.preset4k30
        binding.preset720p60Button.text = labels.preset720p60
        binding.exposureLabel.text = labels.exposure
        binding.manualResolutionHintText.text = labels.manualResolutionHint
    }
}
