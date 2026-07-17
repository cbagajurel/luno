package com.luno.gateway.backend

import com.luno.gateway.agent.ConnectionState
import com.luno.gateway.backend.protocol.Event
import com.luno.gateway.backend.ws.EventPublisher
import com.luno.gateway.testutil.FakeEventOutboxDao
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
        val dao = FakeEventOutboxDao()
        val publisher = EventPublisher(sink, backgroundScope, testLogger(), dao = dao)

        publisher.reliable(event("A"), "rcv:A")
        assertEquals(listOf("rcv:A"), sink.idsFor(Event.SmsReceived.TYPE))
        assertEquals(1, publisher.outstandingCount())

        publisher.onBackendAck("rcv:A")
        assertEquals(0, publisher.outstandingCount())
    }

    @Test
    fun `an event buffered while offline is sent on the next READY`() = runTest(UnconfinedTestDispatcher()) {
        val sink = FakeEventSink(ready = false)
        val publisher = EventPublisher(sink, backgroundScope, testLogger(), dao = FakeEventOutboxDao())
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
        val publisher = EventPublisher(sink, backgroundScope, testLogger(), dao = FakeEventOutboxDao())
        val state = MutableStateFlow(ConnectionState.READY)
        publisher.start(state)

        publisher.reliable(event("A"), "rcv:A")

        state.value = ConnectionState.BACKING_OFF
        state.value = ConnectionState.READY

        // Sent on first emit + resent once on the reconnect — both under the same id.
        assertEquals(listOf("rcv:A", "rcv:A"), sink.idsFor(Event.SmsReceived.TYPE))
    }

    @Test
    fun `an acked event is not resent after a later reconnect`() = runTest(UnconfinedTestDispatcher()) {
        val sink = FakeEventSink(ready = true)
        val publisher = EventPublisher(sink, backgroundScope, testLogger(), dao = FakeEventOutboxDao())
        val state = MutableStateFlow(ConnectionState.READY)
        publisher.start(state)

        publisher.reliable(event("A"), "rcv:A")
        publisher.onBackendAck("rcv:A")

        state.value = ConnectionState.BACKING_OFF
        state.value = ConnectionState.READY

        assertEquals(listOf("rcv:A"), sink.idsFor(Event.SmsReceived.TYPE)) // sent once, never resent
    }

    @Test
    fun `an ack runs the durable follow-up keyed off correlationId exactly once`() = runTest {
        val sink = FakeEventSink(ready = true)
        val acked = mutableListOf<String?>()
        val publisher = EventPublisher(
            sink,
            backgroundScope,
            testLogger(),
            dao = FakeEventOutboxDao(),
            onEventAcked = { _, correlationId -> acked += correlationId },
        )

        publisher.reliable(event("A"), "rcv:A", correlationId = "in-A")
        publisher.onBackendAck("rcv:A")
        publisher.onBackendAck("rcv:A") // duplicate ack: no-op

        assertEquals(listOf("in-A"), acked)
    }

    @Test
    fun `an unacked event survives process death and resends from a fresh publisher`() =
        runTest(UnconfinedTestDispatcher()) {
            val dao = FakeEventOutboxDao() // stands in for the durable Room table across a restart

            // First "process": persist an event while offline, then die before any ack.
            val before = EventPublisher(FakeEventSink(ready = false), backgroundScope, testLogger(), dao = dao)
            before.reliable(event("A"), "rcv:A")

            // Fresh process over the same durable store — the in-memory publisher is gone.
            val sink = FakeEventSink(ready = true)
            val after = EventPublisher(sink, backgroundScope, testLogger(), dao = dao)
            after.start(MutableStateFlow(ConnectionState.READY))

            assertEquals(listOf("rcv:A"), sink.idsFor(Event.SmsReceived.TYPE))
            assertEquals(1, after.outstandingCount())
        }

    @Test
    fun `ephemeral events are never persisted or resent`() = runTest(UnconfinedTestDispatcher()) {
        val sink = FakeEventSink(ready = true)
        val dao = FakeEventOutboxDao()
        val publisher = EventPublisher(sink, backgroundScope, testLogger(), dao = dao)
        val state = MutableStateFlow(ConnectionState.READY)
        publisher.start(state)

        publisher.ephemeral(Event.Heartbeat(queueDepth = 0))
        assertEquals(0, publisher.outstandingCount())
        assertEquals(0, dao.rows.size)

        state.value = ConnectionState.BACKING_OFF
        state.value = ConnectionState.READY

        assertEquals(1, sink.events.size) // sent once, not resent
    }
}
