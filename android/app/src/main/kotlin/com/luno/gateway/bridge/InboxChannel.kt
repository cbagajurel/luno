package com.luno.gateway.bridge

import com.luno.gateway.data.repository.InboxRepository
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.EventChannel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/** Ticks a revision counter whenever the inbox changes; the UI re-queries `getRecentInbox`. */
class InboxChannel(
    messenger: BinaryMessenger,
    private val inbox: InboxRepository,
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
        var revision = 0L
        collectScope.launch {
            inbox.observeRecent()
                .onEach { events?.success(revision++) }
                .collect()
        }
    }

    override fun onCancel(arguments: Any?) {
        scope?.cancel()
        scope = null
    }

    companion object {
        const val CHANNEL_NAME = "com.luno.gateway/events/inbox"
    }
}
