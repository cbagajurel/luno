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
 * Streams the agent's running state to Dart over an [EventChannel]. The source
 * of truth is [AgentController.state] (a StateFlow held for the whole process),
 * so this handler is a pure adapter: it collects the flow and forwards each
 * value's [com.luno.gateway.agent.AgentState.name] to Dart.
 *
 * Snapshot-then-stream: collecting a StateFlow immediately replays the current
 * value, so a Dart listener that attaches after the agent already started still
 * gets the correct initial state, then live updates.
 *
 * The collection runs on the main dispatcher because the [EventChannel.EventSink]
 * must be touched on the main thread. The scope lives exactly as long as the
 * subscription: created in [onListen], cancelled in [onCancel].
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
