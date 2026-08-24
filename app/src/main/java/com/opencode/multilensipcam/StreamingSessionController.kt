package com.opencode.multilensipcam

import android.content.pm.ActivityInfo
import android.graphics.SurfaceTexture
import android.util.Size

data class StreamingStartRequest(
    val cameraScanInProgress: Boolean,
    val option: CameraLensOption?,
    val previewTexture: SurfaceTexture?,
    val capabilities: CameraCapabilities?,
    val selectedOutputSize: Size?,
    val useFullMjpegSize: Boolean,
    val streamQuality: Int,
    val selectedTargetFps: Int?,
    val unlimitedFpsSelected: Boolean,
    val runtimeControls: NativeRuntimeControlValues?,
    val focusMode: FocusMode,
    val mjpegPipeline: MjpegPipeline
)

class StreamingSessionController(
    private val repository: CameraLensRepository,
    private val server: MjpegHttpServer,
    private val streamer: CameraStreamer,
    private val startRequestProvider: () -> StreamingStartRequest,
    private val setPanelOpen: (Boolean) -> Unit,
    private val setRequestedOrientation: (Int) -> Unit,
    private val setKeepScreenOn: (Boolean) -> Unit,
    private val updatePreviewPresentation: (Size, Int) -> Unit,
    private val updatePreviewFillMode: () -> Unit,
    private val updateStreamingUiState: () -> Unit,
    private val updateUrl: () -> Unit,
    private val updateParameterSummary: () -> Unit,
    private val updateStatus: (String) -> Unit,
    private val audioEnabledProvider: () -> Boolean,
    private val audioPermissionGranted: () -> Boolean,
    private val startAudio: () -> Unit,
    private val stopAudio: () -> Unit,
    private val runOnUiThread: (() -> Unit) -> Unit
) {
    var isStreaming: Boolean = false
        private set

    var h264EnabledForSession: Boolean = false
        private set

    fun start() {
        val request = startRequestProvider()
        if (request.cameraScanInProgress) {
            updateStatus("Camera scan in progress")
            return
        }
        val option = request.option ?: return
        val texture = request.previewTexture
        if (texture == null) {
            updateStatus("Preview surface not ready")
            return
        }
        val capabilities = request.capabilities ?: return
        val runtimeControls = request.runtimeControls ?: return

        val config = CameraStreamConfigs.build(
            repository = repository,
            option = option,
            capabilities = capabilities,
            selectedOutputSize = request.selectedOutputSize,
            useFullMjpegSize = request.useFullMjpegSize,
            streamQuality = request.streamQuality,
            selectedTargetFps = request.selectedTargetFps,
            unlimitedFpsSelected = request.unlimitedFpsSelected,
            runtimeControls = runtimeControls,
            focusMode = request.focusMode,
            h264Enabled = h264EnabledForSession,
            mjpegPipeline = request.mjpegPipeline
        )

        updatePreviewPresentation(config.outputSize, capabilities.sensorOrientation)
        server.start()
        streamer.start(config, texture)
        if (audioEnabledProvider() && audioPermissionGranted()) {
            startAudio()
        }
        isStreaming = true
        setPanelOpen(false)
        setKeepScreenOn(true)
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE)
        updatePreviewPresentation(config.outputSize, capabilities.sensorOrientation)
        updatePreviewFillMode()
        updateStreamingUiState()
        updateUrl()
        updateParameterSummary()
        updateStatus("Streaming")
    }

    fun restart(reason: String) {
        if (!isStreaming) return
        updateStatus(reason)
        stopAudio()
        start()
    }

    @Suppress("UNUSED_PARAMETER")
    fun stop(status: String, keepServerAlive: Boolean = false) {
        isStreaming = false
        h264EnabledForSession = false
        stopAudio()
        streamer.stop()
        server.closeActiveStreams()
        setPanelOpen(true)
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)
        setKeepScreenOn(false)
        updatePreviewFillMode()
        updateStreamingUiState()
        updateUrl()
        updateParameterSummary()
        updateStatus(status)
    }

    fun handleH264StreamRequest(): Boolean {
        if (!isStreaming) return false
        if (h264EnabledForSession) return true
        h264EnabledForSession = true
        runOnUiThread {
            if (isStreaming) {
                restart("H.264 stream requested")
            }
        }
        return true
    }

    fun stopH264AfterIdle(status: String) {
        if (!isStreaming || !h264EnabledForSession) return
        h264EnabledForSession = false
        runOnUiThread {
            if (isStreaming) {
                restart(status)
            }
        }
    }

    fun shutdown() {
        isStreaming = false
        h264EnabledForSession = false
        stopAudio()
        streamer.stop()
        server.stop()
        setKeepScreenOn(false)
    }
}
