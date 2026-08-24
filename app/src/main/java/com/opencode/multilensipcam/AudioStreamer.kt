package com.opencode.multilensipcam

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaRecorder
import android.os.SystemClock
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

enum class AudioStartResult {
    STARTED,
    ALREADY_RUNNING,
    RETRY_AFTER_PREVIOUS_STOP
}

class AudioStreamer(
    private val onAccessUnit: (AacAccessUnit) -> Unit,
    private val onStatus: (String) -> Unit
) {
    private val lifecycleLock = Object()
    private val running = AtomicBoolean(false)
    private val ready = AtomicBoolean(false)

    @Volatile
    private var executor: ExecutorService? = null

    @Volatile
    private var activeSessionId: Long = 0

    @Volatile
    private var audioRecord: AudioRecord? = null

    @Volatile
    private var codec: MediaCodec? = null

    val isRunning: Boolean
        get() = running.get()

    val isReady: Boolean
        get() = ready.get()

    fun start(config: AudioStreamConfig = AudioStreamConfig()): AudioStartResult {
        val sessionExecutor: ExecutorService
        val sessionId: Long
        synchronized(lifecycleLock) {
            if (running.get()) return AudioStartResult.ALREADY_RUNNING
            val previousExecutor = executor
            if (previousExecutor != null && !previousExecutor.isTerminated) {
                onStatus("Audio stopping; retry shortly")
                return AudioStartResult.RETRY_AFTER_PREVIOUS_STOP
            }
            if (previousExecutor != null && previousExecutor.isTerminated) {
                executor = null
            }
            sessionId = activeSessionId + 1
            activeSessionId = sessionId
            running.set(true)
            ready.set(false)
            sessionExecutor = Executors.newSingleThreadExecutor()
            executor = sessionExecutor
        }
        return try {
            sessionExecutor.execute { runAudioLoop(config, sessionId, sessionExecutor) }
            AudioStartResult.STARTED
        } catch (_: RejectedExecutionException) {
            synchronized(lifecycleLock) {
                if (executor === sessionExecutor) {
                    running.set(false)
                    ready.set(false)
                    executor = null
                }
            }
            AudioStartResult.RETRY_AFTER_PREVIOUS_STOP
        }
    }

    fun stop() {
        val currentExecutor: ExecutorService?
        synchronized(lifecycleLock) {
            running.set(false)
            ready.set(false)
            currentExecutor = executor
        }
        runCatching { audioRecord?.stop() }
        runCatching { codec?.stop() }
        runCatching { currentExecutor?.shutdownNow() }
        runCatching { currentExecutor?.awaitTermination(STOP_WAIT_MS, TimeUnit.MILLISECONDS) }
        synchronized(lifecycleLock) {
            if (executor === currentExecutor && currentExecutor?.isTerminated == true) {
                executor = null
            }
        }
    }

    @Suppress("MissingPermission")
    private fun runAudioLoop(
        config: AudioStreamConfig,
        sessionId: Long,
        sessionExecutor: ExecutorService
    ) {
        val bufferInfo = MediaCodec.BufferInfo()
        var localRecord: AudioRecord? = null
        var localCodec: MediaCodec? = null
        try {
            val minBufferSize = AudioRecord.getMinBufferSize(
                config.sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            if (minBufferSize <= 0) {
                onStatus("Audio unavailable: invalid microphone buffer")
                return
            }
            val bufferSize = maxOf(minBufferSize * 2, config.sampleRate / 10 * 2)
            localRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                config.sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )
            if (localRecord.state != AudioRecord.STATE_INITIALIZED) {
                onStatus("Audio unavailable: microphone failed to initialize")
                return
            }

            val format = MediaFormat.createAudioFormat(
                MediaFormat.MIMETYPE_AUDIO_AAC,
                config.sampleRate,
                config.channelCount
            ).apply {
                setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                setInteger(MediaFormat.KEY_BIT_RATE, config.bitrate)
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, bufferSize)
            }
            localCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
            localCodec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            localCodec.start()
            audioRecord = localRecord
            codec = localCodec

            val pcm = ByteArray(bufferSize)
            localRecord.startRecording()
            onStatus("Audio capture starting")

            while (isSessionRunning(sessionId)) {
                val read = localRecord.read(pcm, 0, pcm.size)
                when {
                    read > 0 -> queuePcm(localCodec, pcm, read)
                    read == 0 -> Thread.sleep(10)
                    else -> {
                        if (isSessionRunning(sessionId)) {
                            onStatus("Audio stopped after microphone read error")
                        }
                        break
                    }
                }
                drainEncoder(localCodec, bufferInfo, config, sessionId)
            }
            drainEncoder(localCodec, bufferInfo, config, sessionId)
        } catch (_: Exception) {
            if (isSessionRunning(sessionId)) {
                onStatus("Audio stopped after encoder error")
            }
        } finally {
            runCatching { localRecord?.stop() }
            runCatching { localRecord?.release() }
            runCatching { localCodec?.stop() }
            runCatching { localCodec?.release() }
            synchronized(lifecycleLock) {
                if (activeSessionId == sessionId) {
                    running.set(false)
                    ready.set(false)
                }
                if (audioRecord === localRecord) audioRecord = null
                if (codec === localCodec) codec = null
                if (executor === sessionExecutor) {
                    executor = null
                }
            }
            sessionExecutor.shutdown()
        }
    }

    private fun queuePcm(codec: MediaCodec, pcm: ByteArray, length: Int) {
        val index = codec.dequeueInputBuffer(10_000)
        if (index < 0) return
        val inputBuffer = codec.getInputBuffer(index) ?: return
        inputBuffer.clear()
        inputBuffer.put(pcm, 0, length)
        codec.queueInputBuffer(
            index,
            0,
            length,
            SystemClock.elapsedRealtimeNanos() / 1_000,
            0
        )
    }

    private fun drainEncoder(
        codec: MediaCodec,
        bufferInfo: MediaCodec.BufferInfo,
        config: AudioStreamConfig,
        sessionId: Long
    ) {
        while (isSessionRunning(sessionId)) {
            val index = codec.dequeueOutputBuffer(bufferInfo, 0)
            when {
                index == MediaCodec.INFO_TRY_AGAIN_LATER -> return
                index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> Unit
                index >= 0 -> {
                    val isCodecConfig = (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0
                    val outputBuffer = codec.getOutputBuffer(index)
                    if (!isCodecConfig && outputBuffer != null && bufferInfo.size > 0) {
                        outputBuffer.position(bufferInfo.offset)
                        outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                        val payload = ByteArray(bufferInfo.size)
                        outputBuffer.get(payload)
                        onAccessUnit(
                            AacAccessUnit(
                                bytes = withAdtsHeader(payload, config),
                                presentationTimeUs = bufferInfo.presentationTimeUs
                            )
                        )
                        if (isSessionRunning(sessionId) && ready.compareAndSet(false, true)) {
                            onStatus("Audio streaming")
                        }
                    }
                    codec.releaseOutputBuffer(index, false)
                }
                else -> return
            }
        }
    }

    private fun withAdtsHeader(payload: ByteArray, config: AudioStreamConfig): ByteArray {
        val packetLength = payload.size + ADTS_HEADER_LENGTH
        val output = ByteArray(packetLength)
        val profile = 2
        val frequencyIndex = config.frequencyIndex
        val channelConfig = config.channelCount.coerceIn(1, 7)
        output[0] = 0xFF.toByte()
        output[1] = 0xF1.toByte()
        output[2] = (((profile - 1) shl 6) or (frequencyIndex shl 2) or (channelConfig shr 2)).toByte()
        output[3] = (((channelConfig and 3) shl 6) or (packetLength shr 11)).toByte()
        output[4] = ((packetLength and 0x7FF) shr 3).toByte()
        output[5] = (((packetLength and 7) shl 5) or 0x1F).toByte()
        output[6] = 0xFC.toByte()
        payload.copyInto(output, ADTS_HEADER_LENGTH)
        return output
    }

    private fun isSessionRunning(sessionId: Long): Boolean {
        return running.get() && activeSessionId == sessionId
    }

    private companion object {
        const val ADTS_HEADER_LENGTH = 7
        const val STOP_WAIT_MS = 1_500L
    }
}
