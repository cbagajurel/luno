package com.luno.gateway.agent

import com.luno.gateway.backend.protocol.Command
import com.luno.gateway.backend.protocol.Event
import com.luno.gateway.backend.protocol.ProtocolFrame
import com.luno.gateway.backend.ws.EventPublisher
import com.luno.gateway.data.repository.OutboxDispatcher
import com.luno.gateway.data.repository.OutboxRepository
import com.luno.gateway.model.DeviceState
import com.luno.gateway.model.OutboxStatus
import com.luno.gateway.testutil.FakeEventSink
import com.luno.gateway.testutil.FakeOutboxDao
import com.luno.gateway.testutil.testLogger
import com.luno.gateway.transport.TransportId
import com.luno.gateway.transport.TransportRegistry
import com.luno.gateway.transport.fake.FakeTransport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CommandRouterTest {
    private class Fixture(scope: CoroutineScope, val transport: FakeTransport = FakeTransport()) {
        val dao = FakeOutboxDao()
        val outbox = OutboxRepository(dao, testLogger())
        val sink = FakeEventSink(ready = true)
        val events = EventPublisher(sink, scope, testLogger())
        val registry = TransportRegistry().apply { register(transport) }
        val dispatcher = OutboxDispatcher(outbox, registry, testLogger(), scope, TransportId.FAKE)
        var heartbeatSeconds = 0
        val router = CommandRouter(
            outbox = outbox,
            dispatcher = dispatcher,
            events = events,
            ack = sink,
            deviceState = { DeviceState() },
            onHeartbeatInterval = { heartbeatSeconds = it },
            logger = testLogger(),
        )
    }

    private fun sendFrame(id: String, to: String = "+15551112222") =
        ProtocolFrame.command(1, id, TS, "dev-1", 1, Command.SendSms(to = to, body = "hello"))

    @Test
    fun `send_sms enqueues, accepts, acks, and dispatches once`() = runTest {
        val f = Fixture(this)

        val seq = f.router.handle(sendFrame("cmd-1"))

        assertEquals(1L, seq)
        assertEquals(1, f.transport.sent.size)
        assertEquals(listOf("acc:cmd-1"), f.sink.idsFor(Event.SmsAccepted.TYPE))
        assertEquals(listOf("cmd-1"), f.sink.acks)
        assertEquals(OutboxStatus.SENT, f.dao.rows.values.first().status)
    }

    @Test
    fun `a redelivered send_sms re-acks but never sends twice`() = runTest {
        val f = Fixture(this)

        f.router.handle(sendFrame("cmd-1"))
        f.router.handle(sendFrame("cmd-1")) // same command id: duplicate

        assertEquals(1, f.transport.sent.size)
        assertEquals(listOf("acc:cmd-1", "acc:cmd-1"), f.sink.idsFor(Event.SmsAccepted.TYPE))
        assertEquals(listOf("cmd-1", "cmd-1"), f.sink.acks)
    }

    @Test
    fun `cancel_sms cancels the queued message for that command`() = runTest {
        val f = Fixture(this, FakeTransport().apply { sendBehavior = { throw AssertionError("must not send") } })
        // Enqueue directly so it stays QUEUED (transport would throw if dispatched).
        f.outbox.enqueue(
            com.luno.gateway.model.OutboundMessage(id = "m1", recipient = "+1", body = "x", commandId = "cmd-1"),
        )

        f.router.handle(ProtocolFrame.command(1, "c2", TS, "dev-1", 2, Command.CancelSms("cmd-1")))

        assertEquals(OutboxStatus.CANCELLED, f.dao.findById("m1")!!.status)
    }

    @Test
    fun `get_status emits a device_status snapshot`() = runTest {
        val f = Fixture(this)

        f.router.handle(ProtocolFrame.command(1, "c3", TS, "dev-1", 5, Command.GetStatus))

        assertTrue(f.sink.events.any { it.event.type == Event.DeviceStatus.TYPE })
    }

    @Test
    fun `config_update applies the heartbeat interval`() = runTest {
        val f = Fixture(this)

        f.router.handle(ProtocolFrame.command(1, "c4", TS, "dev-1", 6, Command.ConfigUpdate(heartbeatSec = 45)))

        assertEquals(45, f.heartbeatSeconds)
    }

    companion object {
        private const val TS = "2026-01-01T00:00:00Z"
    }
}
