package com.opencode.multilensipcam

object WebMediaHeaders {
    fun snapshot(contentLength: Int): String {
        return buildString {
            append("HTTP/1.1 200 OK\r\n")
            append("Content-Type: image/jpeg\r\n")
            append("Content-Length: $contentLength\r\n")
            append("Connection: close\r\n")
            append("Cache-Control: no-store\r\n")
            append("\r\n")
        }
    }

    fun frozenSnapshot(contentLength: Long, snapshotId: String): String {
        return buildString {
            append("HTTP/1.1 200 OK\r\n")
            append("Content-Type: image/jpeg\r\n")
            append("Content-Length: $contentLength\r\n")
            append("Connection: close\r\n")
            append("Cache-Control: public, max-age=86400\r\n")
            append("ETag: \"snapshot-$snapshotId\"\r\n")
            append("\r\n")
        }
    }

    fun mjpegStream(boundary: String): String {
        return buildString {
            append("HTTP/1.1 200 OK\r\n")
            append("Connection: close\r\n")
            append("Cache-Control: no-store\r\n")
            append("Pragma: no-cache\r\n")
            append("Content-Type: multipart/x-mixed-replace; boundary=$boundary\r\n")
            append("\r\n")
        }
    }

    fun mjpegFrame(
        boundary: String,
        frameId: Long,
        frame: MjpegFrame,
        receivedAtElapsedMs: Long,
        frameAgeMs: Long
    ): String {
        return buildString {
            append("--$boundary\r\n")
            append("Content-Type: image/jpeg\r\n")
            append("Content-Length: ${frame.bytes.size}\r\n")
            append("X-Frame-Id: $frameId\r\n")
            append("X-Capture-Timestamp-Ms: ${frame.capturedAtElapsedMs}\r\n")
            append("X-Frame-Generated-Ms: ${frame.generatedAtElapsedMs}\r\n")
            append("X-Frame-Received-Ms: $receivedAtElapsedMs\r\n")
            append("X-Frame-Age-Ms: $frameAgeMs\r\n")
            append("X-Jpeg-Pipeline: ${frame.pipeline.controlValue}\r\n")
            append("X-Jpeg-Encode-Ms: ${frame.encodeDurationMs}\r\n")
            append("X-Yuv-To-Nv21-Ms: ${frame.yuvToNv21DurationMs}\r\n")
            append("X-Jpeg-Compress-Ms: ${frame.jpegCompressDurationMs}\r\n")
            append("X-Output-Transform-Ms: ${frame.outputTransformDurationMs}\r\n")
            append("X-Overlay-Render-Ms: ${frame.overlayRenderDurationMs}\r\n")
            append("X-Jpeg-Bytes: ${frame.bytes.size}\r\n")
            append("X-Jpeg-Width: ${frame.width}\r\n")
            append("X-Jpeg-Height: ${frame.height}\r\n")
            append("X-Output-Rotation-Degrees: ${frame.outputRotationDegrees}\r\n")
            append("X-Jpeg-Quality: ${frame.jpegQuality}\r\n")
            append("X-Target-Fps: ${frame.targetFps}\r\n")
            append("X-Source-Frames-Seen: ${frame.sourceFramesSeen}\r\n")
            append("X-Source-Frames-Encoded: ${frame.sourceFramesEncoded}\r\n")
            append("X-Source-Frames-Dropped: ${frame.sourceFramesDropped}\r\n\r\n")
        }
    }

    fun h264Stream(): String {
        return buildString {
            append("HTTP/1.1 200 OK\r\n")
            append("Connection: close\r\n")
            append("Cache-Control: no-store\r\n")
            append("Pragma: no-cache\r\n")
            append("Content-Type: video/h264\r\n")
            append("\r\n")
        }
    }

    fun aacStream(): String {
        return buildString {
            append("HTTP/1.1 200 OK\r\n")
            append("Connection: close\r\n")
            append("Cache-Control: no-store\r\n")
            append("Pragma: no-cache\r\n")
            append("Content-Type: audio/aac\r\n")
            append("\r\n")
        }
    }
}
