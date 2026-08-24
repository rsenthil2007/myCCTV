package com.opencode.multilensipcam

import org.json.JSONObject

data class WebDebugStreamSnapshot(
    val nowElapsedMs: Long,
    val serverRunning: Boolean,
    val serverStartedAtElapsedMs: Long,
    val streaming: Boolean,
    val cameraStreamDebug: CameraStreamDebugState?,
    val httpClients: Int,
    val mjpegClients: Int,
    val h264Clients: Int,
    val latestFrameId: Long?,
    val latestMjpegFrame: MjpegFrame?,
    val lastMjpegFrameReceivedAtElapsedMs: Long,
    val mjpegFramesReceived: Long,
    val mjpegFramesSent: Long,
    val mjpegBytesSent: Long,
    val mjpegWriteFailures: Long,
    val lastMjpegSendIntervalMs: Long,
    val lastMjpegSocketWriteMs: Long,
    val maxMjpegSocketWriteMs: Long,
    val lastMjpegFrameAgeMs: Long,
    val h264AccessUnitsReceived: Long,
    val h264BytesReceived: Long,
    val h264AccessUnitsSent: Long,
    val h264BytesSent: Long,
    val h264KeyFramesReceived: Long,
    val h264CodecConfigUnitsReceived: Long,
    val h264WriteFailures: Long,
    val lastH264UnitReceivedAtElapsedMs: Long,
    val lastH264SocketWriteMs: Long,
    val maxH264SocketWriteMs: Long,
    val audioClients: Int,
    val audioAccessUnitsReceived: Long,
    val audioBytesReceived: Long,
    val audioAccessUnitsSent: Long,
    val audioBytesSent: Long,
    val audioWriteFailures: Long,
    val lastAudioUnitReceivedAtElapsedMs: Long,
    val lastAudioSocketWriteMs: Long,
    val maxAudioSocketWriteMs: Long
)

object WebDebugStreamJson {
    fun build(snapshot: WebDebugStreamSnapshot): JSONObject {
        val latestMjpeg = snapshot.latestMjpegFrame
        return JSONObject().apply {
            put("nowElapsedMs", snapshot.nowElapsedMs)
            put("serverRunning", snapshot.serverRunning)
            put("serverUptimeMs", elapsedSince(snapshot.nowElapsedMs, snapshot.serverStartedAtElapsedMs))
            put("streaming", snapshot.streaming)
            put("cameraStream", snapshot.cameraStreamDebug?.let(::cameraStreamDebugJson))
            put("mjpegClients", snapshot.mjpegClients)
            put("h264Clients", snapshot.h264Clients)
            put("httpClients", snapshot.httpClients)
            put("mjpeg", JSONObject().apply {
                put("latestFrameId", snapshot.latestFrameId)
                put("latestFrameAgeMs", latestMjpeg?.let { snapshot.nowElapsedMs - it.generatedAtElapsedMs })
                put("latestFrameReceivedAgeMs", elapsedSince(snapshot.nowElapsedMs, snapshot.lastMjpegFrameReceivedAtElapsedMs))
                put("latestFrameBytes", latestMjpeg?.bytes?.size)
                put("latestFrameWidth", latestMjpeg?.width)
                put("latestFrameHeight", latestMjpeg?.height)
                put("latestOutputRotationDegrees", latestMjpeg?.outputRotationDegrees)
                put("latestJpegPipeline", latestMjpeg?.pipeline?.controlValue)
                put("latestJpegQuality", latestMjpeg?.jpegQuality)
                put("latestTargetFps", latestMjpeg?.targetFps)
                put("latestJpegEncodeMs", latestMjpeg?.encodeDurationMs)
                put("latestYuvToNv21Ms", latestMjpeg?.yuvToNv21DurationMs)
                put("latestJpegCompressMs", latestMjpeg?.jpegCompressDurationMs)
                put("latestOutputTransformMs", latestMjpeg?.outputTransformDurationMs)
                put("latestOverlayRenderMs", latestMjpeg?.overlayRenderDurationMs)
                put("framesReceived", snapshot.mjpegFramesReceived)
                put("framesSent", snapshot.mjpegFramesSent)
                put("bytesSent", snapshot.mjpegBytesSent)
                put("writeFailures", snapshot.mjpegWriteFailures)
                put("approxReceivedFps", rateSinceStart(snapshot.mjpegFramesReceived, snapshot.nowElapsedMs, snapshot.serverStartedAtElapsedMs))
                put("approxSentFps", rateSinceStart(snapshot.mjpegFramesSent, snapshot.nowElapsedMs, snapshot.serverStartedAtElapsedMs))
                put("lastSendIntervalMs", snapshot.lastMjpegSendIntervalMs)
                put("lastSocketWriteMs", snapshot.lastMjpegSocketWriteMs)
                put("maxSocketWriteMs", snapshot.maxMjpegSocketWriteMs)
                put("lastFrameAgeAtSendMs", snapshot.lastMjpegFrameAgeMs)
            })
            put("cameraSource", JSONObject().apply {
                put("framesSeen", latestMjpeg?.sourceFramesSeen)
                put("framesEncoded", latestMjpeg?.sourceFramesEncoded)
                put("framesDropped", latestMjpeg?.sourceFramesDropped)
                put("framesDroppedByFpsGate", latestMjpeg?.sourceFramesDroppedByFpsGate)
                put("framesDroppedByEncoderBusy", latestMjpeg?.sourceFramesDroppedByEncoderBusy)
            })
            put("h264", JSONObject().apply {
                put("accessUnitsReceived", snapshot.h264AccessUnitsReceived)
                put("bytesReceived", snapshot.h264BytesReceived)
                put("accessUnitsSent", snapshot.h264AccessUnitsSent)
                put("bytesSent", snapshot.h264BytesSent)
                put("keyFramesReceived", snapshot.h264KeyFramesReceived)
                put("codecConfigUnitsReceived", snapshot.h264CodecConfigUnitsReceived)
                put("writeFailures", snapshot.h264WriteFailures)
                put("lastUnitAgeMs", elapsedSince(snapshot.nowElapsedMs, snapshot.lastH264UnitReceivedAtElapsedMs))
                put("lastSocketWriteMs", snapshot.lastH264SocketWriteMs)
                put("maxSocketWriteMs", snapshot.maxH264SocketWriteMs)
            })
            put("audio", JSONObject().apply {
                put("clients", snapshot.audioClients)
                put("accessUnitsReceived", snapshot.audioAccessUnitsReceived)
                put("bytesReceived", snapshot.audioBytesReceived)
                put("accessUnitsSent", snapshot.audioAccessUnitsSent)
                put("bytesSent", snapshot.audioBytesSent)
                put("writeFailures", snapshot.audioWriteFailures)
                put("lastUnitAgeMs", elapsedSince(snapshot.nowElapsedMs, snapshot.lastAudioUnitReceivedAtElapsedMs))
                put("lastSocketWriteMs", snapshot.lastAudioSocketWriteMs)
                put("maxSocketWriteMs", snapshot.maxAudioSocketWriteMs)
            })
        }
    }

    private fun elapsedSince(nowMs: Long, thenMs: Long): Long? {
        return if (thenMs > 0L) nowMs - thenMs else null
    }

    private fun rateSinceStart(count: Long, nowMs: Long, startedAtMs: Long): Double {
        val uptimeMs = elapsedSince(nowMs, startedAtMs) ?: return 0.0
        if (uptimeMs <= 0L) return 0.0
        return count * 1000.0 / uptimeMs
    }

    private fun cameraStreamDebugJson(debug: CameraStreamDebugState): JSONObject {
        return JSONObject().apply {
            put("cameraId", debug.cameraId)
            put("physicalCameraId", debug.physicalCameraId)
            put("outputSize", debug.outputSize)
            put("mjpegSize", debug.mjpegSize)
            put("targetFps", debug.targetFps)
            put("targetFpsRange", debug.targetFpsRange)
            put("h264Enabled", debug.h264Enabled)
            put("mjpegPipeline", debug.mjpegPipeline)
            put("highSpeedRequested", debug.highSpeedRequested)
            put("highSpeedActive", debug.highSpeedActive)
            put("sessionMode", debug.sessionMode)
            put("sessionModeReason", debug.sessionModeReason)
            put("requestTemplate", debug.requestTemplate)
            put("targetSurfaces", org.json.JSONArray(debug.targetSurfaces))
            put("requestedZoomRatio", debug.requestedZoomRatio)
            put("appliedZoomRatio", debug.appliedZoomRatio)
            put("cropRegion", debug.cropRegion)
            put("activeArraySize", debug.activeArraySize)
            put("videoStabilizationMode", debug.videoStabilizationMode)
        }
    }
}
