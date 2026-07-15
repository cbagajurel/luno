package com.luno.gateway.bridge

import android.os.Handler
import android.os.Looper
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.EventChannel

/**
 * M2 bridge proof: a 1 Hz tick pushed to Dart. The ticker runs only while a
 * listener is attached, so nothing is produced before Dart subscribes; each new
 * subscription restarts from 0. The EventSink must be touched on the main thread.
 */
class FlutterEventBridge(messenger: BinaryMessenger) : EventChannel.StreamHandler {
    private val channel = EventChannel(messenger, CHANNEL_NAME)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var sink: EventChannel.EventSink? = null
    private var tick = 0L

    private val ticker = object : Runnable {
        override fun run() {
            sink?.success(tick++)
            mainHandler.postDelayed(this, TICK_INTERVAL_MS)
        }
    }

    fun attach() = channel.setStreamHandler(this)

    fun detach() {
        stopTicking()
        channel.setStreamHandler(null)
    }

    override fun onListen(arguments: Any?, events: EventChannel.EventSink?) {
        sink = events
        tick = 0L
        mainHandler.post(ticker)
    }

    override fun onCancel(arguments: Any?) = stopTicking()

    private fun stopTicking() {
        mainHandler.removeCallbacks(ticker)
        sink = null
    }

    companion object {
        const val CHANNEL_NAME = "com.luno.gateway/events/tick"
        private const val TICK_INTERVAL_MS = 1000L
    }
}
