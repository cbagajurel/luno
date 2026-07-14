package com.luno.gateway.bridge

import android.os.Handler
import android.os.Looper
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.EventChannel

/**
 * Pushes native-originated events to Dart over a hand-written [EventChannel]
 * (Pigeon carries only request/response calls; streams go here so the
 * subscribe/cancel lifecycle is explicit).
 *
 * M2 payload is a monotonically increasing tick emitted once per second, to
 * prove Kotlin->Dart push works and arrives in order.
 *
 * Buffer/drop policy: the ticker runs ONLY while a Dart listener is attached —
 * started in [onListen], stopped in [onCancel] — so no event is produced before
 * Dart subscribes; there is nothing to buffer or drop. Each new subscription
 * restarts the count from 0.
 *
 * The [EventChannel.EventSink] must be touched on the main thread, so ticks are
 * driven by a main-looper [Handler].
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

    /** Registers this handler on the channel. Call once the engine is attached. */
    fun attach() = channel.setStreamHandler(this)

    /** Stops ticking and unregisters. Call on engine detach to avoid leaks. */
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
