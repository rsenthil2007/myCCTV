package com.opencode.multilensipcam

class WebControlCoordinator(
    private val isStreaming: () -> Boolean,
    private val stopStreaming: (String, Boolean) -> Unit,
    private val startStreaming: () -> Unit,
    private val restartStreaming: (String) -> Unit,
    private val applyStreamPreset: (StreamPreset) -> Unit,
    private val getPreviewAdjustment: () -> PreviewAdjustment,
    private val setPreviewAdjustment: (PreviewAdjustment) -> Unit,
    private val applyPreviewAdjustment: () -> Unit,
    private val getMjpegPipeline: () -> MjpegPipeline,
    private val setMjpegPipeline: (MjpegPipeline) -> Unit,
    private val getMjpegFullSize: () -> Boolean,
    private val setMjpegFullSize: (Boolean) -> Unit,
    private val getVideoOverlayEnabled: () -> Boolean,
    private val setVideoOverlayEnabled: (Boolean) -> Unit,
    private val setVideoOverlaySize: (Int) -> Unit,
    private val applyAudioControl: (Boolean) -> Unit,
    private val applyWebCameraSelection: (WebControlCommand) -> Boolean,
    private val selectedOption: () -> CameraLensOption?,
    private val resolveCapabilities: (CameraLensOption) -> CameraCapabilities,
    private val setCurrentCapabilities: (CameraCapabilities) -> Unit,
    private val applyRecommendedSettings: () -> Unit,
    private val applyWebResolutionSelection: (WebControlCommand, CameraCapabilities) -> Boolean,
    private val applyWebFpsSelection: (WebControlCommand, CameraCapabilities) -> Boolean,
    private val applyWebRuntimeControls: (WebControlCommand, CameraLensOption, CameraCapabilities, Boolean) -> Boolean,
    private val refreshControls: (Boolean) -> Unit,
    private val pushRuntimeControlsIfStreaming: () -> Unit,
    private val updateParameterSummary: () -> Unit
) {
    fun apply(command: WebControlCommand) {
        if (command.stopStreaming || command.streaming == false) {
            stopStreaming("Stopped", true)
            return
        }

        command.preset?.let { presetKey ->
            val preset = StreamControlOptions.presetForKey(presetKey)
            if (preset != null) {
                applyStreamPreset(preset)
                return
            }
        }

        val shouldStartStreaming = command.streaming == true && !isStreaming()
        val effects = WebControlApplyEffects()

        val streamSelection = WebStreamControlSelections.resolve(
            command = command,
            currentPreviewAdjustment = getPreviewAdjustment(),
            currentMjpegPipeline = getMjpegPipeline(),
            currentMjpegFullSize = getMjpegFullSize()
        )
        if (streamSelection.previewChanged) {
            setPreviewAdjustment(streamSelection.previewAdjustment)
            applyPreviewAdjustment()
        }
        if (streamSelection.requiresRestart) {
            setMjpegPipeline(streamSelection.mjpegPipeline)
            setMjpegFullSize(streamSelection.mjpegFullSize)
            effects.requiresRestart = true
        }
        command.videoOverlayEnabled?.let { enabled ->
            if (enabled != getVideoOverlayEnabled()) {
                setVideoOverlayEnabled(enabled)
            }
        }
        command.videoOverlaySize?.let { size ->
            setVideoOverlaySize(size.coerceIn(VIDEO_OVERLAY_SIZE_MIN_PERCENT, VIDEO_OVERLAY_SIZE_MAX_PERCENT))
        }

        command.audioEnabled?.let { enabled ->
            applyAudioControl(enabled)
        }

        if (applyWebCameraSelection(command)) {
            effects.requiresRestart = true
            effects.cameraChanged = true
        }

        val option = selectedOption() ?: return
        val capabilities = resolveCapabilities(option)
        setCurrentCapabilities(capabilities)

        if (command.applyRecommended) {
            applyRecommendedSettings()
            return
        }

        if (applyWebResolutionSelection(command, capabilities)) {
            effects.requiresRestart = true
        }

        if (applyWebFpsSelection(command, capabilities)) {
            effects.requiresRestart = true
        }

        if (applyWebRuntimeControls(command, option, capabilities, effects.cameraChanged)) {
            effects.runtimeChanged = true
        }

        refreshControls(false)
        if (effects.requiresRestart && isStreaming()) {
            restartStreaming("Updated from dashboard")
        } else if (shouldStartStreaming && !isStreaming()) {
            startStreaming()
        } else if (effects.runtimeChanged) {
            pushRuntimeControlsIfStreaming()
        } else {
            updateParameterSummary()
        }
    }
}
