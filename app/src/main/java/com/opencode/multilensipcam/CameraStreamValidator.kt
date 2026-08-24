package com.opencode.multilensipcam

import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraManager
import java.util.concurrent.atomic.AtomicInteger

class CameraStreamValidator(
    private val cameraManager: CameraManager,
    private val repository: CameraLensRepository
) {
    fun validate(
        cameraId: String,
        options: List<CameraLensOption>
    ): CameraStreamValidationResult {
        val option = repository.findOptionByControlKey(
            options = options,
            key = cameraId,
            allowedDebugDirectCameraIds = setOf(cameraId)
        ) ?: return CameraStreamValidationResult(false, 0, "Camera option unavailable.")
        val capabilities = runCatching { repository.getCapabilities(option) }.getOrNull()
            ?: return CameraStreamValidationResult(false, 0, "Camera capabilities unavailable.")
        val outputSize = repository.findSupportedOrNearestSize(capabilities, 1280, 720)
            ?: repository.chooseRecommendedSize(capabilities)
        val targetFps = repository.chooseTargetFpsRange(capabilities, 30)?.upper
            ?: repository.chooseRecommendedFps(capabilities)
        val texture = SurfaceTexture(0).apply {
            setDefaultBufferSize(outputSize.width, outputSize.height)
        }
        val frames = AtomicInteger(0)
        val validationStreamer = CameraStreamer(
            cameraManager = cameraManager,
            onFrame = { frames.incrementAndGet() },
            onH264AccessUnit = {},
            onStatus = {}
        )
        return try {
            validationStreamer.start(
                CameraStreamConfig(
                    option = option,
                    outputSize = outputSize,
                    mjpegSize = repository.chooseMjpegSize(capabilities, outputSize),
                    jpegQuality = 50,
                    streamQuality = 50,
                    targetFps = targetFps,
                    targetFpsRange = repository.chooseTargetFpsRange(capabilities, targetFps),
                    zoomRatio = repository.inferNativeZoomRatio(option, capabilities, listOf(option)),
                    focusMode = FocusMode.AUTO,
                    h264Enabled = false,
                    mjpegPipeline = MjpegPipeline.YUV_JPEG
                ),
                texture
            )
            val deadline = System.currentTimeMillis() + VALIDATION_TIMEOUT_MS
            while (System.currentTimeMillis() < deadline && frames.get() < MIN_VALIDATION_FRAMES) {
                Thread.sleep(100)
            }
            val frameCount = frames.get()
            if (frameCount > 0) {
                CameraStreamValidationResult(true, frameCount, null)
            } else {
                CameraStreamValidationResult(false, 0, "CameraStreamer produced no MJPEG frames.")
            }
        } catch (throwable: Throwable) {
            CameraStreamValidationResult(false, frames.get(), "${throwable.javaClass.simpleName}: ${throwable.message?.take(120)}")
        } finally {
            validationStreamer.stop()
            runCatching { texture.release() }
        }
    }

    private companion object {
        const val VALIDATION_TIMEOUT_MS = 3_500L
        const val MIN_VALIDATION_FRAMES = 3
    }
}

data class CameraStreamValidationResult(
    val verified: Boolean,
    val frames: Int,
    val error: String?
)
