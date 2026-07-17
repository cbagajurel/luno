package com.luno.gateway.bridge

import com.luno.gateway.logging.RingBufferLogSink
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.EventChannel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/** Ticks a revision counter whenever a log line is written; the UI re-queries `getRecentLogs`. */
class LogChannel(
    messenger: BinaryMessenger,
    private val logBuffer: RingBufferLogSink,
) : EventChannel.StreamHandler {

    private val channel = EventChannel(messenger, CHANNEL_NAME)
    private var scope: CoroutineScope? = null

    fun attach() = channel.setStreamHandler(this)

    fun detach() {
        onCancel(null)
        channel.setStreamHandler(null)
    }

    override fun onListen(arguments: Any?, events: EventChannel.EventSink?) {
        val collectScope = CoroutineScope(Dispatchers.Main.immediate)
        scope = collectScope
        collectScope.launch {
            logBuffer.revision
                .onEach { events?.success(it) }
                .collect()
        }
    }

    override fun onCancel(arguments: Any?) {
        scope?.cancel()
        scope = null
    }

    companion object {
        const val CHANNEL_NAME = "com.luno.gateway/events/logs"
    }
}
