package com.opencode.multilensipcam

import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.util.Range
import android.view.Surface
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class CameraOpenProbe(private val cameraManager: CameraManager) {
    fun run(request: CameraOpenProbeRequest): CameraOpenProbeResult {
        val startedAtMs = SystemClock.elapsedRealtime()
        val events = mutableListOf<String>()
        var cameraDevice: CameraDevice? = null
        var session: CameraCaptureSession? = null
        var imageReader: ImageReader? = null
        var surfaceTexture: SurfaceTexture? = null
        var surfaces: List<Surface> = emptyList()
        val frames = AtomicInteger(0)
        val thread = HandlerThread("CameraOpenProbe-${request.cameraId}-${request.mode}").also { it.start() }
        val handler = Handler(thread.looper)

        var characteristicsReadable = false
        var opened = false
        var configured = false
        var repeating = false
        var error: String? = null

        try {
            val characteristics = try {
                cameraManager.getCameraCharacteristics(request.cameraId).also {
                    characteristicsReadable = true
                    events += "characteristics"
                }
            } catch (throwable: Throwable) {
                error = errorLabel(throwable)
                return buildResult(
                    request = request,
                    startedAtMs = startedAtMs,
                    characteristicsReadable = false,
                    opened = false,
                    configured = false,
                    repeating = false,
                    frames = 0,
                    events = events,
                    error = error
                )
            }

            val openLatch = CountDownLatch(1)
            val doneLatch = CountDownLatch(1)

            try {
                cameraManager.openCamera(
                    request.cameraId,
                    object : CameraDevice.StateCallback() {
                        override fun onOpened(camera: CameraDevice) {
                            cameraDevice = camera
                            opened = true
                            events += "opened"
                            openLatch.countDown()
                            if (request.mode == CameraOpenProbeMode.OPEN_ONLY) {
                                doneLatch.countDown()
                            } else {
                                configureSession(
                                    camera = camera,
                                    characteristics = characteristics,
                                    request = request,
                                    handler = handler,
                                    frames = frames,
                                    events = events,
                                    onResources = { reader, texture, createdSurfaces ->
                                        imageReader = reader
                                        surfaceTexture = texture
                                        surfaces = createdSurfaces
                                    },
                                    onConfigured = { createdSession ->
                                        session = createdSession
                                        configured = true
                                        events += "configured"
                                        startRepeating(
                                            session = createdSession,
                                            camera = camera,
                                            targets = surfaces,
                                            characteristics = characteristics,
                                            request = request,
                                            handler = handler,
                                            onRepeating = {
                                                repeating = true
                                                events += "repeating"
                                            }
                                        )
                                        handler.postDelayed({ doneLatch.countDown() }, request.durationMs.toLong())
                                    },
                                    onError = { reason ->
                                        error = reason
                                        doneLatch.countDown()
                                    }
                                )
                            }
                        }

                        override fun onDisconnected(camera: CameraDevice) {
                            events += "disconnected"
                            camera.close()
                            openLatch.countDown()
                            doneLatch.countDown()
                        }

                        override fun onError(camera: CameraDevice, cameraError: Int) {
                            error = "CameraDevice error $cameraError"
                            events += "camera-error-$cameraError"
                            camera.close()
                            openLatch.countDown()
                            doneLatch.countDown()
                        }
                    },
                    handler
                )
            } catch (throwable: Throwable) {
                error = errorLabel(throwable)
                return buildResult(
                    request = request,
                    startedAtMs = startedAtMs,
                    characteristicsReadable = characteristicsReadable,
                    opened = false,
                    configured = false,
                    repeating = false,
                    frames = 0,
                    events = events,
                    error = error
                )
            }

            if (!openLatch.await(request.openTimeoutMs.toLong(), TimeUnit.MILLISECONDS)) {
                error = "Timed out waiting for camera open"
            } else {
                doneLatch.await(request.totalTimeoutMs.toLong(), TimeUnit.MILLISECONDS)
            }
        } finally {
            runCatching { session?.stopRepeating() }
            runCatching { session?.close() }
            runCatching { cameraDevice?.close() }
            runCatching { imageReader?.close() }
            surfaces.forEach { createdSurface -> runCatching { createdSurface.release() } }
            runCatching { surfaceTexture?.release() }
            thread.quitSafely()
            runCatching { thread.join(1_000) }
        }

        return buildResult(
            request = request,
            startedAtMs = startedAtMs,
            characteristicsReadable = characteristicsReadable,
            opened = opened,
            configured = configured,
            repeating = repeating,
            frames = frames.get(),
            events = events,
            error = error
        )
    }

    private fun configureSession(
        camera: CameraDevice,
        characteristics: CameraCharacteristics,
        request: CameraOpenProbeRequest,
        handler: Handler,
        frames: AtomicInteger,
        events: MutableList<String>,
        onResources: (ImageReader?, SurfaceTexture?, List<Surface>) -> Unit,
        onConfigured: (CameraCaptureSession) -> Unit,
        onError: (String) -> Unit
    ) {
        val size = chooseProbeSize(characteristics, request)
        val surfaces = when (request.mode) {
            CameraOpenProbeMode.TEXTURE -> {
                val texture = SurfaceTexture(0).apply {
                    setDefaultBufferSize(size.width, size.height)
                }
                listOf(Surface(texture)).also { onResources(null, texture, it) }
            }

            CameraOpenProbeMode.YUV -> {
                val reader = ImageReader.newInstance(size.width, size.height, ImageFormat.YUV_420_888, 2).apply {
                    setOnImageAvailableListener({ imageReader ->
                        val image = imageReader.acquireLatestImage() ?: return@setOnImageAvailableListener
                        frames.incrementAndGet()
                        image.close()
                    }, handler)
                }
                listOf(reader.surface).also { onResources(reader, null, it) }
            }

            CameraOpenProbeMode.TEXTURE_YUV -> {
                val texture = SurfaceTexture(0).apply {
                    setDefaultBufferSize(size.width, size.height)
                }
                val reader = ImageReader.newInstance(size.width, size.height, ImageFormat.YUV_420_888, 2).apply {
                    setOnImageAvailableListener({ imageReader ->
                        val image = imageReader.acquireLatestImage() ?: return@setOnImageAvailableListener
                        frames.incrementAndGet()
                        image.close()
                    }, handler)
                }
                listOf(Surface(texture), reader.surface).also { onResources(reader, texture, it) }
            }

            CameraOpenProbeMode.OPEN_ONLY -> {
                onError("OPEN_ONLY does not create a session")
                return
            }
        }

        events += "session-size-${size.width}x${size.height}"
        camera.createCaptureSession(
            surfaces,
            object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    onConfigured(session)
                }

                override fun onConfigureFailed(session: CameraCaptureSession) {
                    events += "configure-failed"
                    onError("Session configure failed")
                }
            },
            handler
        )
    }

    private fun startRepeating(
        session: CameraCaptureSession,
        camera: CameraDevice,
        targets: List<Surface>,
        characteristics: CameraCharacteristics,
        request: CameraOpenProbeRequest,
        handler: Handler,
        onRepeating: () -> Unit
    ) {
        if (targets.isEmpty()) return
        val builder = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
            targets.forEach(::addTarget)
            set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
            set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
            chooseFpsRange(characteristics, request.fps)?.let {
                set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, it)
            }
        }
        session.setRepeatingRequest(builder.build(), null, handler)
        onRepeating()
    }

    private fun chooseProbeSize(
        characteristics: CameraCharacteristics,
        request: CameraOpenProbeRequest
    ): ProbeSize {
        val streamMap = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        val sizes = when (request.mode) {
            CameraOpenProbeMode.TEXTURE -> streamMap?.getOutputSizes(SurfaceTexture::class.java)
            CameraOpenProbeMode.YUV -> streamMap?.getOutputSizes(ImageFormat.YUV_420_888)
            CameraOpenProbeMode.TEXTURE_YUV -> streamMap?.getOutputSizes(ImageFormat.YUV_420_888)
            CameraOpenProbeMode.OPEN_ONLY -> null
        }.orEmpty()
        val requestedArea = request.width.toLong() * request.height.toLong()
        val selected = sizes.minWithOrNull(
            compareBy<android.util.Size> {
                kotlin.math.abs((it.width.toLong() * it.height.toLong()) - requestedArea)
            }.thenBy {
                kotlin.math.abs(it.width - request.width) + kotlin.math.abs(it.height - request.height)
            }
        )
        return ProbeSize(
            width = selected?.width ?: request.width,
            height = selected?.height ?: request.height
        )
    }

    private fun chooseFpsRange(
        characteristics: CameraCharacteristics,
        targetFps: Int
    ): Range<Int>? {
        return characteristics
            .get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)
            ?.minByOrNull { range ->
                kotlin.math.abs(range.upper - targetFps) * 100 + kotlin.math.abs(range.lower - targetFps)
            }
    }

    private fun buildResult(
        request: CameraOpenProbeRequest,
        startedAtMs: Long,
        characteristicsReadable: Boolean,
        opened: Boolean,
        configured: Boolean,
        repeating: Boolean,
        frames: Int,
        events: List<String>,
        error: String?
    ): CameraOpenProbeResult {
        val elapsedMs = SystemClock.elapsedRealtime() - startedAtMs
        return CameraOpenProbeResult(
            cameraId = request.cameraId,
            mode = request.mode.controlValue,
            requestedWidth = request.width,
            requestedHeight = request.height,
            requestedFps = request.fps,
            durationMs = request.durationMs,
            elapsedMs = elapsedMs,
            characteristicsReadable = characteristicsReadable,
            opened = opened,
            configured = configured,
            repeating = repeating,
            frames = frames,
            events = events,
            error = error
        )
    }

    private fun errorLabel(error: Throwable): String {
        val prefix = if (error is CameraAccessException) {
            "CameraAccessException(${error.reason})"
        } else {
            error.javaClass.simpleName
        }
        val message = error.message?.take(180)
        return if (message.isNullOrBlank()) prefix else "$prefix: $message"
    }

    private data class ProbeSize(
        val width: Int,
        val height: Int
    )
}

data class CameraOpenProbeRequest(
    val cameraId: String,
    val mode: CameraOpenProbeMode,
    val width: Int = 640,
    val height: Int = 480,
    val fps: Int = 30,
    val durationMs: Int = 1_200,
    val openTimeoutMs: Int = 2_500,
    val totalTimeoutMs: Int = 5_000
)

data class CameraOpenProbeResult(
    val cameraId: String,
    val mode: String,
    val requestedWidth: Int,
    val requestedHeight: Int,
    val requestedFps: Int,
    val durationMs: Int,
    val elapsedMs: Long,
    val characteristicsReadable: Boolean,
    val opened: Boolean,
    val configured: Boolean,
    val repeating: Boolean,
    val frames: Int,
    val events: List<String>,
    val error: String?
)

enum class CameraOpenProbeMode(val controlValue: String) {
    OPEN_ONLY("open"),
    TEXTURE("texture"),
    YUV("yuv"),
    TEXTURE_YUV("dual");

    companion object {
        fun from(value: String?): CameraOpenProbeMode {
            return when (value?.lowercase()) {
                "texture", "preview" -> TEXTURE
                "yuv", "image", "imagereader" -> YUV
                "dual", "texture-yuv", "preview-yuv" -> TEXTURE_YUV
                else -> OPEN_ONLY
            }
        }
    }
}
