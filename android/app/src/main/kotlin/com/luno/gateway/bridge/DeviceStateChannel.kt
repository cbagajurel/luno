package com.luno.gateway.bridge

import com.luno.gateway.telephony.DeviceStateStore
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.EventChannel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * The single coalesced telemetry stream. The channel carries only a revision
 * counter; structured [com.luno.gateway.model.DeviceState] travels over Pigeon
 * (`getDeviceState`). A subscription brings the device managers up via [onStart]
 * and stops them via [onStop] so nothing leaks when the dashboard goes away.
 */
class DeviceStateChannel(
    messenger: BinaryMessenger,
    private val store: DeviceStateStore,
    private val onStart: () -> Unit,
    private val onStop: () -> Unit,
) : EventChannel.StreamHandler {

    private val channel = EventChannel(messenger, CHANNEL_NAME)
    private var scope: CoroutineScope? = null

    fun attach() = channel.setStreamHandler(this)

    fun detach() {
        onCancel(null)
        channel.setStreamHandler(null)
    }

    override fun onListen(arguments: Any?, events: EventChannel.EventSink?) {
        onStart()
        val collectScope = CoroutineScope(Dispatchers.Main.immediate)
        scope = collectScope
        var revision = 0L
        collectScope.launch {
            store.state
                .onEach { events?.success(revision++) }
                .collect()
        }
    }

    override fun onCancel(arguments: Any?) {
        scope?.cancel()
        scope = null
        onStop()
    }

    companion object {
        const val CHANNEL_NAME = "com.luno.gateway/events/device_state"
    }
}
