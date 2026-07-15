package com.luno.gateway.logging

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
