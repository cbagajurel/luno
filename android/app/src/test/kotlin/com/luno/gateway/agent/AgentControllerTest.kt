package com.luno.gateway.agent

import com.luno.gateway.backend.protocol.Ack
import com.luno.gateway.backend.protocol.Command
import com.luno.gateway.backend.protocol.Event
import com.luno.gateway.backend.protocol.ProtocolFrame
import com.luno.gateway.backend.ws.EventPublisher
import com.luno.gateway.data.repository.InboxRepository
import com.luno.gateway.data.repository.OutboxDispatcher
import com.luno.gateway.data.repository.OutboxRepository
import com.luno.gateway.model.DeliveryReport
import com.luno.gateway.model.DeviceState
import com.luno.gateway.model.InboundMessage
import com.luno.gateway.model.InboxStatus
import com.luno.gateway.testutil.FakeEventSink
import com.luno.gateway.testutil.FakeInboxDao
import com.luno.gateway.testutil.FakeOutboxDao
import com.luno.gateway.testutil.testLogger
import com.luno.gateway.transport.TransportId
import com.luno.gateway.transport.TransportRegistry
import com.luno.gateway.transport.fake.FakeTransport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AgentControllerTest {
    private class Fixture(scope: CoroutineScope) {
        val sink = FakeEventSink(ready = true)
        val events = EventPublisher(sink, scope, testLogger())
        val outboxDao = FakeOutboxDao()
        val outbox = OutboxRepository(outboxDao, testLogger())
        val inboxDao = FakeInboxDao()
        val inbox = InboxRepository(inboxDao, testLogger())
        val transport = FakeTransport()
        val registry = TransportRegistry().apply { register(transport) }
        val dispatcher = OutboxDispatcher(outbox, registry, testLogger(), scope, TransportId.FAKE)
        val router = CommandRouter(outbox, dispatcher, events, sink, { DeviceState() }, {}, testLogger())
        val incoming = MutableSharedFlow<ProtocolFrame>(extraBufferCapacity = 16)
        val state = MutableStateFlow(ConnectionState.READY)
        val deliveries = MutableSharedFlow<DeliveryReport>(extraBufferCapacity = 16)
        val agent = AgentController(
            logger = testLogger(),
            scope = scope,
            incoming = incoming,
            connectionState = state,
            events = events,
            router = router,
            heartbeat = null,
            dispatcher = dispatcher,
            inbox = inbox,
            deliveryReports = deliveries,
        )
    }

    private fun sendFrame(id: String) =
        ProtocolFrame.command(1, id, TS, "dev-1", 1, Command.SendSms(to = "+15551112222", body = "hi"))

    @Test
    fun `a backend send_sms drives a real send and an sms_accepted event`() = runTest(UnconfinedTestDispatcher()) {
        val f = Fixture(backgroundScope)
        f.agent.onServiceStarted()

        f.incoming.emit(sendFrame("cmd-1"))

        assertEquals(1, f.transport.sent.size)
        assertEquals(listOf("acc:cmd-1"), f.sink.idsFor(Event.SmsAccepted.TYPE))
        assertEquals(listOf("cmd-1"), f.sink.acks)
        assertEquals(1L, f.agent.lastAckedInboundSeq())
    }

    @Test
    fun `a captured inbound surfaces as sms_received and is acked exactly once`() = runTest(UnconfinedTestDispatcher()) {
        val f = Fixture(backgroundScope)
        f.inbox.capture(InboundMessage(id = "in1", sender = "+15550001111", body = "yo", receivedAt = 5L))

        f.agent.onServiceStarted()

        assertEquals(listOf("rcv:in1"), f.sink.idsFor(Event.SmsReceived.TYPE))
        assertEquals(InboxStatus.REPORTED, f.inboxDao.findById("in1")!!.status)

        f.incoming.emit(ProtocolFrame.ack(1, "a1", TS, "dev-1", 9, Ack("rcv:in1")))

        assertEquals(InboxStatus.ACKED, f.inboxDao.findById("in1")!!.status)
    }

    @Test
    fun `a delivery report becomes a delivery_report event`() = runTest(UnconfinedTestDispatcher()) {
        val f = Fixture(backgroundScope)
        f.agent.onServiceStarted()

        f.deliveries.emit(DeliveryReport(messageId = "m1", partIndex = 0, delivered = true, at = 7L))

        val report = f.sink.events.map { it.event }.filterIsInstance<Event.DeliveryReport>().single()
        assertEquals("dlv:m1:0", f.sink.idsFor(Event.DeliveryReport.TYPE).single())
        assertEquals("delivered", report.status)
    }

    companion object {
        private const val TS = "2026-01-01T00:00:00Z"
    }
}
