package com.luno.gateway.backend

import com.luno.gateway.agent.ConnectionState
import com.luno.gateway.backend.protocol.Event
import com.luno.gateway.backend.ws.EventPublisher
import com.luno.gateway.testutil.FakeEventSink
import com.luno.gateway.testutil.testLogger
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EventPublisherTest {
    private fun event(from: String) = Event.SmsReceived(from = from, body = "hi", receivedAt = 1L)

    @Test
    fun `reliable event is sent once when ready and cleared on ack`() = runTest {
        val sink = FakeEventSink(ready = true)
        val publisher = EventPublisher(sink, backgroundScope, testLogger())

        publisher.reliable(event("A"), "rcv:A")
        assertEquals(listOf("rcv:A"), sink.idsFor(Event.SmsReceived.TYPE))
        assertEquals(1, publisher.outstandingCount())

        publisher.onBackendAck("rcv:A")
        assertEquals(0, publisher.outstandingCount())
    }

    @Test
    fun `an event buffered while offline is sent on the next READY`() = runTest(UnconfinedTestDispatcher()) {
        val sink = FakeEventSink(ready = false)
        val publisher = EventPublisher(sink, backgroundScope, testLogger())
        val state = MutableStateFlow(ConnectionState.CONNECTING)
        publisher.start(state)

        publisher.reliable(event("A"), "rcv:A")
        assertEquals(0, sink.events.size) // not sent yet — link not ready

        sink.ready = true
        state.value = ConnectionState.READY

        assertEquals(listOf("rcv:A"), sink.idsFor(Event.SmsReceived.TYPE))
    }

    @Test
    fun `unacked events resend with the same stable id after a reconnect`() = runTest(UnconfinedTestDispatcher()) {
        val sink = FakeEventSink(ready = true)
        val publisher = EventPublisher(sink, backgroundScope, testLogger())
        val state = MutableStateFlow(ConnectionState.READY)
        publisher.start(state)

        publisher.reliable(event("A"), "rcv:A")

        state.value = ConnectionState.BACKING_OFF
        state.value = ConnectionState.READY

        // Sent on first emit + resent once on the reconnect — both under the same id.
        assertEquals(listOf("rcv:A", "rcv:A"), sink.idsFor(Event.SmsReceived.TYPE))
    }

    @Test
    fun `an ack runs the follow-up exactly once`() = runTest {
        val sink = FakeEventSink(ready = true)
        val publisher = EventPublisher(sink, backgroundScope, testLogger())
        var acks = 0

        publisher.reliable(event("A"), "rcv:A", onAck = { acks++ })
        publisher.onBackendAck("rcv:A")
        publisher.onBackendAck("rcv:A") // duplicate ack: no-op

        assertEquals(1, acks)
    }

    @Test
    fun `ephemeral events are never buffered or resent`() = runTest(UnconfinedTestDispatcher()) {
        val sink = FakeEventSink(ready = true)
        val publisher = EventPublisher(sink, backgroundScope, testLogger())
        val state = MutableStateFlow(ConnectionState.READY)
        publisher.start(state)

        publisher.ephemeral(Event.Heartbeat(queueDepth = 0))
        assertEquals(0, publisher.outstandingCount())

        state.value = ConnectionState.BACKING_OFF
        state.value = ConnectionState.READY

        assertEquals(1, sink.events.size) // sent once, not resent
    }
}
