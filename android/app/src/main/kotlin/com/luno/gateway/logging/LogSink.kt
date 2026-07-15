package com.luno.gateway.logging

import android.util.Log

enum class LogLevel { DEBUG, INFO, WARN, ERROR }

interface LogSink {
    fun write(level: LogLevel, tag: String, message: String, throwable: Throwable?)
}

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
