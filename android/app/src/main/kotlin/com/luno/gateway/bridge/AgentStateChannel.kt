package com.luno.gateway.bridge

import com.luno.gateway.agent.AgentController
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.EventChannel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * Streams the agent running state to Dart. Collecting the StateFlow replays the
 * current value first (snapshot-then-stream). Runs on the main dispatcher
 * because the EventSink must be touched on the main thread.
 */
class AgentStateChannel(
    messenger: BinaryMessenger,
    private val controller: AgentController,
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
            controller.state
                .onEach { events?.success(it.name) }
                .collect()
        }
    }

    override fun onCancel(arguments: Any?) {
        scope?.cancel()
        scope = null
    }

    companion object {
        const val CHANNEL_NAME = "com.luno.gateway/events/agent_state"
    }
}
