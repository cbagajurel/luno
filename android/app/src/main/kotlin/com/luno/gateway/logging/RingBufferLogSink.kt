package com.luno.gateway.logging

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class LogRecord(
    val timestampMs: Long,
    val level: LogLevel,
    val tag: String,
    val message: String,
)

/**
 * Keeps the most recent [capacity] log lines in memory so the UI can render them.
 * Messages arrive already-redacted (see [LunoLogger.emit]); this sink stores them verbatim.
 */
class RingBufferLogSink(
    private val capacity: Int = 500,
    private val now: () -> Long = System::currentTimeMillis,
) : LogSink {
    private val buffer = ArrayDeque<LogRecord>(capacity)
    private val lock = Any()
    private val _revision = MutableStateFlow(0L)
    val revision: StateFlow<Long> = _revision

    override fun write(level: LogLevel, tag: String, message: String, throwable: Throwable?) {
        val text = if (throwable != null) "$message\n${throwable.stackTraceToString()}" else message
        synchronized(lock) {
            if (buffer.size >= capacity) buffer.removeFirst()
            buffer.addLast(LogRecord(now(), level, tag, text))
        }
        _revision.value += 1
    }

    /** Newest-first snapshot of the buffered records. */
    fun snapshot(): List<LogRecord> = synchronized(lock) { buffer.toList() }.asReversed()
}
