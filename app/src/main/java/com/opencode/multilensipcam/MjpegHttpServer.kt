package com.opencode.multilensipcam

import android.os.Build
import android.os.SystemClock
import java.io.File
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class MjpegHttpServer(
    private val port: Int,
    snapshotCacheRoot: File,
    private val stateProvider: () -> WebControlState,
    private val controlHandler: (WebControlCommand) -> Unit,
    private val h264RequestHandler: () -> Boolean,
    private val audioRequestHandler: (Boolean) -> Boolean,
    private val streamDebugProvider: () -> CameraStreamDebugState?,
    private val cameraDebugProvider: () -> CameraDebugState,
    private val cameraScanStateProvider: () -> CameraScanState,
    private val cameraScanHandler: () -> CameraScanResponse,
    private val cameraScanCacheDeleteHandler: (String?) -> CameraScanCacheDeleteResponse,
    private val cameraOpenProbeHandler: (CameraOpenProbeRequest) -> CameraOpenProbeResult,
    private val mjpegClientCountChanged: () -> Unit = {},
    private val h264ClientCountChanged: () -> Unit = {}
) {
    private val running = AtomicBoolean(false)
    private val clients = CopyOnWriteArrayList<Socket>()
    private val mediaStreams = WebMediaStreamBroker(
        snapshotCacheRoot = snapshotCacheRoot,
        h264RequestHandler = h264RequestHandler,
        runningProvider = { running.get() },
        mjpegClientCountChanged = mjpegClientCountChanged,
        h264ClientCountChanged = h264ClientCountChanged
    )
    private val controlResponses = WebControlResponses(
        stateProvider = stateProvider,
        controlHandler = controlHandler
    )
    private val cameraDebugResponses = WebCameraDebugResponses(
        stateProvider = stateProvider,
        cameraDebugProvider = cameraDebugProvider,
        cameraScanStateProvider = cameraScanStateProvider,
        cameraScanHandler = cameraScanHandler,
        cameraScanCacheDeleteHandler = cameraScanCacheDeleteHandler,
        cameraOpenProbeHandler = cameraOpenProbeHandler
    )
    private val diagnostics = HttpDiagnostics()
    private val finiteResponseGracefulCloseMaxWaitMs = 200L
    private val finiteResponseDrainPollTimeoutMs = 60

    @Volatile
    private var serverSocket: ServerSocket? = null

    @Volatile
    private var executor: ExecutorService? = null

    @Volatile
    private var serverStartedAtElapsedMs: Long = 0

    @Volatile
    private var lastKnownStreaming: Boolean? = null

    // Server lifecycle and frame fanout.

    fun start() {
        if (!running.compareAndSet(false, true)) return
        resetDebugStats()
        diagnostics.reset()
        lastKnownStreaming = null
        serverStartedAtElapsedMs = SystemClock.elapsedRealtime()
        val currentExecutor = Executors.newCachedThreadPool()
        executor = currentExecutor

        currentExecutor.execute {
            val socket = ServerSocket()
            serverSocket = socket
            socket.reuseAddress = true
            socket.bind(InetSocketAddress(port))

            while (running.get()) {
                try {
                    val client = socket.accept()
                    clients += client
                    currentExecutor.execute { handleClient(client) }
                } catch (_: Exception) {
                    if (!running.get()) {
                        return@execute
                    }
                }
            }
        }
    }

    fun stop() {
        running.set(false)
        clients.forEach { runCatching { it.close() } }
        clients.clear()
        runCatching { serverSocket?.close() }
        serverSocket = null
        executor?.shutdownNow()
        executor = null
        lastKnownStreaming = false
        mediaStreams.shutdown()
    }

    fun closeActiveStreams() {
        mediaStreams.closeActiveStreamClientsAndFrames()
    }

    fun closeAudioStreams() {
        mediaStreams.closeAudioClients()
    }

    fun updateFrame(frame: MjpegFrame) {
        mediaStreams.updateFrame(frame)
    }

    fun updateH264(accessUnit: H264AccessUnit) {
        mediaStreams.updateH264(accessUnit)
    }

    fun updateAudio(accessUnit: AacAccessUnit) {
        mediaStreams.updateAudio(accessUnit)
    }

    fun audioClientCount(): Int {
        return mediaStreams.audioClientCount
    }

    fun mjpegClientCount(): Int {
        return mediaStreams.mjpegClientCount
    }

    fun h264ClientCount(): Int {
        return mediaStreams.h264ClientCount
    }

    fun hasRecentMjpegClients(graceMs: Long): Boolean {
        return mediaStreams.hasRecentMjpegClients(graceMs)
    }

    // Request routing and dashboard shell.

    private fun handleClient(socket: Socket) {
        val requestStartedWallMs = System.currentTimeMillis()
        val requestStartedElapsedMs = SystemClock.elapsedRealtime()
        var trace: HttpDiagnostics.Trace? = null
        var countedOutput: HttpDiagnostics.CountingOutputStream? = null
        var streamRequest = false
        var recorded = false
        var shouldGracefullyFinishFiniteResponse = false

        fun diagnosticOutput(): OutputStream {
            val current = countedOutput
            if (current != null) return current
            return HttpDiagnostics.CountingOutputStream(socket.getOutputStream(), trace).also {
                countedOutput = it
            }
        }

        try {
            socket.soTimeout = 15_000
            val reader = socket.getInputStream().bufferedReader()
            val requestLine = reader.readLine() ?: return
            val rawTarget = requestLine.split(" ").getOrNull(1) ?: "/"
            val target = rawTarget.substringBefore("?")
            val query = WebHttpQueryParser.parse(rawTarget.substringAfter("?", ""))
            while (reader.readLine()?.isNotEmpty() == true) {
                // Drain request headers.
            }

            val frozenSnapshotId = WebHttpRoutes.frozenSnapshotId(target)
            if (frozenSnapshotId != null) {
                val currentTrace = diagnostics.begin(
                    startedWallTimeMs = requestStartedWallMs,
                    startedElapsedMs = requestStartedElapsedMs,
                    remoteAddress = socket.remoteSocketAddress?.toString().orEmpty(),
                    target = rawTarget,
                    route = "FROZEN_SNAPSHOT"
                )
                trace = currentTrace
                diagnostics.recordStart(currentTrace)
                mediaStreams.writeFrozenSnapshot(diagnosticOutput(), frozenSnapshotId)
                diagnostics.recordComplete(currentTrace, countedOutput, failure = null)
                recorded = true
                shouldGracefullyFinishFiniteResponse = true
                return
            }

            val route = WebHttpRoutes.resolve(target)
            val currentTrace = diagnostics.begin(
                startedWallTimeMs = requestStartedWallMs,
                startedElapsedMs = requestStartedElapsedMs,
                remoteAddress = socket.remoteSocketAddress?.toString().orEmpty(),
                target = rawTarget,
                route = route.name
            )
            trace = currentTrace
            diagnostics.recordStart(currentTrace)

            when (route) {
                WebHttpRoute.INDEX -> writeIndex(diagnosticOutput())
                WebHttpRoute.DASHBOARD_BODY_ASSET -> writeDashboardBodyAsset(diagnosticOutput(), target)
                WebHttpRoute.DASHBOARD_CSS_ASSET -> writeDashboardCssAsset(diagnosticOutput(), target)
                WebHttpRoute.DASHBOARD_JS_ASSET -> writeDashboardJsAsset(diagnosticOutput(), target)
                WebHttpRoute.SNAPSHOT -> mediaStreams.writeSnapshot(diagnosticOutput())
                WebHttpRoute.MJPEG -> recordStreamRequest(currentTrace, recordedSetter = { recorded = it }) {
                    streamRequest = true
                    mediaStreams.writeMjpeg(socket)
                }
                WebHttpRoute.H264 -> recordStreamRequest(currentTrace, recordedSetter = { recorded = it }) {
                    streamRequest = true
                    mediaStreams.writeH264(socket)
                }
                WebHttpRoute.AUDIO -> recordStreamRequest(currentTrace, recordedSetter = { recorded = it }) {
                    streamRequest = true
                    mediaStreams.writeAudio(socket, audioRequestHandler, query["wait"] == "1")
                }
                WebHttpRoute.STATE -> controlResponses.writeState(diagnosticOutput())
                WebHttpRoute.DEBUG_STREAM -> writeDebugStream(diagnosticOutput())
                WebHttpRoute.DEBUG_DIAGNOSTICS -> writeDiagnostics(diagnosticOutput())
                WebHttpRoute.DEBUG_CAMERAS -> cameraDebugResponses.writeDebugCameras(diagnosticOutput())
                WebHttpRoute.CAMERA_SCAN_CACHE -> cameraDebugResponses.writeCameraScanState(diagnosticOutput())
                WebHttpRoute.CAMERA_SCAN_CACHE_DELETE -> cameraDebugResponses.writeCameraScanCacheDelete(diagnosticOutput(), query)
                WebHttpRoute.CAMERA_SCAN -> cameraDebugResponses.writeCameraScan(diagnosticOutput())
                WebHttpRoute.CAMERA_OPEN -> cameraDebugResponses.writeCameraOpenProbe(diagnosticOutput(), query)
                WebHttpRoute.CAMERA_OPEN_MATRIX -> cameraDebugResponses.writeCameraOpenMatrix(diagnosticOutput(), query)
                WebHttpRoute.CONTROL -> controlResponses.handleControl(diagnosticOutput(), query)
                WebHttpRoute.NOT_FOUND -> WebHttpResponses.writeNotFound(diagnosticOutput())
            }
            if (!streamRequest) {
                diagnostics.recordComplete(currentTrace, countedOutput, failure = null)
                recorded = true
                shouldGracefullyFinishFiniteResponse = true
            }
        } catch (exception: Exception) {
            // Client disconnected or sent an incomplete request.
            if (!recorded) {
                trace?.let { currentTrace ->
                    if (streamRequest) {
                        diagnostics.recordEnd(currentTrace, exception)
                    } else {
                        diagnostics.recordComplete(currentTrace, countedOutput, exception)
                    }
                }
            }
        } finally {
            if (shouldGracefullyFinishFiniteResponse) {
                finishFiniteResponse(socket)
            }
            runCatching { socket.close() }
            clients.remove(socket)
            mediaStreams.removeSocket(socket)
        }
    }

    private fun finishFiniteResponse(socket: Socket) {
        runCatching { socket.shutdownOutput() }
        runCatching {
            val input = socket.getInputStream()
            val originalTimeout = socket.soTimeout
            val waitStartedMs = SystemClock.elapsedRealtime()
            try {
                socket.soTimeout = finiteResponseDrainPollTimeoutMs
                while (SystemClock.elapsedRealtime() - waitStartedMs < finiteResponseGracefulCloseMaxWaitMs) {
                    try {
                        if (input.read() == -1) break
                    } catch (_: SocketTimeoutException) {
                        // Poll until the short wait budget is exhausted.
                    }
                }
            } finally {
                runCatching { socket.soTimeout = originalTimeout }
            }
        }
    }

    private fun writeIndex(output: OutputStream) {
        WebHttpResponses.writeText(output, WebDashboardPage.renderShell(appVersionLabel()), "text/html; charset=utf-8")
    }

    private fun writeDashboardBodyAsset(output: OutputStream, target: String) {
        val index = WebHttpRoutes.dashboardBodyChunkIndex(target) ?: run {
            WebHttpResponses.writeNotFound(output)
            return
        }
        val chunk = WebDashboardPage.bodyChunk(index, appVersionLabel()) ?: run {
            WebHttpResponses.writeNotFound(output)
            return
        }
        WebHttpResponses.writeText(output, chunk, "text/html; charset=utf-8")
    }

    private fun writeDashboardCssAsset(output: OutputStream, target: String) {
        val index = WebHttpRoutes.dashboardCssChunkIndex(target) ?: run {
            WebHttpResponses.writeNotFound(output)
            return
        }
        val chunk = WebDashboardPage.cssChunk(index, appVersionLabel()) ?: run {
            WebHttpResponses.writeNotFound(output)
            return
        }
        WebHttpResponses.writeText(output, chunk, "text/css; charset=utf-8")
    }

    private fun writeDashboardJsAsset(output: OutputStream, target: String) {
        val index = WebHttpRoutes.dashboardJsChunkIndex(target) ?: run {
            WebHttpResponses.writeNotFound(output)
            return
        }
        val chunk = WebDashboardPage.jsChunk(index, appVersionLabel()) ?: run {
            WebHttpResponses.writeNotFound(output)
            return
        }
        WebHttpResponses.writeText(output, chunk, "application/javascript; charset=utf-8")
    }

    // Debug and control API responses.

    private fun writeDebugStream(output: OutputStream) {
        val nowMs = SystemClock.elapsedRealtime()
        val state = stateProvider()
        lastKnownStreaming = state.streaming
        val json = WebDebugStreamJson.build(
            mediaStreams.debugSnapshot(
                nowElapsedMs = nowMs,
                serverRunning = running.get(),
                serverStartedAtElapsedMs = serverStartedAtElapsedMs,
                streaming = state.streaming,
                httpClients = clients.size,
                cameraStreamDebug = streamDebugProvider()
            )
        )
        WebHttpResponses.writeText(output, json.toString(), "application/json; charset=utf-8")
    }

    private fun writeDiagnostics(output: OutputStream) {
        WebHttpResponses.writeText(output, diagnosticsText(), "text/plain; charset=utf-8")
    }

    // Debug counters and small parsers.

    private fun resetDebugStats() {
        mediaStreams.resetDebugStats()
    }

    private fun appVersionLabel(): String {
        return AppDisplayInfo.versionLabel()
    }

    private fun recordStreamRequest(
        trace: HttpDiagnostics.Trace,
        recordedSetter: (Boolean) -> Unit,
        block: () -> Unit
    ) {
        try {
            block()
            diagnostics.recordEnd(trace, failure = null)
            recordedSetter(true)
        } catch (exception: Exception) {
            diagnostics.recordEnd(trace, exception)
            recordedSetter(true)
            throw exception
        }
    }

    private fun diagnosticsText(): String {
        val nowMs = SystemClock.elapsedRealtime()
        val streamingSnapshot = lastKnownStreaming
        val snapshot = mediaStreams.debugSnapshot(
            nowElapsedMs = nowMs,
            serverRunning = running.get(),
            serverStartedAtElapsedMs = serverStartedAtElapsedMs,
            streaming = streamingSnapshot ?: false,
            httpClients = clients.size,
            cameraStreamDebug = streamDebugProvider()
        )
        val requestEntries = diagnostics.snapshot()
        val activeEntries = diagnostics.activeSnapshot(nowMs)
        return buildString {
            appendLine("IPCam Android HTTP diagnostics")
            appendLine("appVersion=${appVersionLabel()}")
            appendLine("device=${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("androidSdk=${Build.VERSION.SDK_INT}")
            appendLine("serverRunning=${running.get()}")
            appendLine("serverUptimeMs=${elapsedSince(nowMs, serverStartedAtElapsedMs) ?: "n/a"}")
            appendLine("httpClients=${clients.size}")
            appendLine("activeHttpRemotes=${activeHttpRemotes()}")
            appendLine("streaming=${streamingSnapshot?.toString() ?: "unknown"}")
            appendLine("streamingSource=${if (streamingSnapshot == null) "notSampledByDiagnostics" else "lastKnown"}")
            appendLine()
            appendLine("[mjpeg]")
            appendLine("clients=${snapshot.mjpegClients}")
            appendLine("latestFrameId=${snapshot.latestFrameId ?: "n/a"}")
            appendLine("latestFrameBytes=${snapshot.latestMjpegFrame?.bytes?.size ?: "n/a"}")
            appendLine("latestFrameAgeMs=${snapshot.latestMjpegFrame?.let { nowMs - it.generatedAtElapsedMs } ?: "n/a"}")
            appendLine("framesReceived=${snapshot.mjpegFramesReceived}")
            appendLine("framesSent=${snapshot.mjpegFramesSent}")
            appendLine("bytesSent=${snapshot.mjpegBytesSent}")
            appendLine("writeFailures=${snapshot.mjpegWriteFailures}")
            appendLine("lastSocketWriteMs=${snapshot.lastMjpegSocketWriteMs}")
            appendLine("maxSocketWriteMs=${snapshot.maxMjpegSocketWriteMs}")
            appendLine()
            appendLine("[h264]")
            appendLine("clients=${snapshot.h264Clients}")
            appendLine("accessUnitsReceived=${snapshot.h264AccessUnitsReceived}")
            appendLine("bytesReceived=${snapshot.h264BytesReceived}")
            appendLine("accessUnitsSent=${snapshot.h264AccessUnitsSent}")
            appendLine("bytesSent=${snapshot.h264BytesSent}")
            appendLine("writeFailures=${snapshot.h264WriteFailures}")
            appendLine("lastSocketWriteMs=${snapshot.lastH264SocketWriteMs}")
            appendLine("maxSocketWriteMs=${snapshot.maxH264SocketWriteMs}")
            appendLine()
            appendLine("[audio]")
            appendLine("clients=${snapshot.audioClients}")
            appendLine("accessUnitsReceived=${snapshot.audioAccessUnitsReceived}")
            appendLine("bytesReceived=${snapshot.audioBytesReceived}")
            appendLine("accessUnitsSent=${snapshot.audioAccessUnitsSent}")
            appendLine("bytesSent=${snapshot.audioBytesSent}")
            appendLine("writeFailures=${snapshot.audioWriteFailures}")
            appendLine("lastSocketWriteMs=${snapshot.lastAudioSocketWriteMs}")
            appendLine("maxSocketWriteMs=${snapshot.maxAudioSocketWriteMs}")
            appendLine()
            appendLine("[cameraStream]")
            appendLine("cameraId=${snapshot.cameraStreamDebug?.cameraId ?: "n/a"}")
            appendLine("physicalCameraId=${snapshot.cameraStreamDebug?.physicalCameraId ?: "n/a"}")
            appendLine("outputSize=${snapshot.cameraStreamDebug?.outputSize ?: "n/a"}")
            appendLine("mjpegSize=${snapshot.cameraStreamDebug?.mjpegSize ?: "n/a"}")
            appendLine("targetFps=${snapshot.cameraStreamDebug?.targetFps ?: "n/a"}")
            appendLine("sessionMode=${snapshot.cameraStreamDebug?.sessionMode ?: "n/a"}")
            appendLine("sessionModeReason=${snapshot.cameraStreamDebug?.sessionModeReason ?: "n/a"}")
            appendLine()
            appendLine("[activeRequests count=${activeEntries.size}]")
            if (activeEntries.isEmpty()) {
                appendLine("(empty)")
            } else {
                activeEntries.forEach { entry ->
                    appendLine(entry.toTextLine())
                }
            }
            appendLine()
            appendLine("[recentRequests newestLast count=${requestEntries.size}]")
            if (requestEntries.isEmpty()) {
                appendLine("(empty)")
            } else {
                requestEntries.forEach { entry ->
                    appendLine(entry.toTextLine(nowMs))
                }
            }
        }
    }

    private fun activeHttpRemotes(): String {
        return clients.mapNotNull { socket -> socket.remoteSocketAddress?.toString() }
            .take(16)
            .joinToString(separator = ",")
            .ifBlank { "none" }
    }

    private fun elapsedSince(nowMs: Long, thenMs: Long): Long? {
        return if (thenMs > 0L) nowMs - thenMs else null
    }

}

