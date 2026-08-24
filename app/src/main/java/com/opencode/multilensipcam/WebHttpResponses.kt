package com.opencode.multilensipcam

import java.io.OutputStream
import java.nio.charset.StandardCharsets

object WebHttpResponses {
    fun writeText(output: OutputStream, body: String, contentType: String) {
        val bytes = body.toByteArray(StandardCharsets.UTF_8)
        val header = buildString {
            append("HTTP/1.1 200 OK\r\n")
            append("Content-Type: $contentType\r\n")
            append("Content-Length: ${bytes.size}\r\n")
            append("Connection: close\r\n")
            append("Cache-Control: no-store\r\n")
            append("\r\n")
        }
        output.write(header.toByteArray(StandardCharsets.UTF_8))
        var offset = 0
        while (offset < bytes.size) {
            val count = minOf(TEXT_BODY_CHUNK_BYTES, bytes.size - offset)
            output.write(bytes, offset, count)
            output.flush()
            offset += count
        }
        output.flush()
    }

    fun writeNotFound(output: OutputStream) {
        val message = "Not found"
        val bytes = message.toByteArray(StandardCharsets.UTF_8)
        val header = buildString {
            append("HTTP/1.1 404 Not Found\r\n")
            append("Content-Type: text/plain; charset=utf-8\r\n")
            append("Content-Length: ${bytes.size}\r\n")
            append("Connection: close\r\n")
            append("\r\n")
        }
        output.write(header.toByteArray(StandardCharsets.UTF_8))
        output.write(bytes)
        output.flush()
    }

    fun writeServiceUnavailable(output: OutputStream, message: String) {
        val bytes = message.toByteArray(StandardCharsets.UTF_8)
        val header = buildString {
            append("HTTP/1.1 503 Service Unavailable\r\n")
            append("Content-Type: text/plain; charset=utf-8\r\n")
            append("Content-Length: ${bytes.size}\r\n")
            append("Connection: close\r\n")
            append("\r\n")
        }
        output.write(header.toByteArray(StandardCharsets.UTF_8))
        output.write(bytes)
        output.flush()
    }

    fun writeRedirect(output: OutputStream, location: String) {
        val header = buildString {
            append("HTTP/1.1 302 Found\r\n")
            append("Location: $location\r\n")
            append("Content-Length: 0\r\n")
            append("Connection: close\r\n")
            append("Cache-Control: no-store\r\n")
            append("\r\n")
        }
        output.write(header.toByteArray(StandardCharsets.UTF_8))
        output.flush()
    }

    private const val TEXT_BODY_CHUNK_BYTES = 4 * 1024
}
