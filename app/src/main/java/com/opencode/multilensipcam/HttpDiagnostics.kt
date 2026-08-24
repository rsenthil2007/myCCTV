package com.opencode.multilensipcam

import android.os.SystemClock
import java.io.OutputStream
import java.util.ArrayDeque
import java.util.concurrent.atomic.AtomicLong

class HttpDiagnostics(private val capacity: Int = DEFAULT_CAPACITY) {
    class Trace(
        val id: Long,
        val startedWallTimeMs: Long,
        val startedElapsedMs: Long,
        val remoteAddress: String,
        val target: String,
        val route: String
    ) {
        private val responseBytes = AtomicLong(0L)
        private val writeDurationMs = AtomicLong(0L)
        private val lastProgressElapsedMs = AtomicLong(startedElapsedMs)
        private val currentWriteStartedElapsedMs = AtomicLong(0L)

        fun markWriteStarted() {
            currentWriteStartedElapsedMs.set(SystemClock.elapsedRealtime())
        }

        fun markWriteFinished(bytes: Int, durationMs: Long) {
            if (bytes > 0) {
                responseBytes.addAndGet(bytes.toLong())
            }
            writeDurationMs.addAndGet(durationMs)
            lastProgressElapsedMs.set(SystemClock.elapsedRealtime())
            currentWriteStartedElapsedMs.set(0L)
        }

        fun responseBytes(): Long = responseBytes.get()

        fun writeDurationMs(): Long = writeDurationMs.get()

        fun activeEntry(nowElapsedMs: Long): ActiveEntry {
            val lastProgressMs = lastProgressElapsedMs.get()
            val currentWriteStartedMs = currentWriteStartedElapsedMs.get()
            return ActiveEntry(
                id = id,
                startedWallTimeMs = startedWallTimeMs,
                startedElapsedMs = startedElapsedMs,
                nowElapsedMs = nowElapsedMs,
                lastProgressElapsedMs = lastProgressMs,
                currentWriteElapsedMs = if (currentWriteStartedMs > 0L) nowElapsedMs - currentWriteStartedMs else null,
                remoteAddress = remoteAddress,
                target = target,
                route = route,
                responseBytes = responseBytes.get(),
                writeDurationMs = writeDurationMs.get()
            )
        }
    }

    data class ActiveEntry(
        val id: Long,
        val startedWallTimeMs: Long,
        val startedElapsedMs: Long,
        val nowElapsedMs: Long,
        val lastProgressElapsedMs: Long,
        val currentWriteElapsedMs: Long?,
        val remoteAddress: String,
        val target: String,
        val route: String,
        val responseBytes: Long,
        val writeDurationMs: Long
    ) {
        fun toTextLine(): String {
            return buildString {
                append("#").append(id)
                append(" elapsedMs=").append(nowElapsedMs - startedElapsedMs)
                append(" sinceProgressMs=").append(nowElapsedMs - lastProgressElapsedMs)
                append(" currentWriteMs=").append(currentWriteElapsedMs?.toString() ?: "n/a")
                append(" startWallMs=").append(startedWallTimeMs)
                append(" startElapsedMs=").append(startedElapsedMs)
                append(" remote=").append(remoteAddress.ifBlank { "unknown" })
                append(" route=").append(route)
                append(" target=").append(target)
                append(" responseBytes=").append(responseBytes)
                append(" writeMs=").append(writeDurationMs)
            }
        }
    }

    data class Entry(
        val id: Long,
        val event: String,
        val startedWallTimeMs: Long,
        val startedElapsedMs: Long,
        val eventElapsedMs: Long,
        val remoteAddress: String,
        val target: String,
        val route: String,
        val responseBytes: Long?,
        val totalDurationMs: Long?,
        val writeDurationMs: Long?,
        val failure: String?
    ) {
        fun toTextLine(nowElapsedMs: Long): String {
            val ageMs = nowElapsedMs - eventElapsedMs
            return buildString {
                append("#").append(id)
                append(" event=").append(event)
                append(" ageMs=").append(ageMs)
                append(" startWallMs=").append(startedWallTimeMs)
                append(" startElapsedMs=").append(startedElapsedMs)
                append(" remote=").append(remoteAddress.ifBlank { "unknown" })
                append(" route=").append(route)
                append(" target=").append(target)
                append(" responseBytes=").append(responseBytes?.toString() ?: "n/a")
                append(" totalMs=").append(totalDurationMs?.toString() ?: "n/a")
                append(" writeMs=").append(writeDurationMs?.toString() ?: "n/a")
                failure?.let { append(" failure=").append(it) }
            }
        }
    }

    class CountingOutputStream(
        private val delegate: OutputStream,
        private val trace: Trace? = null
    ) : OutputStream() {
        var bytesWritten: Long = 0
            private set

        var writeDurationMs: Long = 0
            private set

        override fun write(b: Int) {
            trace?.markWriteStarted()
            val startedAtMs = SystemClock.elapsedRealtime()
            try {
                delegate.write(b)
                recordProgress(bytes = 1, durationMs = SystemClock.elapsedRealtime() - startedAtMs)
            } catch (exception: Throwable) {
                trace?.markWriteFinished(bytes = 0, durationMs = SystemClock.elapsedRealtime() - startedAtMs)
                throw exception
            }
        }

        override fun write(b: ByteArray) {
            write(b, 0, b.size)
        }

        override fun write(b: ByteArray, off: Int, len: Int) {
            trace?.markWriteStarted()
            val startedAtMs = SystemClock.elapsedRealtime()
            try {
                delegate.write(b, off, len)
                recordProgress(bytes = len, durationMs = SystemClock.elapsedRealtime() - startedAtMs)
            } catch (exception: Throwable) {
                trace?.markWriteFinished(bytes = 0, durationMs = SystemClock.elapsedRealtime() - startedAtMs)
                throw exception
            }
        }

        override fun flush() {
            trace?.markWriteStarted()
            val startedAtMs = SystemClock.elapsedRealtime()
            try {
                delegate.flush()
                recordProgress(bytes = 0, durationMs = SystemClock.elapsedRealtime() - startedAtMs)
            } catch (exception: Throwable) {
                trace?.markWriteFinished(bytes = 0, durationMs = SystemClock.elapsedRealtime() - startedAtMs)
                throw exception
            }
        }

        override fun close() {
            delegate.close()
        }

        private fun recordProgress(bytes: Int, durationMs: Long) {
            bytesWritten += bytes.toLong()
            writeDurationMs += durationMs
            trace?.markWriteFinished(bytes, durationMs)
        }
    }

    private val nextId = AtomicLong(0L)
    private val entries = ArrayDeque<Entry>(capacity)
    private val activeTraces = LinkedHashMap<Long, Trace>()
    private val lock = Any()

    fun reset() {
        synchronized(lock) {
            entries.clear()
            activeTraces.clear()
        }
        nextId.set(0L)
    }

    fun begin(
        startedWallTimeMs: Long,
        startedElapsedMs: Long,
        remoteAddress: String,
        target: String,
        route: String
    ): Trace {
        return Trace(
            id = nextId.incrementAndGet(),
            startedWallTimeMs = startedWallTimeMs,
            startedElapsedMs = startedElapsedMs,
            remoteAddress = remoteAddress,
            target = target,
            route = route
        )
    }

    fun recordStart(trace: Trace) {
        synchronized(lock) {
            activeTraces[trace.id] = trace
        }
        append(trace, event = "start", responseBytes = null, writeDurationMs = null, failure = null)
    }

    fun recordEnd(trace: Trace, failure: Throwable?) {
        removeActive(trace)
        append(trace, event = if (failure == null) "end" else "failure", responseBytes = null, writeDurationMs = null, failure = failure)
    }

    fun recordComplete(trace: Trace, output: CountingOutputStream?, failure: Throwable?) {
        removeActive(trace)
        append(
            trace = trace,
            event = if (failure == null) "complete" else "failure",
            responseBytes = output?.bytesWritten ?: trace.responseBytes(),
            writeDurationMs = output?.writeDurationMs ?: trace.writeDurationMs(),
            failure = failure
        )
    }

    fun snapshot(): List<Entry> {
        return synchronized(lock) {
            entries.toList()
        }
    }

    fun activeSnapshot(nowElapsedMs: Long): List<ActiveEntry> {
        return synchronized(lock) {
            activeTraces.values.map { trace -> trace.activeEntry(nowElapsedMs) }
        }
    }

    private fun removeActive(trace: Trace) {
        synchronized(lock) {
            activeTraces.remove(trace.id)
        }
    }

    private fun append(
        trace: Trace,
        event: String,
        responseBytes: Long?,
        writeDurationMs: Long?,
        failure: Throwable?
    ) {
        val nowMs = SystemClock.elapsedRealtime()
        val entry = Entry(
            id = trace.id,
            event = event,
            startedWallTimeMs = trace.startedWallTimeMs,
            startedElapsedMs = trace.startedElapsedMs,
            eventElapsedMs = nowMs,
            remoteAddress = trace.remoteAddress,
            target = trace.target,
            route = trace.route,
            responseBytes = responseBytes,
            totalDurationMs = nowMs - trace.startedElapsedMs,
            writeDurationMs = writeDurationMs,
            failure = failure?.diagnosticMessage()
        )
        synchronized(lock) {
            while (entries.size >= capacity) {
                entries.removeFirst()
            }
            entries.addLast(entry)
        }
    }

    private fun Throwable.diagnosticMessage(): String {
        val type = this::class.java.simpleName.ifBlank { "Exception" }
        val message = message?.takeIf { it.isNotBlank() }
        return if (message == null) type else "$type:${message.sanitizeForLine()}"
    }

    private fun String.sanitizeForLine(): String {
        return replace('\r', ' ')
            .replace('\n', ' ')
            .let { value ->
                if (value.length <= MAX_FAILURE_CHARS) value else value.substring(0, MAX_FAILURE_CHARS)
            }
    }

    companion object {
        const val DEFAULT_CAPACITY = 96
        private const val MAX_FAILURE_CHARS = 160
    }
}
