package com.opencode.multilensipcam

import android.os.SystemClock
import java.io.BufferedOutputStream
import java.io.File
import java.io.OutputStream
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

class WebMediaStreamBroker(
    snapshotCacheRoot: File,
    private val h264RequestHandler: () -> Boolean,
    private val runningProvider: () -> Boolean,
    private val mjpegClientCountChanged: () -> Unit = {},
    private val h264ClientCountChanged: () -> Unit = {}
) {
    private data class H264Client(
        val socket: Socket,
        val output: BufferedOutputStream
    )

    private data class AudioClient(
        val socket: Socket,
        val output: BufferedOutputStream,
        val writePending: AtomicBoolean = AtomicBoolean(false)
    )

    private data class ServerFrame(
        val id: Long,
        val frame: MjpegFrame,
        val receivedAtElapsedMs: Long
    )

    private data class FrozenSnapshot(
        val id: String,
        val file: File
    )

    private val mjpegClients = CopyOnWriteArrayList<Socket>()
    private val h264Clients = CopyOnWriteArrayList<H264Client>()
    private val audioClients = CopyOnWriteArrayList<AudioClient>()
    private val audioFanoutLock = Object()
    private val frameSignal = Object()
    private val snapshotStoreLock = Any()
    private val snapshotDirectory = File(snapshotCacheRoot, SNAPSHOT_DIRECTORY_NAME)
    private val mjpegFramesReceived = AtomicLong(0)
    private val mjpegFramesSent = AtomicLong(0)
    private val mjpegBytesSent = AtomicLong(0)
    private val mjpegWriteFailures = AtomicLong(0)
    private val h264AccessUnitsReceived = AtomicLong(0)
    private val h264BytesReceived = AtomicLong(0)
    private val h264AccessUnitsSent = AtomicLong(0)
    private val h264BytesSent = AtomicLong(0)
    private val h264KeyFramesReceived = AtomicLong(0)
    private val h264CodecConfigUnitsReceived = AtomicLong(0)
    private val h264WriteFailures = AtomicLong(0)
    private val audioAccessUnitsReceived = AtomicLong(0)
    private val audioBytesReceived = AtomicLong(0)
    private val audioAccessUnitsSent = AtomicLong(0)
    private val audioBytesSent = AtomicLong(0)
    private val audioWriteFailures = AtomicLong(0)

    @Volatile
    private var latestFrame: ServerFrame? = null

    @Volatile
    private var audioFanoutExecutor: ExecutorService? = null

    @Volatile
    private var latestFrameId: Long = 0

    @Volatile
    private var latestCodecConfig: ByteArray? = null

    @Volatile
    private var lastMjpegFrameReceivedAtElapsedMs: Long = 0

    @Volatile
    private var lastMjpegFrameSentAtElapsedMs: Long = 0

    @Volatile
    private var lastMjpegSendIntervalMs: Long = 0

    @Volatile
    private var lastMjpegSocketWriteMs: Long = 0

    @Volatile
    private var maxMjpegSocketWriteMs: Long = 0

    @Volatile
    private var lastMjpegFrameAgeMs: Long = 0

    @Volatile
    private var lastH264UnitReceivedAtElapsedMs: Long = 0

    @Volatile
    private var lastH264SocketWriteMs: Long = 0

    @Volatile
    private var maxH264SocketWriteMs: Long = 0

    @Volatile
    private var lastAudioUnitReceivedAtElapsedMs: Long = 0

    @Volatile
    private var lastAudioSocketWriteMs: Long = 0

    @Volatile
    private var maxAudioSocketWriteMs: Long = 0

    val audioClientCount: Int
        get() = audioClients.size

    val mjpegClientCount: Int
        get() = mjpegClients.size

    val h264ClientCount: Int
        get() = h264Clients.size

    @Volatile
    private var lastMjpegClientDisconnectedAtElapsedMs: Long = 0

    @Volatile
    private var lastMjpegDemandAtElapsedMs: Long = 0

    private val snapshotSequence = AtomicLong(0L)

    init {
        initializeSnapshotDirectory()
    }

    fun hasRecentMjpegClients(graceMs: Long): Boolean {
        if (mjpegClients.isNotEmpty()) return true
        val lastDemand = maxOf(lastMjpegClientDisconnectedAtElapsedMs, lastMjpegDemandAtElapsedMs)
        return lastDemand > 0 && SystemClock.elapsedRealtime() - lastDemand < graceMs
    }

    fun closeActiveStreamClientsAndFrames() {
        mjpegClients.forEach { socket -> runCatching { socket.close() } }
        mjpegClients.clear()
        h264Clients.forEach { client -> runCatching { client.socket.close() } }
        h264Clients.clear()
        lastMjpegClientDisconnectedAtElapsedMs = SystemClock.elapsedRealtime()
        mjpegClientCountChanged()
        h264ClientCountChanged()
        closeAudioClients()
        latestFrame = null
        latestFrameId = 0
        latestCodecConfig = null
        lastMjpegDemandAtElapsedMs = 0
        synchronized(frameSignal) {
            frameSignal.notifyAll()
        }
    }

    fun closeAudioClients() {
        audioClients.forEach { client -> runCatching { client.socket.close() } }
        audioClients.clear()
        shutdownAudioFanoutExecutor()
    }

    fun shutdown() {
        closeActiveStreamClientsAndFrames()
        shutdownAudioFanoutExecutor()
    }

    fun removeSocket(socket: Socket) {
        val removedMjpeg = mjpegClients.remove(socket)
        val removedH264 = h264Clients.removeAll { it.socket == socket }
        audioClients.removeAll { it.socket == socket }
        if (removedMjpeg) {
            lastMjpegClientDisconnectedAtElapsedMs = SystemClock.elapsedRealtime()
            mjpegClientCountChanged()
        }
        if (removedH264) {
            h264ClientCountChanged()
        }
    }

    fun updateFrame(frame: MjpegFrame) {
        val receivedAtMs = SystemClock.elapsedRealtime()
        synchronized(frameSignal) {
            latestFrameId += 1
            latestFrame = ServerFrame(
                id = latestFrameId,
                frame = frame,
                receivedAtElapsedMs = receivedAtMs
            )
            frameSignal.notifyAll()
        }
        mjpegFramesReceived.incrementAndGet()
        lastMjpegFrameReceivedAtElapsedMs = receivedAtMs
    }

    fun updateH264(accessUnit: H264AccessUnit) {
        h264AccessUnitsReceived.incrementAndGet()
        h264BytesReceived.addAndGet(accessUnit.bytes.size.toLong())
        lastH264UnitReceivedAtElapsedMs = SystemClock.elapsedRealtime()
        if (accessUnit.isKeyFrame) {
            h264KeyFramesReceived.incrementAndGet()
        }
        if (accessUnit.isCodecConfig) {
            h264CodecConfigUnitsReceived.incrementAndGet()
            latestCodecConfig = accessUnit.bytes
        }
        if (h264Clients.isEmpty()) {
            return
        }

        val payload = accessUnit.bytes
        h264Clients.forEach { client ->
            try {
                val startedAtMs = SystemClock.elapsedRealtime()
                client.output.write(payload)
                client.output.flush()
                val writeMs = SystemClock.elapsedRealtime() - startedAtMs
                lastH264SocketWriteMs = writeMs
                if (writeMs > maxH264SocketWriteMs) {
                    maxH264SocketWriteMs = writeMs
                }
                h264AccessUnitsSent.incrementAndGet()
                h264BytesSent.addAndGet(payload.size.toLong())
            } catch (_: Exception) {
                h264WriteFailures.incrementAndGet()
                removeH264Client(client)
            }
        }
    }

    fun updateAudio(accessUnit: AacAccessUnit) {
        audioAccessUnitsReceived.incrementAndGet()
        audioBytesReceived.addAndGet(accessUnit.bytes.size.toLong())
        lastAudioUnitReceivedAtElapsedMs = SystemClock.elapsedRealtime()
        if (audioClients.isEmpty()) {
            return
        }

        val payload = accessUnit.bytes
        audioClients.forEach { client ->
            if (!client.writePending.compareAndSet(false, true)) return@forEach
            try {
                audioFanoutExecutor().execute {
                    writeAudioPayload(client, payload)
                }
            } catch (_: RejectedExecutionException) {
                client.writePending.set(false)
                audioWriteFailures.incrementAndGet()
                audioClients.remove(client)
                runCatching { client.socket.close() }
            }
        }
    }

    fun writeSnapshot(output: OutputStream) {
        val frozenSnapshot = captureFrozenSnapshot() ?: run {
            WebHttpResponses.writeServiceUnavailable(output, "No frame available yet.")
            return
        }
        WebHttpResponses.writeRedirect(output, snapshotPath(frozenSnapshot.id))
    }

    fun writeFrozenSnapshot(output: OutputStream, snapshotId: String) {
        if (!SNAPSHOT_ID_PATTERN.matches(snapshotId)) {
            WebHttpResponses.writeNotFound(output)
            return
        }
        val snapshot = synchronized(snapshotStoreLock) {
            val file = snapshotFile(snapshotId)
            if (!file.isFile) return@synchronized null
            FrozenSnapshot(
                id = snapshotId,
                file = file
            )
        } ?: run {
            WebHttpResponses.writeNotFound(output)
            return
        }
        output.write(
            WebMediaHeaders.frozenSnapshot(
                contentLength = snapshot.file.length(),
                snapshotId = snapshot.id
            ).toByteArray(StandardCharsets.UTF_8)
        )
        snapshot.file.inputStream().use { input -> input.copyTo(output) }
        output.flush()
    }

    fun writeMjpeg(socket: Socket) {
        mjpegClients += socket
        mjpegClientCountChanged()
        val output = socket.getOutputStream()
        val boundary = "frame"
        val writer = BufferedOutputStream(output)
        writer.write(WebMediaHeaders.mjpegStream(boundary).toByteArray(StandardCharsets.UTF_8))
        writer.flush()

        var lastSentFrameId = -1L
        try {
            while (runningProvider()) {
                val serverFrame = awaitNextFrame(lastSentFrameId) ?: break
                val frame = serverFrame.frame
                lastSentFrameId = serverFrame.id
                val sendStartedAtMs = SystemClock.elapsedRealtime()
                val frameAgeMs = sendStartedAtMs - frame.generatedAtElapsedMs
                writer.write(
                    WebMediaHeaders.mjpegFrame(
                        boundary = boundary,
                        frameId = serverFrame.id,
                        frame = frame,
                        receivedAtElapsedMs = serverFrame.receivedAtElapsedMs,
                        frameAgeMs = frameAgeMs
                    ).toByteArray(StandardCharsets.UTF_8)
                )
                writer.write(frame.bytes)
                writer.write("\r\n".toByteArray(StandardCharsets.UTF_8))
                writer.flush()
                recordMjpegSent(frame.bytes.size, sendStartedAtMs, frameAgeMs)
            }
        } catch (_: Exception) {
            mjpegWriteFailures.incrementAndGet()
        } finally {
            removeSocket(socket)
        }
    }

    fun writeH264(socket: Socket) {
        if (!h264RequestHandler()) {
            WebHttpResponses.writeServiceUnavailable(socket.getOutputStream(), "H.264 encoder is not available for the current stream.")
            return
        }
        val output = BufferedOutputStream(socket.getOutputStream())
        output.write(WebMediaHeaders.h264Stream().toByteArray(StandardCharsets.UTF_8))
        latestCodecConfig?.let { output.write(it) }
        output.flush()

        val client = H264Client(socket = socket, output = output)
        h264Clients += client
        h264ClientCountChanged()

        while (runningProvider() && !socket.isClosed) {
            try {
                Thread.sleep(250)
            } catch (_: InterruptedException) {
                break
            }
        }
        removeSocket(socket)
    }

    fun writeAudio(socket: Socket, audioRequestHandler: (Boolean) -> Boolean, waitForReady: Boolean) {
        if (!audioRequestHandler(waitForReady)) {
            WebHttpResponses.writeServiceUnavailable(socket.getOutputStream(), "Audio is not running. Enable audio and start streaming first.")
            return
        }
        val output = BufferedOutputStream(socket.getOutputStream())
        output.write(WebMediaHeaders.aacStream().toByteArray(StandardCharsets.UTF_8))
        output.flush()

        val client = AudioClient(socket = socket, output = output)
        audioClients += client

        while (runningProvider() && !socket.isClosed) {
            try {
                Thread.sleep(250)
            } catch (_: InterruptedException) {
                break
            }
        }
    }

    fun debugSnapshot(
        nowElapsedMs: Long,
        serverRunning: Boolean,
        serverStartedAtElapsedMs: Long,
        streaming: Boolean,
        httpClients: Int,
        cameraStreamDebug: CameraStreamDebugState?
    ): WebDebugStreamSnapshot {
        val latest = latestFrame
        return WebDebugStreamSnapshot(
            nowElapsedMs = nowElapsedMs,
            serverRunning = serverRunning,
            serverStartedAtElapsedMs = serverStartedAtElapsedMs,
            streaming = streaming,
            cameraStreamDebug = cameraStreamDebug,
            httpClients = httpClients,
            mjpegClients = mjpegClients.size,
            h264Clients = h264Clients.size,
            latestFrameId = latest?.id,
            latestMjpegFrame = latest?.frame,
            lastMjpegFrameReceivedAtElapsedMs = lastMjpegFrameReceivedAtElapsedMs,
            mjpegFramesReceived = mjpegFramesReceived.get(),
            mjpegFramesSent = mjpegFramesSent.get(),
            mjpegBytesSent = mjpegBytesSent.get(),
            mjpegWriteFailures = mjpegWriteFailures.get(),
            lastMjpegSendIntervalMs = lastMjpegSendIntervalMs,
            lastMjpegSocketWriteMs = lastMjpegSocketWriteMs,
            maxMjpegSocketWriteMs = maxMjpegSocketWriteMs,
            lastMjpegFrameAgeMs = lastMjpegFrameAgeMs,
            h264AccessUnitsReceived = h264AccessUnitsReceived.get(),
            h264BytesReceived = h264BytesReceived.get(),
            h264AccessUnitsSent = h264AccessUnitsSent.get(),
            h264BytesSent = h264BytesSent.get(),
            h264KeyFramesReceived = h264KeyFramesReceived.get(),
            h264CodecConfigUnitsReceived = h264CodecConfigUnitsReceived.get(),
            h264WriteFailures = h264WriteFailures.get(),
            lastH264UnitReceivedAtElapsedMs = lastH264UnitReceivedAtElapsedMs,
            lastH264SocketWriteMs = lastH264SocketWriteMs,
            maxH264SocketWriteMs = maxH264SocketWriteMs,
            audioClients = audioClients.size,
            audioAccessUnitsReceived = audioAccessUnitsReceived.get(),
            audioBytesReceived = audioBytesReceived.get(),
            audioAccessUnitsSent = audioAccessUnitsSent.get(),
            audioBytesSent = audioBytesSent.get(),
            audioWriteFailures = audioWriteFailures.get(),
            lastAudioUnitReceivedAtElapsedMs = lastAudioUnitReceivedAtElapsedMs,
            lastAudioSocketWriteMs = lastAudioSocketWriteMs,
            maxAudioSocketWriteMs = maxAudioSocketWriteMs
        )
    }

    fun resetDebugStats() {
        mjpegFramesReceived.set(0)
        mjpegFramesSent.set(0)
        mjpegBytesSent.set(0)
        mjpegWriteFailures.set(0)
        h264AccessUnitsReceived.set(0)
        h264BytesReceived.set(0)
        h264AccessUnitsSent.set(0)
        h264BytesSent.set(0)
        h264KeyFramesReceived.set(0)
        h264CodecConfigUnitsReceived.set(0)
        h264WriteFailures.set(0)
        audioAccessUnitsReceived.set(0)
        audioBytesReceived.set(0)
        audioAccessUnitsSent.set(0)
        audioBytesSent.set(0)
        audioWriteFailures.set(0)
        lastMjpegFrameReceivedAtElapsedMs = 0
        lastMjpegFrameSentAtElapsedMs = 0
        lastMjpegSendIntervalMs = 0
        lastMjpegSocketWriteMs = 0
        maxMjpegSocketWriteMs = 0
        lastMjpegFrameAgeMs = 0
        lastH264UnitReceivedAtElapsedMs = 0
        lastH264SocketWriteMs = 0
        maxH264SocketWriteMs = 0
        lastAudioUnitReceivedAtElapsedMs = 0
        lastAudioSocketWriteMs = 0
        maxAudioSocketWriteMs = 0
    }

    private fun awaitNextFrame(lastSentFrameId: Long): ServerFrame? {
        return awaitNextFrame(lastSentFrameId, timeoutMs = 0L)
    }

    private fun awaitNextFrame(lastSentFrameId: Long, timeoutMs: Long): ServerFrame? {
        val deadlineMs = if (timeoutMs > 0) SystemClock.elapsedRealtime() + timeoutMs else Long.MAX_VALUE
        synchronized(frameSignal) {
            while (runningProvider()) {
                val frame = latestFrame
                if (frame != null && frame.id != lastSentFrameId) {
                    return frame
                }
                val remainingMs = deadlineMs - SystemClock.elapsedRealtime()
                if (remainingMs <= 0) return null
                frameSignal.wait(minOf(250L, remainingMs))
            }
        }
        return null
    }

    private fun recordMjpegDemand() {
        lastMjpegDemandAtElapsedMs = SystemClock.elapsedRealtime()
        mjpegClientCountChanged()
    }

    private fun captureFrozenSnapshot(): FrozenSnapshot? {
        val requestStartFrameId = latestFrame?.id ?: -1L
        recordMjpegDemand()
        val frame = (awaitNextFrame(requestStartFrameId, SNAPSHOT_FRAME_WAIT_MS) ?: latestFrame)?.frame ?: return null
        val snapshotId = snapshotSequenceId()
        val snapshotFile = snapshotFile(snapshotId)
        val snapshot = FrozenSnapshot(
            id = snapshotId,
            file = snapshotFile
        )
        val wroteFile = synchronized(snapshotStoreLock) {
            snapshotDirectory.mkdirs()
            val success = runCatching {
                snapshot.file.outputStream().use { output ->
                    output.write(frame.bytes)
                    output.flush()
                }
            }.isSuccess
            pruneSnapshotFilesLocked()
            success && snapshot.file.isFile
        }
        if (!wroteFile) {
            runCatching { snapshot.file.delete() }
            return null
        }
        return snapshot
    }

    private fun snapshotSequenceId(): String {
        return "${System.currentTimeMillis()}-${snapshotSequence.incrementAndGet()}"
    }

    private fun snapshotPath(snapshotId: String): String = "/snapshots/$snapshotId.jpg"

    private fun snapshotFile(snapshotId: String): File {
        return File(snapshotDirectory, "$snapshotId.jpg")
    }

    private fun initializeSnapshotDirectory() {
        synchronized(snapshotStoreLock) {
            snapshotDirectory.mkdirs()
            if (!snapshotDirectory.isDirectory) return
            snapshotDirectory.listFiles()?.forEach { file ->
                if (!file.isFile) return@forEach
                if (!SNAPSHOT_FILE_PATTERN.matches(file.name)) {
                    runCatching { file.delete() }
                }
            }
            pruneSnapshotFilesLocked()
        }
    }

    private fun pruneSnapshotFilesLocked() {
        val files = snapshotDirectory.listFiles()
            ?.filter { it.isFile && SNAPSHOT_FILE_PATTERN.matches(it.name) }
            ?.sortedBy { it.name }
            ?: return
        var totalBytes = files.sumOf { it.length() }
        var filesToTrim = (files.size - MAX_FROZEN_SNAPSHOTS).coerceAtLeast(0)
        files.forEach { file ->
            val shouldDelete = filesToTrim > 0 || totalBytes > MAX_FROZEN_SNAPSHOT_TOTAL_BYTES
            if (!shouldDelete) return@forEach
            val size = file.length()
            if (file.delete()) {
                filesToTrim = (filesToTrim - 1).coerceAtLeast(0)
                totalBytes = (totalBytes - size).coerceAtLeast(0L)
            }
        }
    }

    private fun removeH264Client(client: H264Client) {
        val removed = h264Clients.remove(client)
        runCatching { client.socket.close() }
        if (removed) {
            h264ClientCountChanged()
        }
    }

    private fun recordMjpegSent(bytes: Int, sendStartedAtMs: Long, frameAgeMs: Long) {
        val finishedAtMs = SystemClock.elapsedRealtime()
        val writeMs = finishedAtMs - sendStartedAtMs
        val previousSentAtMs = lastMjpegFrameSentAtElapsedMs
        if (previousSentAtMs > 0) {
            lastMjpegSendIntervalMs = finishedAtMs - previousSentAtMs
        }
        lastMjpegFrameSentAtElapsedMs = finishedAtMs
        lastMjpegSocketWriteMs = writeMs
        if (writeMs > maxMjpegSocketWriteMs) {
            maxMjpegSocketWriteMs = writeMs
        }
        lastMjpegFrameAgeMs = frameAgeMs
        mjpegFramesSent.incrementAndGet()
        mjpegBytesSent.addAndGet(bytes.toLong())
    }

    private fun writeAudioPayload(client: AudioClient, payload: ByteArray) {
        try {
            val startedAtMs = SystemClock.elapsedRealtime()
            client.output.write(payload)
            client.output.flush()
            val writeMs = SystemClock.elapsedRealtime() - startedAtMs
            lastAudioSocketWriteMs = writeMs
            if (writeMs > maxAudioSocketWriteMs) {
                maxAudioSocketWriteMs = writeMs
            }
            audioAccessUnitsSent.incrementAndGet()
            audioBytesSent.addAndGet(payload.size.toLong())
        } catch (_: Exception) {
            audioWriteFailures.incrementAndGet()
            audioClients.remove(client)
            runCatching { client.socket.close() }
        } finally {
            client.writePending.set(false)
        }
    }

    private fun audioFanoutExecutor(): ExecutorService {
        synchronized(audioFanoutLock) {
            val currentExecutor = audioFanoutExecutor
            if (currentExecutor != null && !currentExecutor.isShutdown && !currentExecutor.isTerminated) {
                return currentExecutor
            }
            return Executors.newCachedThreadPool().also { audioFanoutExecutor = it }
        }
    }

    private fun shutdownAudioFanoutExecutor() {
        val executorToShutdown = synchronized(audioFanoutLock) {
            val currentExecutor = audioFanoutExecutor
            audioFanoutExecutor = null
            currentExecutor
        }
        runCatching { executorToShutdown?.shutdownNow() }
    }

    private companion object {
        const val SNAPSHOT_FRAME_WAIT_MS = 1_500L
        const val MAX_FROZEN_SNAPSHOTS = 64
        const val MAX_FROZEN_SNAPSHOT_TOTAL_BYTES = 96L * 1024L * 1024L
        const val SNAPSHOT_DIRECTORY_NAME = "snapshots"
        val SNAPSHOT_ID_PATTERN = Regex("^\\d{13}-\\d+$")
        val SNAPSHOT_FILE_PATTERN = Regex("^\\d{13}-\\d+\\.jpg$")
    }
}
