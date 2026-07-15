package com.luno.gateway.logging

/**
 * The single structured-logging facade for the native agent. Everything logs
 * through here rather than calling [android.util.Log] directly, so sinks and
 * redaction are configured in one place.
 *
 * M3 fans out to a single [LogcatSink]. The [redactor] hook is where PII
 * scrubbing lands in M16 (docs/folder-structure.md `logging/Redaction.kt`); for
 * now it is identity. Records are dispatched to every sink in registration
 * order.
 */
class LunoLogger(
    private val sinks: List<LogSink>,
    private val redactor: (String) -> String = { it },
) {
    constructor(sink: LogSink) : this(listOf(sink))

    fun d(tag: String, message: String, throwable: Throwable? = null) =
        emit(LogLevel.DEBUG, tag, message, throwable)

    fun i(tag: String, message: String, throwable: Throwable? = null) =
        emit(LogLevel.INFO, tag, message, throwable)

    fun w(tag: String, message: String, throwable: Throwable? = null) =
        emit(LogLevel.WARN, tag, message, throwable)

    fun e(tag: String, message: String, throwable: Throwable? = null) =
        emit(LogLevel.ERROR, tag, message, throwable)

    private fun emit(level: LogLevel, tag: String, message: String, throwable: Throwable?) {
        val safe = redactor(message)
        sinks.forEach { it.write(level, tag, safe, throwable) }
    }
}
