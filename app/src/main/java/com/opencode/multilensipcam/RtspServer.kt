package com.opencode.multilensipcam

import android.util.Base64
import android.util.Log
import android.os.SystemClock
import java.io.BufferedOutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class RtspServer(
    private val port: Int,
    private val streamRequestHandler: () -> Boolean,
    private val audioAvailableProvider: () -> Boolean,
    private val clientCountChanged: () -> Unit
) {
    private data class RtspClient(
        val socket: Socket,
        val output: BufferedOutputStream,
        val sessionId: String,
        var videoRtpChannel: Int = 0,
        var audioRtpChannel: Int = 2,
        var videoSetup: Boolean = false,
        var audioSetup: Boolean = false,
        var playing: Boolean = false,
        var videoSequence: Int = 0,
        var audioSequence: Int = 0,
        val videoSsrc: Int = nextSsrc(),
        val audioSsrc: Int = nextSsrc(),
        val writeLock: Any = Object()
    )

    private data class H264ParameterSets(
        val sps: ByteArray?,
        val pps: ByteArray?
    )

    private val running = AtomicBoolean(false)
    private val clients = CopyOnWriteArrayList<RtspClient>()
    private val lifecycleLock = Object()

    @Volatile
    private var serverSocket: ServerSocket? = null

    @Volatile
    private var executor: ExecutorService? = null

    @Volatile
    private var latestCodecConfig: ByteArray? = null

    @Volatile
    private var latestParameterSets: H264ParameterSets = H264ParameterSets(sps = null, pps = null)

    val clientCount: Int
        get() = clients.count { it.playing && (it.videoSetup || it.audioSetup) }

    val isRunning: Boolean
        get() = running.get() && serverSocket?.isClosed == false

    fun start(): Boolean {
        synchronized(lifecycleLock) {
            if (running.get()) return true
            val socket = ServerSocket()
            try {
                socket.reuseAddress = true
                socket.bind(InetSocketAddress(port))
            } catch (exception: Exception) {
                running.set(false)
                runCatching { socket.close() }
                serverSocket = null
                Log.w(TAG, "RTSP bind failed on port $port", exception)
                return false
            }
            running.set(true)
            serverSocket = socket
            val currentExecutor = Executors.newCachedThreadPool()
            executor = currentExecutor
            currentExecutor.execute { acceptClients(socket, currentExecutor) }
            return true
        }
    }

    private fun acceptClients(socket: ServerSocket, currentExecutor: ExecutorService) {
        while (running.get()) {
            try {
                val client = socket.accept()
                currentExecutor.execute { handleClient(client) }
            } catch (exception: Exception) {
                if (!running.get()) return
                Log.w(TAG, "RTSP accept failed", exception)
            }
        }
    }

    fun stop() {
        running.set(false)
        clients.forEach { client -> runCatching { client.socket.close() } }
        clients.clear()
        runCatching { serverSocket?.close() }
        serverSocket = null
        executor?.shutdownNow()
        executor = null
        clientCountChanged()
    }

    fun clearCachedVideoConfig() {
        latestCodecConfig = null
        latestParameterSets = H264ParameterSets(sps = null, pps = null)
    }

    fun updateH264(accessUnit: H264AccessUnit) {
        val nals = splitAnnexBNalUnits(accessUnit.bytes)
        if (nals.isEmpty()) return
        updateCachedVideoConfig(accessUnit, nals)
        val timestamp = ((SystemClock.elapsedRealtimeNanos() / 1_000_000L) * 90L).toInt()
        clients.forEach { client ->
            if (!client.playing || !client.videoSetup) return@forEach
            try {
                nals.forEachIndexed { index, nal ->
                    writeH264Nal(
                        client = client,
                        nal = nal,
                        timestamp = timestamp,
                        marker = index == nals.lastIndex
                    )
                }
            } catch (_: Exception) {
                removeClient(client)
            }
        }
    }

    fun updateAudio(accessUnit: AacAccessUnit) {
        val payload = stripAdtsHeader(accessUnit.bytes)
        if (payload.isEmpty()) return
        val timestamp = ((accessUnit.presentationTimeUs * AUDIO_SAMPLE_RATE) / 1_000_000L).toInt()
        clients.forEach { client ->
            if (!client.playing || !client.audioSetup) return@forEach
            try {
                val auHeader = payload.size shl 3
                val rtpPayload = ByteArray(4 + payload.size)
                rtpPayload[0] = 0
                rtpPayload[1] = 16
                rtpPayload[2] = ((auHeader shr 8) and 0xFF).toByte()
                rtpPayload[3] = (auHeader and 0xFF).toByte()
                payload.copyInto(rtpPayload, 4)
                writeInterleavedRtp(
                    client = client,
                    channel = client.audioRtpChannel,
                    payloadType = AAC_PAYLOAD_TYPE,
                    marker = true,
                    sequence = client.audioSequence++,
                    timestamp = timestamp,
                    ssrc = client.audioSsrc,
                    payload = rtpPayload
                )
            } catch (_: Exception) {
                removeClient(client)
            }
        }
    }

    private fun updateCachedVideoConfig(accessUnit: H264AccessUnit, nals: List<ByteArray>) {
        if (accessUnit.isCodecConfig) {
            latestCodecConfig = accessUnit.bytes
        }
        val current = latestParameterSets
        var sps = current.sps
        var pps = current.pps
        nals.forEach { nal ->
            when (nalType(nal)) {
                7 -> sps = nal
                8 -> pps = nal
            }
        }
        if (sps !== current.sps || pps !== current.pps) {
            latestParameterSets = H264ParameterSets(sps = sps, pps = pps)
            val cachedSps = sps
            val cachedPps = pps
            if (cachedSps != null && cachedPps != null) {
                latestCodecConfig = buildAnnexBConfig(cachedSps, cachedPps)
            }
        }
    }

    private fun writeCachedVideoConfig(client: RtspClient) {
        val config = latestCodecConfig ?: return
        val nals = splitAnnexBNalUnits(config)
        if (nals.isEmpty()) return
        val timestamp = ((SystemClock.elapsedRealtimeNanos() / 1_000_000L) * 90L).toInt()
        nals.forEachIndexed { index, nal ->
            writeH264Nal(
                client = client,
                nal = nal,
                timestamp = timestamp,
                marker = index == nals.lastIndex
            )
        }
    }

    private fun cachedSpropFmtp(): String? {
        val parameterSets = latestParameterSets
        val sps = parameterSets.sps ?: return null
        val pps = parameterSets.pps ?: return null
        val spsValue = Base64.encodeToString(sps, Base64.NO_WRAP)
        val ppsValue = Base64.encodeToString(pps, Base64.NO_WRAP)
        val profile = if (sps.size >= 4) {
            "%02X%02X%02X".format(Locale.US, sps[1].toInt() and 0xFF, sps[2].toInt() and 0xFF, sps[3].toInt() and 0xFF)
        } else {
            null
        }
        return buildString {
            append("packetization-mode=1")
            profile?.let { append(";profile-level-id=").append(it) }
            append(";sprop-parameter-sets=").append(spsValue).append(",").append(ppsValue)
        }
    }

    private fun nalType(nal: ByteArray): Int {
        return if (nal.isNotEmpty()) nal[0].toInt() and 0x1F else -1
    }

    private fun buildAnnexBConfig(sps: ByteArray, pps: ByteArray): ByteArray {
        return byteArrayOf(0, 0, 0, 1) + sps + byteArrayOf(0, 0, 0, 1) + pps
    }

    /*
     * RTSP request handling.
     */

    private fun handleClient(socket: Socket) {
        val client = RtspClient(
            socket = socket,
            output = BufferedOutputStream(socket.getOutputStream()),
            sessionId = UUID.randomUUID().toString().replace("-", "")
        )
        try {
            socket.soTimeout = 30_000
            val reader = socket.getInputStream().bufferedReader(StandardCharsets.UTF_8)
            while (running.get() && !socket.isClosed) {
                val requestLine = reader.readLine() ?: break
                if (requestLine.isBlank()) continue
                val headers = linkedMapOf<String, String>()
                while (true) {
                    val line = reader.readLine() ?: break
                    if (line.isEmpty()) break
                    val separator = line.indexOf(':')
                    if (separator > 0) {
                        headers[line.substring(0, separator).trim().lowercase(Locale.US)] =
                            line.substring(separator + 1).trim()
                    }
                }
                handleRequest(client, requestLine, headers)
                if (!running.get() || socket.isClosed) break
            }
        } catch (_: Exception) {
            // RTSP clients commonly disconnect without a TEARDOWN.
        } finally {
            removeClient(client)
        }
    }

    private fun handleRequest(
        client: RtspClient,
        requestLine: String,
        headers: Map<String, String>
    ) {
        val parts = requestLine.split(" ")
        val method = parts.getOrNull(0)?.uppercase(Locale.US).orEmpty()
        val target = parts.getOrNull(1).orEmpty()
        val cSeq = headers["cseq"] ?: "1"
        when (method) {
            "OPTIONS" -> writeRtspResponse(
                client,
                cSeq,
                extraHeaders = listOf("Public: OPTIONS, DESCRIBE, SETUP, PLAY, TEARDOWN")
            )
            "DESCRIBE" -> {
                val body = buildSdp(target)
                writeRtspResponse(
                    client,
                    cSeq,
                    extraHeaders = listOf(
                        "Content-Base: ${target.substringBeforeLast('/')}/",
                        "Content-Type: application/sdp",
                        "Content-Length: ${body.toByteArray(StandardCharsets.UTF_8).size}"
                    ),
                    body = body
                )
            }
            "SETUP" -> handleSetup(client, target, cSeq, headers["transport"].orEmpty())
            "PLAY" -> {
                if (!streamRequestHandler()) {
                    writeRtspResponse(client, cSeq, status = "503 Service Unavailable")
                    return
                }
                if (!clients.contains(client)) {
                    clients += client
                }
                client.playing = true
                client.socket.soTimeout = 0
                writeRtspResponse(
                    client,
                    cSeq,
                    extraHeaders = listOf(
                        "Session: ${client.sessionId}",
                        "RTP-Info: url=${target}/trackID=0;seq=${client.videoSequence};rtptime=0"
                    )
                )
                clientCountChanged()
                if (client.videoSetup) {
                    writeCachedVideoConfig(client)
                }
            }
            "TEARDOWN" -> {
                writeRtspResponse(client, cSeq, extraHeaders = listOf("Session: ${client.sessionId}"))
                removeClient(client)
            }
            else -> writeRtspResponse(client, cSeq, status = "405 Method Not Allowed")
        }
    }

    private fun handleSetup(
        client: RtspClient,
        target: String,
        cSeq: String,
        transport: String
    ) {
        val isAudio = target.contains("trackID=1", ignoreCase = true)
        if (isAudio && !audioAvailableProvider()) {
            writeRtspResponse(client, cSeq, status = "404 Not Found")
            return
        }
        val channels = parseInterleavedChannels(transport) ?: if (isAudio) 2 to 3 else 0 to 1
        if (isAudio) {
            client.audioRtpChannel = channels.first
            client.audioSetup = true
        } else {
            client.videoRtpChannel = channels.first
            client.videoSetup = true
        }
        writeRtspResponse(
            client,
            cSeq,
            extraHeaders = listOf(
                "Transport: RTP/AVP/TCP;unicast;interleaved=${channels.first}-${channels.second}",
                "Session: ${client.sessionId}"
            )
        )
    }

    private fun buildSdp(target: String): String {
        val base = target.substringBefore("?").trimEnd('/')
        return buildString {
            append("v=0\r\n")
            append("o=- 0 0 IN IP4 0.0.0.0\r\n")
            append("s=MultiLens IP Cam\r\n")
            append("c=IN IP4 0.0.0.0\r\n")
            append("t=0 0\r\n")
            append("a=control:*\r\n")
            append("m=video 0 RTP/AVP/TCP $H264_PAYLOAD_TYPE\r\n")
            append("a=rtpmap:$H264_PAYLOAD_TYPE H264/90000\r\n")
            append("a=fmtp:$H264_PAYLOAD_TYPE ${cachedSpropFmtp() ?: "packetization-mode=1"}\r\n")
            append("a=control:$base/trackID=0\r\n")
            if (audioAvailableProvider()) {
                append("m=audio 0 RTP/AVP/TCP $AAC_PAYLOAD_TYPE\r\n")
                append("a=rtpmap:$AAC_PAYLOAD_TYPE MPEG4-GENERIC/$AUDIO_SAMPLE_RATE/$AUDIO_CHANNELS\r\n")
                append("a=fmtp:$AAC_PAYLOAD_TYPE streamtype=5;profile-level-id=1;mode=AAC-hbr;config=1208;SizeLength=13;IndexLength=3;IndexDeltaLength=3\r\n")
                append("a=control:$base/trackID=1\r\n")
            }
        }
    }

    private fun writeRtspResponse(
        client: RtspClient,
        cSeq: String,
        status: String = "200 OK",
        extraHeaders: List<String> = emptyList(),
        body: String = ""
    ) {
        val response = buildString {
            append("RTSP/1.0 $status\r\n")
            append("CSeq: $cSeq\r\n")
            extraHeaders.forEach { append(it).append("\r\n") }
            if (body.isEmpty()) append("Content-Length: 0\r\n")
            append("\r\n")
            append(body)
        }
        writeClientBytes(client, response.toByteArray(StandardCharsets.UTF_8))
    }

    private fun writeH264Nal(client: RtspClient, nal: ByteArray, timestamp: Int, marker: Boolean) {
        if (nal.isEmpty()) return
        if (nal.size <= MAX_RTP_PAYLOAD_SIZE) {
            writeInterleavedRtp(
                client = client,
                channel = client.videoRtpChannel,
                payloadType = H264_PAYLOAD_TYPE,
                marker = marker,
                sequence = client.videoSequence++,
                timestamp = timestamp,
                ssrc = client.videoSsrc,
                payload = nal
            )
            return
        }

        val nalHeader = nal[0].toInt() and 0xFF
        val fuIndicator = ((nalHeader and 0xE0) or 28).toByte()
        val nalType = nalHeader and 0x1F
        var offset = 1
        while (offset < nal.size) {
            val remaining = nal.size - offset
            val chunkSize = minOf(MAX_RTP_PAYLOAD_SIZE - 2, remaining)
            val start = offset == 1
            val end = remaining == chunkSize
            val fuHeader = ((if (start) 0x80 else 0) or (if (end) 0x40 else 0) or nalType).toByte()
            val payload = ByteArray(chunkSize + 2)
            payload[0] = fuIndicator
            payload[1] = fuHeader
            nal.copyInto(payload, 2, offset, offset + chunkSize)
            writeInterleavedRtp(
                client = client,
                channel = client.videoRtpChannel,
                payloadType = H264_PAYLOAD_TYPE,
                marker = marker && end,
                sequence = client.videoSequence++,
                timestamp = timestamp,
                ssrc = client.videoSsrc,
                payload = payload
            )
            offset += chunkSize
        }
    }

    private fun writeInterleavedRtp(
        client: RtspClient,
        channel: Int,
        payloadType: Int,
        marker: Boolean,
        sequence: Int,
        timestamp: Int,
        ssrc: Int,
        payload: ByteArray
    ) {
        val packetSize = RTP_HEADER_SIZE + payload.size
        val packet = ByteArray(4 + packetSize)
        packet[0] = '$'.code.toByte()
        packet[1] = channel.toByte()
        packet[2] = ((packetSize shr 8) and 0xFF).toByte()
        packet[3] = (packetSize and 0xFF).toByte()
        packet[4] = 0x80.toByte()
        packet[5] = ((if (marker) 0x80 else 0) or payloadType).toByte()
        writeShort(packet, 6, sequence)
        writeInt(packet, 8, timestamp)
        writeInt(packet, 12, ssrc)
        payload.copyInto(packet, 16)
        writeClientBytes(client, packet)
    }

    private fun writeClientBytes(client: RtspClient, bytes: ByteArray) {
        synchronized(client.writeLock) {
            client.output.write(bytes)
            client.output.flush()
        }
    }

    private fun removeClient(client: RtspClient) {
        val removed = clients.remove(client)
        runCatching { client.socket.close() }
        if (removed) clientCountChanged()
    }

    private fun parseInterleavedChannels(transport: String): Pair<Int, Int>? {
        val value = transport
            .split(';')
            .firstOrNull { it.trim().startsWith("interleaved=", ignoreCase = true) }
            ?.substringAfter('=')
            ?: return null
        val first = value.substringBefore('-').toIntOrNull() ?: return null
        val second = value.substringAfter('-', "").toIntOrNull() ?: (first + 1)
        return first to second
    }

    private fun stripAdtsHeader(bytes: ByteArray): ByteArray {
        if (bytes.size <= ADTS_HEADER_SIZE) return bytes
        val hasAdts = bytes[0] == 0xFF.toByte() && (bytes[1].toInt() and 0xF0) == 0xF0
        return if (hasAdts) bytes.copyOfRange(ADTS_HEADER_SIZE, bytes.size) else bytes
    }

    private companion object {
        const val H264_PAYLOAD_TYPE = 96
        const val AAC_PAYLOAD_TYPE = 97
        const val AUDIO_SAMPLE_RATE = 44_100
        const val AUDIO_CHANNELS = 1
        const val RTP_HEADER_SIZE = 12
        const val ADTS_HEADER_SIZE = 7
        const val MAX_RTP_PAYLOAD_SIZE = 1_200
        const val TAG = "RtspServer"
        val ssrcCounter = AtomicInteger(0x13572468)

        fun nextSsrc(): Int = ssrcCounter.addAndGet(0x01010101)

        fun writeShort(out: ByteArray, offset: Int, value: Int) {
            out[offset] = ((value shr 8) and 0xFF).toByte()
            out[offset + 1] = (value and 0xFF).toByte()
        }

        fun writeInt(out: ByteArray, offset: Int, value: Int) {
            out[offset] = ((value ushr 24) and 0xFF).toByte()
            out[offset + 1] = ((value ushr 16) and 0xFF).toByte()
            out[offset + 2] = ((value ushr 8) and 0xFF).toByte()
            out[offset + 3] = (value and 0xFF).toByte()
        }

        fun splitAnnexBNalUnits(bytes: ByteArray): List<ByteArray> {
            val starts = mutableListOf<Int>()
            var index = 0
            while (index < bytes.size - 3) {
                val startCodeSize = startCodeSize(bytes, index)
                if (startCodeSize > 0) {
                    starts += index
                    index += startCodeSize
                } else {
                    index += 1
                }
            }
            if (starts.isEmpty()) return listOf(bytes)
            return starts.mapIndexedNotNull { i, start ->
                val nalStart = start + startCodeSize(bytes, start)
                val nalEnd = starts.getOrNull(i + 1) ?: bytes.size
                if (nalStart < nalEnd) bytes.copyOfRange(nalStart, nalEnd) else null
            }
        }

        fun startCodeSize(bytes: ByteArray, offset: Int): Int {
            if (
                offset + 3 < bytes.size &&
                bytes[offset] == 0.toByte() &&
                bytes[offset + 1] == 0.toByte() &&
                bytes[offset + 2] == 0.toByte() &&
                bytes[offset + 3] == 1.toByte()
            ) {
                return 4
            }
            if (
                offset + 2 < bytes.size &&
                bytes[offset] == 0.toByte() &&
                bytes[offset + 1] == 0.toByte() &&
                bytes[offset + 2] == 1.toByte()
            ) {
                return 3
            }
            return 0
        }
    }
}
