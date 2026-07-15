package com.luno.gateway.logging

import android.util.Log

/** Severity of a log record, ordered from most to least verbose. */
enum class LogLevel { DEBUG, INFO, WARN, ERROR }

/**
 * A destination for structured log records. The M3 slice ships only
 * [LogcatSink]; later milestones add Room ring-buffer and backend sinks
 * (see docs/folder-structure.md `logging/`) without changing call sites.
 */
interface LogSink {
    fun write(level: LogLevel, tag: String, message: String, throwable: Throwable?)
}

/** Writes to Android logcat via [android.util.Log]. */
class LogcatSink : LogSink {
    override fun write(level: LogLevel, tag: String, message: String, throwable: Throwable?) {
        when (level) {
            LogLevel.DEBUG -> Log.d(tag, message, throwable)
            LogLevel.INFO -> Log.i(tag, message, throwable)
            LogLevel.WARN -> Log.w(tag, message, throwable)
            LogLevel.ERROR -> Log.e(tag, message, throwable)
        }
    }
}
