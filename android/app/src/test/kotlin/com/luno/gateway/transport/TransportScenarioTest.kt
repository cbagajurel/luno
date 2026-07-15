package com.luno.gateway.transport

import com.luno.gateway.data.repository.InboxRepository
import com.luno.gateway.data.repository.OutboxRepository
import com.luno.gateway.model.DomainError
import com.luno.gateway.model.ErrorClass
import com.luno.gateway.model.InboundMessage
import com.luno.gateway.model.OutboundMessage
import com.luno.gateway.model.OutboxStatus
import com.luno.gateway.model.SendHandle
import com.luno.gateway.testutil.FakeInboxDao
import com.luno.gateway.testutil.FakeOutboxDao
import com.luno.gateway.testutil.testLogger
import com.luno.gateway.transport.fake.FakeTransport
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Drives the outbox/inbox through a FakeTransport, no radio or backend involved. */
@OptIn(ExperimentalCoroutinesApi::class)
class TransportScenarioTest {
    private fun outbound(id: String) =
        OutboundMessage(id = id, recipient = "+15551112222", body = "hello", commandId = "cmd-$id")

    @Test
    fun `queued message sent through transport reaches SENT`() = runTest {
        val dao = FakeOutboxDao()
        val outbox = OutboxRepository(dao, testLogger())
        val transport = FakeTransport()

        outbox.enqueue(outbound("m1"))
        outbox.markSending("m1")
        val handle = transport.send(dao.findById("m1")!!.let { OutboundMessage(it.id, it.recipient, it.body) })
        assertTrue(handle is SendHandle.Sent)
        outbox.markSent("m1")

        assertEquals(OutboxStatus.SENT, dao.findById("m1")!!.status)
        assertEquals(1, transport.sent.size)
    }

    @Test
    fun `transport failure routes to the retryable state`() = runTest {
        val dao = FakeOutboxDao()
        val outbox = OutboxRepository(dao, testLogger())
        val transport = FakeTransport().apply {
            sendBehavior = { SendHandle.Failed(it.id, DomainError(ErrorClass.TRANSIENT, "radio_off", "no service")) }
        }

        outbox.enqueue(outbound("m1"))
        outbox.markSending("m1")
        when (val handle = transport.send(OutboundMessage("m1", "+15551112222", "hello"))) {
            is SendHandle.Failed -> outbox.markFailed("m1", handle.error)
            is SendHandle.Sent -> outbox.markSent("m1")
        }

        assertEquals(OutboxStatus.FAILED_RETRYABLE, dao.findById("m1")!!.status)
    }

    @Test
    fun `inbound delivery is captured once and deduped on replay`() = runTest {
        val dao = FakeInboxDao()
        val inbox = InboxRepository(dao, testLogger())
        val transport = FakeTransport()

        val received = mutableListOf<InboundMessage>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            transport.incoming().collect { received += it }
        }

        val message = InboundMessage(id = "in-1", sender = "+15550000000", body = "yo", receivedAt = 5L)
        transport.deliver(message)

        assertEquals(1, received.size)
        inbox.capture(received.first())
        inbox.capture(received.first())
        assertEquals(1, inbox.count())
    }
}
