package com.luno.gateway.testutil

import com.luno.gateway.backend.protocol.Event
import com.luno.gateway.backend.ws.EventSink

/** Records what the agent tried to send, and lets a test flip readiness. */
class FakeEventSink(var ready: Boolean = true) : EventSink {
    data class SentEvent(val event: Event, val id: String)

    val events = mutableListOf<SentEvent>()
    val acks = mutableListOf<String>()

    override val isReady: Boolean get() = ready

    override fun sendEvent(event: Event, id: String): Boolean {
        if (!ready) return false
        events += SentEvent(event, id)
        return true
    }

    override fun sendAck(ackedId: String): Boolean {
        acks += ackedId
        return true
    }

    fun idsFor(type: String): List<String> = events.filter { it.event.type == type }.map { it.id }
}
