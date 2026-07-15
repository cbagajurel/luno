package com.luno.gateway.bridge

import com.luno.gateway.telephony.SimInfoManager
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.EventChannel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * Notifies Dart that the SIM set changed, so the UI re-queries the typed
 * [com.luno.gateway.bridge.generated.LunoHostApi.getSimInfo]. Following the M2/M3
 * split, the EventChannel carries a lightweight signal (a revision counter);
 * the structured data travels over Pigeon.
 *
 * Lifecycle: a Dart subscription both starts SIM monitoring
 * ([SimInfoManager.start]) and observes [SimInfoManager.state]; the first emit is
 * the current snapshot (snapshot-then-stream). Cancelling stops monitoring so no
 * listener leaks when the dashboard goes away.
 */
class SimChangeChannel(
    messenger: BinaryMessenger,
    private val manager: SimInfoManager,
) : EventChannel.StreamHandler {

    private val channel = EventChannel(messenger, CHANNEL_NAME)
    private var scope: CoroutineScope? = null

    fun attach() = channel.setStreamHandler(this)

    fun detach() {
        onCancel(null)
        channel.setStreamHandler(null)
    }

    override fun onListen(arguments: Any?, events: EventChannel.EventSink?) {
        manager.start()
        val collectScope = CoroutineScope(Dispatchers.Main.immediate)
        scope = collectScope
        var revision = 0L
        collectScope.launch {
            manager.state
                .onEach { events?.success(revision++) }
                .collect()
        }
    }

    override fun onCancel(arguments: Any?) {
        scope?.cancel()
        scope = null
        manager.stop()
    }

    companion object {
        const val CHANNEL_NAME = "com.luno.gateway/events/sim"
    }
}
