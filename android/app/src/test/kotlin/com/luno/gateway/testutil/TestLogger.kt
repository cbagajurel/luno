package com.luno.gateway.testutil

import com.luno.gateway.logging.LogLevel
import com.luno.gateway.logging.LogSink
import com.luno.gateway.logging.LunoLogger

private object NoopSink : LogSink {
    override fun write(level: LogLevel, tag: String, message: String, throwable: Throwable?) = Unit
}

fun testLogger(): LunoLogger = LunoLogger(NoopSink)
