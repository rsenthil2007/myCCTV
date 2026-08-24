package com.opencode.multilensipcam

import android.graphics.ImageFormat
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.SurfaceTexture
import android.graphics.YuvImage
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraConstrainedHighSpeedCaptureSession
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.params.OutputConfiguration
import android.hardware.camera2.params.SessionConfiguration
import android.media.Image
import android.media.ImageReader
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.util.Range
import android.view.Surface
import androidx.annotation.RequiresApi
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.max

class CameraStreamer(
    private val cameraManager: CameraManager,
    private val onFrame: (MjpegFrame) -> Unit,
    private val onH264AccessUnit: (H264AccessUnit) -> Unit,
    private val onStatus: (String) -> Unit,
    private val videoOverlayStatusProvider: () -> VideoOverlayStatus = { VideoOverlayStatus(false, 25, null, false) },
    private val mjpegOutputRotationProvider: () -> Int = { 0 },
    private val mjpegConsumerActive: () -> Boolean = { true }
) {
    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var imageReader: ImageReader? = null
    private var backgroundThread: HandlerThread? = null
    private var backgroundHandler: Handler? = null
    private var sessionExecutor: ExecutorService? = null
    private var encodeExecutor: ExecutorService? = null
    private var currentRequestBuilder: CaptureRequest.Builder? = null
    private var currentConfig: CameraStreamConfig? = null
    private var currentCharacteristics: CameraCharacteristics? = null
    private val encodeInFlight = AtomicBoolean(false)
    private var nextFrameDeadlineMs = 0L
    private var videoEncoder: MediaCodec? = null
    private var encoderInputSurface: Surface? = null
    private var encoderDrainExecutor: ExecutorService? = null
    private val encoderRunning = AtomicBoolean(false)
    private var sourceFramesSeen = 0L
    private var sourceFramesEncoded = 0L
    private var sourceFramesDroppedByFpsGate = 0L
    private var sourceFramesDroppedByEncoderBusy = 0L
    private var activeMjpegPipeline: MjpegPipeline = MjpegPipeline.YUV_JPEG
    @Volatile
    private var streamDebugState: CameraStreamDebugState = CameraStreamDebugState.stopped()
    private var currentSessionMode: String = "stopped"
    private var currentSessionModeReason: String? = null
    private var currentRequestTemplate: String? = null
    private var currentTargetSurfaces: List<String> = emptyList()
    private var highSpeedSessionActive: Boolean = false
    private var activePreviewSurface: Surface? = null
    private var activeReaderSurface: Surface? = null
    private var activeEncoderSurface: Surface? = null
    private val streamGeneration = AtomicLong(0L)
    private val overlayTimeFormat = SimpleDateFormat("MM/dd/yy HH:mm:ss", Locale.US)
    private val overlayRenderer = PixelFontOverlayRenderer()
    private val nv21OverlayRenderer = Nv21PixelOverlayRenderer()

    fun start(config: CameraStreamConfig, textureView: SurfaceTexture) {
        stop()
        val generation = streamGeneration.incrementAndGet()
        startThread()
        resetFrameMetrics()
        currentConfig = config
        currentCharacteristics = cameraManager.getCameraCharacteristics(config.option.logicalCameraId)
        activeMjpegPipeline = resolveMjpegPipeline(config, currentCharacteristics)
        updateStreamDebugState(
            sessionMode = "opening",
            sessionModeReason = config.highSpeedVideoFallbackReason,
            requestTemplate = null,
            targetSurfaces = emptyList(),
            highSpeedActive = false
        )
        encodeExecutor = Executors.newSingleThreadExecutor()
        if (!config.option.isFrontFacing && config.h264Enabled) {
            startVideoEncoder(config)
        } else if (config.h264Enabled) {
            onStatus("H.264 unavailable for front camera")
        } else {
            onStatus("Opening MJPEG-only stream")
        }

        val previewWidth = config.outputSize.width
        val previewHeight = config.outputSize.height
        textureView.setDefaultBufferSize(previewWidth, previewHeight)
        val previewSurface = Surface(textureView)

        val imageFormat = when (activeMjpegPipeline) {
            MjpegPipeline.YUV_JPEG -> ImageFormat.YUV_420_888
            MjpegPipeline.CAMERA_JPEG -> ImageFormat.JPEG
        }
        imageReader = ImageReader.newInstance(
            config.mjpegSize.width,
            config.mjpegSize.height,
            imageFormat,
            2
        ).apply {
            setOnImageAvailableListener({ reader ->
                val image = reader.acquireLatestImage() ?: return@setOnImageAvailableListener
                sourceFramesSeen += 1
                val activeConfig = currentConfig
                if (activeConfig == null) {
                    image.close()
                    return@setOnImageAvailableListener
                }
                if (!mjpegConsumerActive()) {
                    image.close()
                    return@setOnImageAvailableListener
                }
                val frameIntervalMs = max(1, 1000 / max(1, activeConfig.targetFps))
                val nowMs = SystemClock.elapsedRealtime()
                if (activeConfig.targetFps < 30 && nowMs < nextFrameDeadlineMs) {
                    sourceFramesDroppedByFpsGate += 1
                    image.close()
                    return@setOnImageAvailableListener
                }
                if (!encodeInFlight.compareAndSet(false, true)) {
                    sourceFramesDroppedByEncoderBusy += 1
                    image.close()
                    return@setOnImageAvailableListener
                }
                nextFrameDeadlineMs = nowMs + frameIntervalMs
                val pipeline = activeMjpegPipeline
                val copiedFrame = try {
                    copyCapturedFrame(image, pipeline)
                } catch (_: Exception) {
                    encodeInFlight.set(false)
                    image.close()
                    return@setOnImageAvailableListener
                }
                image.close()

                val executor = encodeExecutor
                if (executor == null) {
                    encodeInFlight.set(false)
                    return@setOnImageAvailableListener
                }
                executor.execute {
                    try {
                        val encodeStartedAtMs = SystemClock.elapsedRealtime()
                        val outputRotationDegrees = normalizeOutputRotationDegrees(mjpegOutputRotationProvider())
                        val overlayStatus = videoOverlayStatusProvider()
                        val jpeg = when (copiedFrame) {
                            is CopiedFrame.Yuv -> yuv420ToJpegFromNv21(
                                nv21 = copiedFrame.nv21,
                                width = copiedFrame.width,
                                height = copiedFrame.height,
                                yuvToNv21DurationMs = copiedFrame.yuvToNv21DurationMs,
                                jpegQuality = activeConfig.jpegQuality,
                                outputRotationDegrees = outputRotationDegrees,
                                overlayStatus = overlayStatus
                            )

                            is CopiedFrame.CameraJpeg -> cameraJpegBytesToResult(copiedFrame.bytes)
                        }
                        val rotationRemainingAfterEncode = normalizeOutputRotationDegrees(
                            outputRotationDegrees - jpeg.rotationAppliedBeforeEncodeDegrees
                        )
                        val overlayPendingStatus = if (jpeg.overlayAppliedBeforeEncode) {
                            overlayStatus.copy(enabled = false)
                        } else {
                            overlayStatus
                        }
                        val transformResult = applyOutputTransformIfNeeded(
                            sourceJpeg = jpeg.bytes,
                            rotationDegrees = rotationRemainingAfterEncode,
                            jpegQuality = activeConfig.jpegQuality,
                            overlayStatus = overlayPendingStatus
                        )
                        val frameWidth = if (outputRotationDegrees == 90 || outputRotationDegrees == 270) {
                            copiedFrame.height
                        } else {
                            copiedFrame.width
                        }
                        val frameHeight = if (outputRotationDegrees == 90 || outputRotationDegrees == 270) {
                            copiedFrame.width
                        } else {
                            copiedFrame.height
                        }
                        val generatedAtMs = SystemClock.elapsedRealtime()
                        sourceFramesEncoded += 1
                        val frame = MjpegFrame(
                            bytes = transformResult.bytes,
                            pipeline = pipeline,
                            width = frameWidth,
                            height = frameHeight,
                            outputRotationDegrees = outputRotationDegrees,
                            jpegQuality = activeConfig.jpegQuality.coerceIn(25, 92),
                            targetFps = activeConfig.targetFps,
                            capturedAtElapsedMs = nowMs,
                            generatedAtElapsedMs = generatedAtMs,
                            encodeDurationMs = generatedAtMs - encodeStartedAtMs,
                            yuvToNv21DurationMs = jpeg.yuvToNv21DurationMs,
                            jpegCompressDurationMs = jpeg.jpegCompressDurationMs,
                            outputTransformDurationMs = transformResult.transformDurationMs,
                            overlayRenderDurationMs = jpeg.overlayRenderDurationMs + transformResult.overlayRenderDurationMs,
                            sourceFramesSeen = sourceFramesSeen,
                            sourceFramesEncoded = sourceFramesEncoded,
                            sourceFramesDropped = sourceFramesDroppedByFpsGate + sourceFramesDroppedByEncoderBusy,
                            sourceFramesDroppedByFpsGate = sourceFramesDroppedByFpsGate,
                            sourceFramesDroppedByEncoderBusy = sourceFramesDroppedByEncoderBusy
                        )
                        onFrame(frame)
                    } catch (_: Exception) {
                        // Keep streaming alive if encode or consumer fails.
                    } finally {
                        encodeInFlight.set(false)
                    }
                }
            }, backgroundHandler)
        }

        onStatus("Opening ${config.option.label} at ${previewWidth}x${previewHeight} (${activeMjpegPipeline.label})")
        cameraManager.openCamera(
            config.option.logicalCameraId,
            object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    if (!isCurrentStreamGeneration(generation)) {
                        camera.close()
                        return
                    }
                    cameraDevice = camera
                    createSession(camera, config.option, previewSurface, generation)
                }

                override fun onDisconnected(camera: CameraDevice) {
                    onStatus("Camera disconnected")
                    camera.close()
                }

                override fun onError(camera: CameraDevice, error: Int) {
                    onStatus("Camera error: $error")
                    camera.close()
                }
            },
            backgroundHandler
        )
    }

    fun stop() {
        streamGeneration.incrementAndGet()
        runCatching { captureSession?.stopRepeating() }
        runCatching { captureSession?.close() }
        runCatching { cameraDevice?.close() }
        runCatching { imageReader?.close() }
        runCatching { sessionExecutor?.shutdownNow() }
        runCatching { encodeExecutor?.shutdownNow() }
        stopVideoEncoder()
        captureSession = null
        cameraDevice = null
        imageReader = null
        sessionExecutor = null
        encodeExecutor = null
        currentRequestBuilder = null
        currentConfig = null
        currentCharacteristics = null
        encodeInFlight.set(false)
        streamDebugState = CameraStreamDebugState.stopped()
        currentSessionMode = "stopped"
        currentSessionModeReason = null
        currentRequestTemplate = null
        currentTargetSurfaces = emptyList()
        highSpeedSessionActive = false
        activePreviewSurface = null
        activeReaderSurface = null
        activeEncoderSurface = null
        stopThread()
    }

    fun debugState(): CameraStreamDebugState = streamDebugState

    private fun resetFrameMetrics() {
        nextFrameDeadlineMs = 0L
        sourceFramesSeen = 0L
        sourceFramesEncoded = 0L
        sourceFramesDroppedByFpsGate = 0L
        sourceFramesDroppedByEncoderBusy = 0L
    }

    private fun resolveMjpegPipeline(
        config: CameraStreamConfig,
        characteristics: CameraCharacteristics?
    ): MjpegPipeline {
        val jpegSizes = characteristics
            ?.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            ?.getOutputSizes(ImageFormat.JPEG)
            ?.toList()
            .orEmpty()
        val cameraJpegSupported = jpegSizes.any { size ->
            size.width == config.mjpegSize.width && size.height == config.mjpegSize.height
        }
        if (config.mjpegPipeline == MjpegPipeline.CAMERA_JPEG) {
            return if (cameraJpegSupported) {
                MjpegPipeline.CAMERA_JPEG
            } else {
                onStatus("Camera JPEG output unsupported at ${config.mjpegSize.width}x${config.mjpegSize.height}; using YUV")
                MjpegPipeline.YUV_JPEG
            }
        }

        return MjpegPipeline.YUV_JPEG
    }

    fun updateRuntimeControls(
        streamQuality: Int,
        zoomRatio: Float?,
        focusMode: FocusMode,
        focusDistance: Float?,
        exposureCompensation: Int,
        torchEnabled: Boolean
    ) {
        currentConfig = currentConfig?.copy(
            jpegQuality = streamQuality,
            streamQuality = streamQuality,
            zoomRatio = zoomRatio,
            focusMode = focusMode,
            focusDistance = focusDistance,
            exposureCompensation = exposureCompensation,
            torchEnabled = torchEnabled
        )
        currentConfig?.let(::updateVideoEncoderBitrate)
        backgroundHandler?.post { rebuildRepeatingRequest() }
    }

    fun onMjpegDemandStateChanged() {
        backgroundHandler?.post { rebuildRepeatingRequest() }
    }

    private fun createSession(
        camera: CameraDevice,
        option: CameraLensOption,
        previewSurface: Surface,
        generation: Long
    ) {
        if (!isCurrentCameraStream(generation, camera)) {
            return
        }
        val readerSurface = imageReader?.surface ?: return
        val encoderSurface = encoderInputSurface
        val config = currentConfig ?: return
        activePreviewSurface = previewSurface
        activeReaderSurface = readerSurface
        activeEncoderSurface = encoderSurface
        val highSpeedFallback = highSpeedFallbackReason(config, option, encoderSurface)
        if (highSpeedFallback == null && encoderSurface != null) {
            createHighSpeedSession(camera, previewSurface, readerSurface, encoderSurface, generation)
            return
        } else if (config.highSpeedVideoEnabled) {
            updateStreamDebugState(
                sessionMode = "regular",
                sessionModeReason = highSpeedFallback,
                requestTemplate = null,
                targetSurfaces = emptyList(),
                highSpeedActive = false
            )
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && option.physicalCameraId != null) {
            runCatching {
                createPhysicalSession(camera, option.physicalCameraId, previewSurface, readerSurface, encoderSurface, generation)
            }.onFailure {
                onStatus("Physical session fallback")
                if (isCurrentCameraStream(generation, camera)) {
                    createLogicalSession(camera, previewSurface, readerSurface, encoderSurface, generation)
                }
            }
            return
        }

        createLogicalSession(camera, previewSurface, readerSurface, encoderSurface, generation)
    }

    private fun highSpeedFallbackReason(
        config: CameraStreamConfig,
        option: CameraLensOption,
        encoderSurface: Surface?
    ): String? {
        if (!config.highSpeedVideoEnabled) {
            return config.highSpeedVideoFallbackReason ?: "high-speed-not-requested"
        }
        return when {
            option.physicalCameraId != null -> "physical-camera-session-uses-regular-mode"
            encoderSurface == null -> "encoder-surface-unavailable"
            else -> null
        }
    }

    private fun createHighSpeedSession(
        camera: CameraDevice,
        previewSurface: Surface,
        readerSurface: Surface,
        encoderSurface: Surface,
        generation: Long
    ) {
        if (!isCurrentCameraStream(generation, camera)) {
            return
        }
        updateStreamDebugState(
            sessionMode = "constrained-high-speed-configuring",
            sessionModeReason = currentConfig?.let(::highSpeedSessionModeReason),
            requestTemplate = null,
            targetSurfaces = listOf("preview", "h264Encoder"),
            highSpeedActive = false
        )
        runCatching {
            camera.createConstrainedHighSpeedCaptureSession(
                listOf(previewSurface, encoderSurface),
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        if (!isCurrentCameraStream(generation, camera)) {
                            runCatching { session.close() }
                            return
                        }
                        val highSpeedSession = session as? CameraConstrainedHighSpeedCaptureSession
                        if (highSpeedSession == null) {
                            onStatus("High-speed session unavailable; using regular session")
                            runCatching { session.close() }
                            highSpeedSessionActive = false
                            updateStreamDebugState(
                                sessionMode = "regular",
                                sessionModeReason = "configured-session-not-high-speed",
                                requestTemplate = null,
                                targetSurfaces = emptyList(),
                                highSpeedActive = false
                            )
                            createLogicalSession(camera, previewSurface, readerSurface, encoderSurface, generation)
                            return
                        }
                        captureSession = session
                        startHighSpeedRepeating(camera, previewSurface, encoderSurface, highSpeedSession, generation)
                    }

                    override fun onConfigureFailed(session: CameraCaptureSession) {
                        if (!isCurrentCameraStream(generation, camera)) {
                            runCatching { session.close() }
                            return
                        }
                        onStatus("High-speed session failed; using regular session")
                        runCatching { session.close() }
                        highSpeedSessionActive = false
                        updateStreamDebugState(
                            sessionMode = "regular",
                            sessionModeReason = "high-speed-configure-failed",
                            requestTemplate = null,
                            targetSurfaces = emptyList(),
                            highSpeedActive = false
                        )
                        createLogicalSession(camera, previewSurface, readerSurface, encoderSurface, generation)
                    }
                },
                backgroundHandler
            )
        }.onFailure {
            if (!isCurrentCameraStream(generation, camera)) {
                return@onFailure
            }
            onStatus("High-speed session rejected; using regular session")
            highSpeedSessionActive = false
            updateStreamDebugState(
                sessionMode = "regular",
                sessionModeReason = "high-speed-create-rejected",
                requestTemplate = null,
                targetSurfaces = emptyList(),
                highSpeedActive = false
            )
            createLogicalSession(camera, previewSurface, readerSurface, encoderSurface, generation)
        }
    }

    private fun createLogicalSession(
        camera: CameraDevice,
        previewSurface: Surface,
        readerSurface: Surface,
        encoderSurface: Surface?,
        generation: Long
    ) {
        if (!isCurrentCameraStream(generation, camera)) {
            return
        }
        camera.createCaptureSession(
            listOfNotNull(previewSurface, readerSurface, encoderSurface),
            object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    if (!isCurrentCameraStream(generation, camera)) {
                        runCatching { session.close() }
                        return
                    }
                    captureSession = session
                    highSpeedSessionActive = false
                    startRepeating(camera, previewSurface, readerSurface, encoderSurface, generation)
                }

                override fun onConfigureFailed(session: CameraCaptureSession) {
                    if (!isCurrentCameraStream(generation, camera)) {
                        runCatching { session.close() }
                        return
                    }
                    onStatus("Failed to configure session")
                }
            },
            backgroundHandler
        )
    }

    @RequiresApi(Build.VERSION_CODES.P)
    private fun createPhysicalSession(
        camera: CameraDevice,
        physicalCameraId: String,
        previewSurface: Surface,
        readerSurface: Surface,
        encoderSurface: Surface?,
        generation: Long
    ) {
        if (!isCurrentCameraStream(generation, camera)) {
            return
        }
        val previewOutput = OutputConfiguration(previewSurface).apply {
            setPhysicalCameraId(physicalCameraId)
        }
        val readerOutput = OutputConfiguration(readerSurface).apply {
            setPhysicalCameraId(physicalCameraId)
        }
        val outputs = mutableListOf(previewOutput, readerOutput)
        if (encoderSurface != null) {
            outputs += OutputConfiguration(encoderSurface).apply {
                setPhysicalCameraId(physicalCameraId)
            }
        }

        val stateCallback = object : CameraCaptureSession.StateCallback() {
            override fun onConfigured(session: CameraCaptureSession) {
                if (!isCurrentCameraStream(generation, camera)) {
                    runCatching { session.close() }
                    return
                }
                captureSession = session
                highSpeedSessionActive = false
                startRepeating(camera, previewSurface, readerSurface, encoderSurface, generation)
            }

            override fun onConfigureFailed(session: CameraCaptureSession) {
                if (!isCurrentCameraStream(generation, camera)) {
                    runCatching { session.close() }
                    return
                }
                onStatus("Failed to configure physical session")
            }
        }

        val executor = Executors.newSingleThreadExecutor()
        sessionExecutor = executor
        val sessionConfiguration = SessionConfiguration(
            SessionConfiguration.SESSION_REGULAR,
            outputs,
            executor,
            stateCallback
        )

        camera.createCaptureSession(sessionConfiguration)
    }

    private fun startRepeating(
        camera: CameraDevice,
        previewSurface: Surface,
        readerSurface: Surface,
        encoderSurface: Surface?,
        generation: Long
    ) {
        currentSessionMode = if (encoderSurface != null) "regular-video" else "regular-mjpeg"
        currentSessionModeReason = currentConfig?.highSpeedVideoFallbackReason ?: streamDebugState.sessionModeReason
        currentRequestTemplate = "TEMPLATE_PREVIEW"
        highSpeedSessionActive = false

        rebuildRepeatingRequest(camera, generation)
        onStatus("Streaming")
    }

    private fun startHighSpeedRepeating(
        camera: CameraDevice,
        previewSurface: Surface,
        encoderSurface: Surface,
        session: CameraConstrainedHighSpeedCaptureSession,
        generation: Long
    ) {
        if (!isCurrentCameraStream(generation, camera) || captureSession !== session) {
            runCatching { session.close() }
            return
        }
        currentRequestBuilder = camera.createCaptureRequest(CameraDevice.TEMPLATE_RECORD).apply {
            addTarget(previewSurface)
            addTarget(encoderSurface)
            set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
            set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_AUTO)
            set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
        }
        currentSessionMode = "constrained-high-speed"
        currentSessionModeReason = currentConfig?.let(::highSpeedSessionModeReason)
        currentRequestTemplate = "TEMPLATE_RECORD"
        currentTargetSurfaces = listOf("preview", "h264Encoder")
        highSpeedSessionActive = true

        if (rebuildRepeatingRequest(camera, generation)) {
            onStatus("Streaming H.264 high-speed")
        }
    }

    private fun rebuildRepeatingRequest(
        capturedCamera: CameraDevice? = cameraDevice,
        capturedGeneration: Long = streamGeneration.get()
    ): Boolean {
        val session = captureSession ?: return false
        val config = currentConfig ?: return false
        if (!highSpeedSessionActive) {
            val previewSurface = activePreviewSurface ?: return false
            val readerSurface = activeReaderSurface
            val encoderSurface = activeEncoderSurface
            val includeReaderSurface = readerSurface != null && mjpegConsumerActive()
            currentRequestBuilder = cameraDeviceForRequest(capturedCamera)?.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)?.apply {
                addTarget(previewSurface)
                if (includeReaderSurface) {
                    val activeReaderSurface = readerSurface ?: return false
                    addTarget(activeReaderSurface)
                }
                if (encoderSurface != null) {
                    addTarget(encoderSurface)
                }
                set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
                set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_AUTO)
                set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
            } ?: return false
            currentRequestTemplate = "TEMPLATE_PREVIEW"
            currentTargetSurfaces = listOfNotNull(
                "preview",
                if (includeReaderSurface) "mjpegImageReader" else null,
                encoderSurface?.let { "h264Encoder" }
            )
        }
        val activeBuilder = currentRequestBuilder ?: return false
        applyControls(activeBuilder, config, currentCharacteristics)

        val highSpeedSession = if (highSpeedSessionActive) {
            session as? CameraConstrainedHighSpeedCaptureSession
        } else {
            null
        }

        if (highSpeedSession != null) {
            val highSpeedRequests = try {
                highSpeedSession.createHighSpeedRequestList(activeBuilder.build())
            } catch (exception: Exception) {
                handleHighSpeedRepeatingFailure(
                    "high-speed-request-list-failed",
                    exception,
                    session,
                    capturedCamera,
                    capturedGeneration
                )
                return false
            }
            return runCatching {
                highSpeedSession.setRepeatingBurst(highSpeedRequests, null, backgroundHandler)
                updateStreamDebugState(
                    sessionMode = currentSessionMode,
                    sessionModeReason = currentSessionModeReason,
                    requestTemplate = currentRequestTemplate,
                    targetSurfaces = currentTargetSurfaces,
                    highSpeedActive = true
                )
            }.onFailure {
                handleHighSpeedRepeatingFailure(
                    "high-speed-repeating-burst-failed",
                    it,
                    session,
                    capturedCamera,
                    capturedGeneration
                )
            }.isSuccess
        }

        return runCatching {
            session.setRepeatingRequest(activeBuilder.build(), null, backgroundHandler)
            updateStreamDebugState(
                sessionMode = currentSessionMode,
                sessionModeReason = currentSessionModeReason,
                requestTemplate = currentRequestTemplate,
                targetSurfaces = currentTargetSurfaces,
                highSpeedActive = false
            )
        }.onFailure {
            onStatus("Failed to update request: ${it.javaClass.simpleName}")
        }.isSuccess
    }

    private fun cameraDeviceForRequest(explicit: CameraDevice?): CameraDevice? {
        return explicit ?: cameraDevice
    }

    private fun handleHighSpeedRepeatingFailure(
        reason: String,
        exception: Throwable,
        expectedSession: CameraCaptureSession,
        capturedCamera: CameraDevice?,
        capturedGeneration: Long
    ) {
        if (
            capturedCamera == null ||
            !isCurrentCameraStream(capturedGeneration, capturedCamera) ||
            captureSession !== expectedSession ||
            currentConfig == null
        ) {
            return
        }
        onStatus("High-speed request failed; using regular session: ${exception.javaClass.simpleName}")
        fallbackToRegularSession(reason, expectedSession, capturedCamera, capturedGeneration)
    }

    private fun fallbackToRegularSession(
        reason: String,
        expectedSession: CameraCaptureSession,
        capturedCamera: CameraDevice,
        capturedGeneration: Long
    ) {
        if (
            !isCurrentCameraStream(capturedGeneration, capturedCamera) ||
            captureSession !== expectedSession ||
            currentConfig == null
        ) {
            return
        }
        val previewSurface = activePreviewSurface ?: return
        val readerSurface = activeReaderSurface ?: return
        val encoderSurface = activeEncoderSurface
        if (
            !isCurrentCameraStream(capturedGeneration, capturedCamera) ||
            captureSession !== expectedSession
        ) {
            return
        }
        runCatching { expectedSession.stopRepeating() }
        runCatching { expectedSession.close() }
        if (captureSession === expectedSession) {
            captureSession = null
        } else {
            return
        }
        highSpeedSessionActive = false
        updateStreamDebugState(
            sessionMode = "regular",
            sessionModeReason = reason,
            requestTemplate = null,
            targetSurfaces = emptyList(),
            highSpeedActive = false
        )
        if (
            isCurrentCameraStream(capturedGeneration, capturedCamera) &&
            captureSession == null
        ) {
            createLogicalSession(capturedCamera, previewSurface, readerSurface, encoderSurface, capturedGeneration)
        }
    }

    private fun isCurrentStreamGeneration(generation: Long): Boolean {
        return streamGeneration.get() == generation && currentConfig != null
    }

    private fun isCurrentCameraStream(generation: Long, camera: CameraDevice): Boolean {
        return isCurrentStreamGeneration(generation) && cameraDevice === camera
    }

    private fun applyControls(
        builder: CaptureRequest.Builder,
        config: CameraStreamConfig,
        characteristics: CameraCharacteristics?
    ) {
        effectiveTargetFpsRange(config, highSpeedSessionActive)
            ?.let { builder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, it) }
        builder.set(CaptureRequest.JPEG_QUALITY, config.jpegQuality.coerceIn(25, 92).toByte())
        applyVideoStabilization(builder, characteristics)
        applyZoom(builder, config.zoomRatio, characteristics)
        applyExposure(builder, config.exposureCompensation, characteristics)
        applyFocus(builder, config, characteristics)
        applyTorch(builder, config.torchEnabled, characteristics)
    }

    private fun applyVideoStabilization(
        builder: CaptureRequest.Builder,
        characteristics: CameraCharacteristics?
    ) {
        val modes = characteristics
            ?.get(CameraCharacteristics.CONTROL_AVAILABLE_VIDEO_STABILIZATION_MODES)
            ?.toList()
            .orEmpty()
        if (CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_OFF in modes) {
            builder.set(
                CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE,
                CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_OFF
            )
        }
    }

    private fun applyZoom(
        builder: CaptureRequest.Builder,
        zoomRatio: Float?,
        characteristics: CameraCharacteristics?
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val zoomRatioRange = characteristics?.get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE)
            if (zoomRatioRange != null && zoomRatio != null) {
                builder.set(
                    CaptureRequest.CONTROL_ZOOM_RATIO,
                    zoomRatio.coerceIn(zoomRatioRange.lower, zoomRatioRange.upper)
                )
                return
            }
        }
        applyCropZoomFallback(builder, zoomRatio, characteristics)
    }

    private fun applyExposure(
        builder: CaptureRequest.Builder,
        exposureCompensation: Int,
        characteristics: CameraCharacteristics?
    ) {
        val exposureRange = characteristics?.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE)
        if (exposureRange != null) {
            builder.set(
                CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION,
                exposureCompensation.coerceIn(exposureRange.lower, exposureRange.upper)
            )
        }
    }

    private fun applyFocus(
        builder: CaptureRequest.Builder,
        config: CameraStreamConfig,
        characteristics: CameraCharacteristics?
    ) {
        val availableModes = characteristics
            ?.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES)
            ?.toList()
            .orEmpty()

        when (config.focusMode) {
            FocusMode.AUTO -> {
                val afMode = when {
                    CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO in availableModes -> CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO
                    CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE in availableModes -> CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE
                    CaptureRequest.CONTROL_AF_MODE_AUTO in availableModes -> CaptureRequest.CONTROL_AF_MODE_AUTO
                    else -> CaptureRequest.CONTROL_AF_MODE_OFF
                }
                builder.set(CaptureRequest.CONTROL_AF_MODE, afMode)
                builder.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_IDLE)
            }

            FocusMode.MANUAL -> {
                val manualAllowed = CaptureRequest.CONTROL_AF_MODE_OFF in availableModes
                builder.set(
                    CaptureRequest.CONTROL_AF_MODE,
                    if (manualAllowed) CaptureRequest.CONTROL_AF_MODE_OFF else CaptureRequest.CONTROL_AF_MODE_AUTO
                )
                builder.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_IDLE)
                if (manualAllowed) {
                    builder.set(CaptureRequest.LENS_FOCUS_DISTANCE, config.focusDistance ?: 0f)
                }
            }
        }
    }

    private fun applyTorch(
        builder: CaptureRequest.Builder,
        torchEnabled: Boolean,
        characteristics: CameraCharacteristics?
    ) {
        val flashAvailable = characteristics?.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        if (!flashAvailable) {
            builder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF)
            return
        }
        builder.set(
            CaptureRequest.FLASH_MODE,
            if (torchEnabled) CaptureRequest.FLASH_MODE_TORCH else CaptureRequest.FLASH_MODE_OFF
        )
    }

    private fun updateStreamDebugState(
        sessionMode: String,
        sessionModeReason: String?,
        requestTemplate: String?,
        targetSurfaces: List<String>,
        highSpeedActive: Boolean
    ) {
        val config = currentConfig
        val characteristics = currentCharacteristics
        val builder = currentRequestBuilder
        streamDebugState = CameraStreamDebugState(
            cameraId = config?.option?.logicalCameraId,
            physicalCameraId = config?.option?.physicalCameraId,
            outputSize = config?.outputSize?.let(::sizeLabel),
            mjpegSize = config?.mjpegSize?.let(::sizeLabel),
            targetFps = config?.targetFps,
            targetFpsRange = config?.let { effectiveTargetFpsRange(it, highSpeedActive) }?.let(::rangeLabel),
            h264Enabled = config?.h264Enabled == true,
            mjpegPipeline = activeMjpegPipeline.controlValue,
            highSpeedRequested = config?.highSpeedVideoEnabled == true,
            highSpeedActive = highSpeedActive,
            sessionMode = sessionMode,
            sessionModeReason = preservedSessionModeReason(sessionMode, sessionModeReason),
            requestTemplate = requestTemplate,
            targetSurfaces = targetSurfaces,
            requestedZoomRatio = config?.zoomRatio,
            appliedZoomRatio = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                builder?.get(CaptureRequest.CONTROL_ZOOM_RATIO)
            } else {
                null
            },
            cropRegion = builder?.get(CaptureRequest.SCALER_CROP_REGION)?.let(::rectLabel),
            activeArraySize = characteristics
                ?.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE)
                ?.let(::rectLabel),
            videoStabilizationMode = builder
                ?.get(CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE)
                ?.let(::videoStabilizationLabel)
        )
    }

    private fun applyCropZoomFallback(
        builder: CaptureRequest.Builder,
        zoomRatio: Float?,
        characteristics: CameraCharacteristics?
    ) {
        val rect = characteristics?.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE) ?: return
        val ratio = zoomRatio ?: 1f
        if (ratio <= 1f) {
            builder.set(CaptureRequest.SCALER_CROP_REGION, rect)
            return
        }

        val cropWidth = (rect.width() / ratio).toInt().coerceAtLeast(1)
        val cropHeight = (rect.height() / ratio).toInt().coerceAtLeast(1)
        val left = rect.left + (rect.width() - cropWidth) / 2
        val top = rect.top + (rect.height() - cropHeight) / 2
        builder.set(
            CaptureRequest.SCALER_CROP_REGION,
            Rect(left, top, left + cropWidth, top + cropHeight)
        )
    }

    private fun effectiveTargetFpsRange(config: CameraStreamConfig, highSpeedActive: Boolean): Range<Int>? {
        return if (highSpeedActive) {
            config.targetFpsRange
        } else {
            config.regularFallbackFpsRange ?: config.targetFpsRange
        }
    }

    private fun highSpeedSessionModeReason(config: CameraStreamConfig): String? {
        val range = config.targetFpsRange ?: return null
        return if (range.lower != range.upper) "high-speed-variable-range" else null
    }

    private fun preservedSessionModeReason(sessionMode: String, sessionModeReason: String?): String? {
        return sessionModeReason ?: streamDebugState.sessionModeReason.takeIf { sessionMode.startsWith("regular") }
    }

    private fun sizeLabel(size: android.util.Size): String = "${size.width}x${size.height}"

    private fun rangeLabel(range: android.util.Range<Int>): String = "${range.lower}-${range.upper}"

    private fun rectLabel(rect: Rect): String = "${rect.left},${rect.top},${rect.right},${rect.bottom}"

    private fun videoStabilizationLabel(mode: Int): String {
        return when (mode) {
            CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_OFF -> "OFF"
            CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_ON -> "ON"
            else -> "UNKNOWN_$mode"
        }
    }

    private fun startThread() {
        backgroundThread = HandlerThread("CameraStreamer").also { thread ->
            thread.start()
            backgroundHandler = Handler(thread.looper)
        }
    }

    private fun stopThread() {
        backgroundThread?.quitSafely()
        backgroundThread?.join()
        backgroundThread = null
        backgroundHandler = null
    }

    private data class JpegEncodeResult(
        val bytes: ByteArray,
        val rotationAppliedBeforeEncodeDegrees: Int,
        val yuvToNv21DurationMs: Long,
        val jpegCompressDurationMs: Long,
        val overlayAppliedBeforeEncode: Boolean,
        val overlayRenderDurationMs: Long
    )

    private sealed class CopiedFrame {
        abstract val width: Int
        abstract val height: Int

        data class Yuv(
            val nv21: ByteArray,
            override val width: Int,
            override val height: Int,
            val yuvToNv21DurationMs: Long
        ) : CopiedFrame()

        data class CameraJpeg(
            val bytes: ByteArray,
            override val width: Int,
            override val height: Int
        ) : CopiedFrame()
    }

    private fun copyCapturedFrame(image: Image, pipeline: MjpegPipeline): CopiedFrame {
        return when (pipeline) {
            MjpegPipeline.YUV_JPEG -> {
                val convertStartedAtMs = SystemClock.elapsedRealtime()
                val nv21 = yuv420888ToNv21(image)
                CopiedFrame.Yuv(
                    nv21 = nv21,
                    width = image.width,
                    height = image.height,
                    yuvToNv21DurationMs = SystemClock.elapsedRealtime() - convertStartedAtMs
                )
            }

            MjpegPipeline.CAMERA_JPEG -> CopiedFrame.CameraJpeg(
                bytes = copyCameraJpegBytes(image),
                width = image.width,
                height = image.height
            )
        }
    }

    private fun copyCameraJpegBytes(image: Image): ByteArray {
        val buffer = image.planes.firstOrNull()?.buffer?.duplicate()
        if (buffer == null) return ByteArray(0)
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        return bytes
    }

    private fun cameraJpegBytesToResult(bytes: ByteArray): JpegEncodeResult {
        return JpegEncodeResult(
            bytes = bytes,
            rotationAppliedBeforeEncodeDegrees = 0,
            yuvToNv21DurationMs = 0,
            jpegCompressDurationMs = 0,
            overlayAppliedBeforeEncode = false,
            overlayRenderDurationMs = 0
        )
    }

    private fun yuv420ToJpegFromNv21(
        nv21: ByteArray,
        width: Int,
        height: Int,
        yuvToNv21DurationMs: Long,
        jpegQuality: Int,
        outputRotationDegrees: Int,
        overlayStatus: VideoOverlayStatus
    ): JpegEncodeResult {
        val normalizedOutputRotation = normalizeOutputRotationDegrees(outputRotationDegrees)
        val rotationAppliedBeforeEncode = if (normalizedOutputRotation == 180) {
            rotateNv21By180InPlace(nv21, width, height)
            180
        } else {
            0
        }
        var overlayAppliedBeforeEncode = false
        var overlayRenderDurationMs = 0L
        if (overlayStatus.enabled && (normalizedOutputRotation == 0 || normalizedOutputRotation == 180)) {
            val timestamp = synchronized(overlayTimeFormat) {
                overlayTimeFormat.format(Date())
            }
            val overlayStartedAtMs = SystemClock.elapsedRealtime()
            runCatching {
                nv21OverlayRenderer.draw(
                    nv21 = nv21,
                    width = width,
                    height = height,
                    timestamp = timestamp,
                    status = overlayStatus
                )
            }.onSuccess {
                overlayAppliedBeforeEncode = true
                overlayRenderDurationMs = SystemClock.elapsedRealtime() - overlayStartedAtMs
            }
        }
        val yuvImage = YuvImage(nv21, ImageFormat.NV21, width, height, null)
        val compressStartedAtMs = SystemClock.elapsedRealtime()
        val jpeg = ByteArrayOutputStream().use { output ->
            yuvImage.compressToJpeg(
                Rect(0, 0, width, height),
                jpegQuality.coerceIn(25, 92),
                output
            )
            output.toByteArray()
        }
        return JpegEncodeResult(
            bytes = jpeg,
            rotationAppliedBeforeEncodeDegrees = rotationAppliedBeforeEncode,
            yuvToNv21DurationMs = yuvToNv21DurationMs,
            jpegCompressDurationMs = SystemClock.elapsedRealtime() - compressStartedAtMs,
            overlayAppliedBeforeEncode = overlayAppliedBeforeEncode,
            overlayRenderDurationMs = overlayRenderDurationMs
        )
    }

    private fun yuv420888ToNv21(image: Image): ByteArray {
        val width = image.width
        val height = image.height
        val ySize = width * height
        val uvSize = width * height / 2
        val out = ByteArray(ySize + uvSize)

        val yBuffer = image.planes[0].buffer.duplicate()
        val uBuffer = image.planes[1].buffer.duplicate()
        val vBuffer = image.planes[2].buffer.duplicate()

        val yRowStride = image.planes[0].rowStride
        val uvRowStride = image.planes[1].rowStride
        val uvPixelStride = image.planes[1].pixelStride

        if (yRowStride == width) {
            yBuffer.position(0)
            yBuffer.get(out, 0, ySize)
        } else {
            var position = 0
            for (row in 0 until height) {
                yBuffer.position(row * yRowStride)
                yBuffer.get(out, position, width)
                position += width
            }
        }

        var uvPosition = ySize
        val uvWidth = width / 2
        val uvHeight = height / 2
        val uRow = ByteArray(uvRowStride)
        val vRow = ByteArray(uvRowStride)
        for (row in 0 until uvHeight) {
            val rowStart = row * uvRowStride
            val rowLength = minOf(uvRowStride, uBuffer.limit() - rowStart, vBuffer.limit() - rowStart)
            uBuffer.position(rowStart)
            vBuffer.position(rowStart)
            uBuffer.get(uRow, 0, rowLength)
            vBuffer.get(vRow, 0, rowLength)
            for (col in 0 until uvWidth) {
                val offset = col * uvPixelStride
                out[uvPosition++] = vRow[offset]
                out[uvPosition++] = uRow[offset]
            }
        }

        return out
    }

    private fun rotateNv21By180InPlace(nv21: ByteArray, width: Int, height: Int) {
        val ySize = width * height
        var yStart = 0
        var yEnd = ySize - 1
        while (yStart < yEnd) {
            val temp = nv21[yStart]
            nv21[yStart] = nv21[yEnd]
            nv21[yEnd] = temp
            yStart += 1
            yEnd -= 1
        }

        var uvStart = ySize
        var uvEnd = nv21.size - 2
        while (uvStart < uvEnd) {
            val leftV = nv21[uvStart]
            val leftU = nv21[uvStart + 1]
            nv21[uvStart] = nv21[uvEnd]
            nv21[uvStart + 1] = nv21[uvEnd + 1]
            nv21[uvEnd] = leftV
            nv21[uvEnd + 1] = leftU
            uvStart += 2
            uvEnd -= 2
        }
    }

    private data class OutputTransformResult(
        val bytes: ByteArray,
        val transformDurationMs: Long,
        val overlayRenderDurationMs: Long
    )

    private fun applyOutputTransformIfNeeded(
        sourceJpeg: ByteArray,
        rotationDegrees: Int,
        jpegQuality: Int,
        overlayStatus: VideoOverlayStatus
    ): OutputTransformResult {
        if (sourceJpeg.isEmpty()) {
            return OutputTransformResult(sourceJpeg, 0, 0)
        }
        val transformStartedAtMs = SystemClock.elapsedRealtime()
        val normalizedRotation = normalizeOutputRotationDegrees(rotationDegrees)
        if (normalizedRotation == 0 && !overlayStatus.enabled) {
            return OutputTransformResult(sourceJpeg, 0, 0)
        }
        return runCatching {
            val decodeOptions = BitmapFactory.Options().apply {
                inMutable = overlayStatus.enabled
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val decoded = BitmapFactory.decodeByteArray(sourceJpeg, 0, sourceJpeg.size, decodeOptions)
                ?: return OutputTransformResult(sourceJpeg, 0, 0)
            val rotatedBitmap = if (normalizedRotation == 0) {
                decoded
            } else {
                Bitmap.createBitmap(
                    decoded,
                    0,
                    0,
                    decoded.width,
                    decoded.height,
                    Matrix().apply { postRotate(normalizedRotation.toFloat()) },
                    true
                )
            }
            if (rotatedBitmap !== decoded) {
                decoded.recycle()
            }

            var overlayRenderDurationMs = 0L
            val targetBitmap = if (overlayStatus.enabled) {
                val mutableBitmap = if (rotatedBitmap.isMutable) {
                    rotatedBitmap
                } else {
                    val copy = rotatedBitmap.copy(Bitmap.Config.ARGB_8888, true) ?: run {
                        if (rotatedBitmap !== decoded) {
                            rotatedBitmap.recycle()
                        } else {
                            decoded.recycle()
                        }
                        return OutputTransformResult(sourceJpeg, 0, 0)
                    }
                    if (copy !== rotatedBitmap) {
                        rotatedBitmap.recycle()
                    }
                    copy
                }

                val canvas = Canvas(mutableBitmap)
                val timestamp = synchronized(overlayTimeFormat) {
                    overlayTimeFormat.format(Date())
                }
                val overlayStartedAtMs = SystemClock.elapsedRealtime()
                overlayRenderer.draw(
                    canvas = canvas,
                    background = mutableBitmap,
                    timestamp = timestamp,
                    status = overlayStatus
                )
                overlayRenderDurationMs = SystemClock.elapsedRealtime() - overlayStartedAtMs
                mutableBitmap
            } else {
                rotatedBitmap
            }

            val encoded = ByteArrayOutputStream().use { output ->
                targetBitmap.compress(
                    Bitmap.CompressFormat.JPEG,
                    jpegQuality.coerceIn(25, 92),
                    output
                )
                output.toByteArray()
            }
            targetBitmap.recycle()
            OutputTransformResult(
                bytes = encoded,
                transformDurationMs = SystemClock.elapsedRealtime() - transformStartedAtMs,
                overlayRenderDurationMs = overlayRenderDurationMs
            )
        }.getOrElse {
            OutputTransformResult(sourceJpeg, 0, 0)
        }
    }

    private fun normalizeOutputRotationDegrees(rotationDegrees: Int): Int {
        val normalized = ((rotationDegrees % 360) + 360) % 360
        return when (normalized) {
            0, 90, 180, 270 -> normalized
            else -> 0
        }
    }

    private fun startVideoEncoder(config: CameraStreamConfig) {
        val width = config.outputSize.width
        val height = config.outputSize.height
        val bitrate = calculateBitrate(width, height, config.targetFps, config.streamQuality)
        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
            setInteger(MediaFormat.KEY_FRAME_RATE, config.targetFps)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            setInteger(MediaFormat.KEY_BITRATE_MODE, MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                setInteger(MediaFormat.KEY_PREPEND_HEADER_TO_SYNC_FRAMES, 1)
            }
        }

        val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        encoderInputSurface = codec.createInputSurface()
        codec.start()
        videoEncoder = codec
        encoderRunning.set(true)
        encoderDrainExecutor = Executors.newSingleThreadExecutor().also { executor ->
            executor.execute { drainVideoEncoder(codec) }
        }
        onStatus("H.264 encoder ready (${bitrate / 1_000_000.0} Mbps)")
    }

    private fun updateVideoEncoderBitrate(config: CameraStreamConfig) {
        val codec = videoEncoder ?: return
        val bitrate = calculateBitrate(
            width = config.outputSize.width,
            height = config.outputSize.height,
            fps = config.targetFps,
            quality = config.streamQuality
        )
        runCatching {
            codec.setParameters(Bundle().apply {
                putInt(MediaCodec.PARAMETER_KEY_VIDEO_BITRATE, bitrate)
            })
            onStatus("Updated H.264 bitrate (${bitrate / 1_000_000.0} Mbps)")
        }
    }

    private fun stopVideoEncoder() {
        encoderRunning.set(false)
        runCatching { encoderDrainExecutor?.shutdownNow() }
        encoderDrainExecutor = null
        runCatching { encoderInputSurface?.release() }
        encoderInputSurface = null
        runCatching { videoEncoder?.stop() }
        runCatching { videoEncoder?.release() }
        videoEncoder = null
    }

    private fun drainVideoEncoder(codec: MediaCodec) {
        val bufferInfo = MediaCodec.BufferInfo()
        while (encoderRunning.get()) {
            val index = try {
                codec.dequeueOutputBuffer(bufferInfo, 20_000)
            } catch (_: Exception) {
                break
            }

            when {
                index == MediaCodec.INFO_TRY_AGAIN_LATER -> Unit
                index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    val codecConfig = buildCodecConfig(codec.outputFormat)
                    if (codecConfig != null) {
                        onH264AccessUnit(
                            H264AccessUnit(
                                bytes = codecConfig,
                                isKeyFrame = false,
                                isCodecConfig = true
                            )
                        )
                    }
                }

                index >= 0 -> {
                    val outputBuffer = codec.getOutputBuffer(index)
                    if (outputBuffer != null && bufferInfo.size > 0) {
                        outputBuffer.position(bufferInfo.offset)
                        outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                        val bytes = ByteArray(bufferInfo.size)
                        outputBuffer.get(bytes)
                        val annexB = toAnnexB(bytes)
                        val isCodecConfig =
                            (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0
                        val isKeyFrame =
                            (bufferInfo.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0
                        if (annexB.isNotEmpty()) {
                            onH264AccessUnit(
                                H264AccessUnit(
                                    bytes = annexB,
                                    isKeyFrame = isKeyFrame,
                                    isCodecConfig = isCodecConfig
                                )
                            )
                        }
                    }
                    codec.releaseOutputBuffer(index, false)
                }
            }
        }
    }

    private fun buildCodecConfig(format: MediaFormat): ByteArray? {
        val csd0 = format.getByteBuffer("csd-0")?.let { byteBufferToArray(it) }
        val csd1 = format.getByteBuffer("csd-1")?.let { byteBufferToArray(it) }
        if (csd0 == null && csd1 == null) {
            return null
        }
        val output = ByteArrayOutputStream()
        csd0?.let { output.write(withStartCode(it)) }
        csd1?.let { output.write(withStartCode(it)) }
        return output.toByteArray()
    }

    private fun toAnnexB(sample: ByteArray): ByteArray {
        if (sample.size >= 4 &&
            sample[0] == 0.toByte() &&
            sample[1] == 0.toByte() &&
            ((sample[2] == 0.toByte() && sample[3] == 1.toByte()) || sample[2] == 1.toByte())
        ) {
            return sample
        }

        val output = ByteArrayOutputStream(sample.size + 32)
        var offset = 0
        while (offset + 4 <= sample.size) {
            val length = ((sample[offset].toInt() and 0xFF) shl 24) or
                ((sample[offset + 1].toInt() and 0xFF) shl 16) or
                ((sample[offset + 2].toInt() and 0xFF) shl 8) or
                (sample[offset + 3].toInt() and 0xFF)
            offset += 4
            if (length <= 0 || offset + length > sample.size) {
                return withStartCode(sample)
            }
            output.write(byteArrayOf(0, 0, 0, 1))
            output.write(sample, offset, length)
            offset += length
        }
        return output.toByteArray()
    }

    private fun withStartCode(bytes: ByteArray): ByteArray {
        return if (bytes.size >= 4 &&
            bytes[0] == 0.toByte() &&
            bytes[1] == 0.toByte() &&
            ((bytes[2] == 0.toByte() && bytes[3] == 1.toByte()) || bytes[2] == 1.toByte())
        ) {
            bytes
        } else {
            byteArrayOf(0, 0, 0, 1) + bytes
        }
    }

    private fun byteBufferToArray(buffer: java.nio.ByteBuffer): ByteArray {
        val duplicate = buffer.duplicate()
        duplicate.clear()
        val bytes = ByteArray(duplicate.remaining())
        duplicate.get(bytes)
        return bytes
    }

    private fun calculateBitrate(width: Int, height: Int, fps: Int, quality: Int): Int {
        val normalized = quality.coerceIn(30, 95)
        val base = width.toLong() * height.toLong() * fps.toLong()
        val scale = 0.18 + (normalized.toDouble() / 100.0) * 0.48
        val bitrate = (base * scale).toInt()
        return bitrate.coerceIn(700_000, 8_000_000)
    }
}
