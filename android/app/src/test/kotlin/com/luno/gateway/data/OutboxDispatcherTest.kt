package com.luno.gateway.data

import com.luno.gateway.data.repository.OutboxDispatcher
import com.luno.gateway.data.repository.OutboxRepository
import com.luno.gateway.model.DomainError
import com.luno.gateway.model.ErrorClass
import com.luno.gateway.model.OutboundMessage
import com.luno.gateway.model.OutboxStatus
import com.luno.gateway.model.SendHandle
import com.luno.gateway.testutil.FakeOutboxDao
import com.luno.gateway.testutil.testLogger
import com.luno.gateway.transport.TransportId
import com.luno.gateway.transport.TransportRegistry
import com.luno.gateway.transport.fake.FakeTransport
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OutboxDispatcherTest {
    private fun outbound(id: String) =
        OutboundMessage(id = id, recipient = "+15551112222", body = "hi", commandId = "cmd-$id")

    private fun fixture(transport: FakeTransport?): Triple<FakeOutboxDao, OutboxRepository, TransportRegistry> {
        val dao = FakeOutboxDao()
        val outbox = OutboxRepository(dao, testLogger())
        val registry = TransportRegistry().apply { transport?.let { register(it) } }
        return Triple(dao, outbox, registry)
    }

    @Test
    fun `dispatch drives a queued message to SENT`() = runTest {
        val (dao, outbox, registry) = fixture(FakeTransport())
        val dispatcher = OutboxDispatcher(outbox, registry, testLogger(), this, TransportId.FAKE)

        outbox.enqueue(outbound("m1"))
        assertEquals(true, dispatcher.dispatch("m1"))

        assertEquals(OutboxStatus.SENT, dao.findById("m1")!!.status)
    }

    @Test
    fun `transient transport failure routes to FAILED_RETRYABLE`() = runTest {
        val transport = FakeTransport().apply {
            sendBehavior = { SendHandle.Failed(it.id, DomainError(ErrorClass.TRANSIENT, "no_service", "no service")) }
        }
        val (dao, outbox, registry) = fixture(transport)
        val dispatcher = OutboxDispatcher(outbox, registry, testLogger(), this, TransportId.FAKE)

        outbox.enqueue(outbound("m1"))
        dispatcher.dispatch("m1")

        assertEquals(OutboxStatus.FAILED_RETRYABLE, dao.findById("m1")!!.status)
    }

    @Test
    fun `permission-denied (terminal) failure routes to FAILED_TERMINAL`() = runTest {
        val transport = FakeTransport().apply {
            sendBehavior = {
                SendHandle.Failed(it.id, DomainError(ErrorClass.TERMINAL, "permission_denied", "no SEND_SMS"))
            }
        }
        val (dao, outbox, registry) = fixture(transport)
        val dispatcher = OutboxDispatcher(outbox, registry, testLogger(), this, TransportId.FAKE)

        outbox.enqueue(outbound("m1"))
        dispatcher.dispatch("m1")

        assertEquals(OutboxStatus.FAILED_TERMINAL, dao.findById("m1")!!.status)
    }

    @Test
    fun `no registered transport leaves the message QUEUED`() = runTest {
        val (dao, outbox, registry) = fixture(transport = null)
        val dispatcher = OutboxDispatcher(outbox, registry, testLogger(), this, TransportId.FAKE)

        outbox.enqueue(outbound("m1"))
        assertFalse(dispatcher.dispatch("m1"))

        assertEquals(OutboxStatus.QUEUED, dao.findById("m1")!!.status)
    }

    @Test
    fun `submit dedupes a replayed commandId to a single send`() = runTest {
        val transport = FakeTransport()
        val (_, outbox, registry) = fixture(transport)
        val dispatcher = OutboxDispatcher(outbox, registry, testLogger(), this, TransportId.FAKE)

        // Same commandId, different durable ids: the second is a duplicate and must not send.
        dispatcher.submit(OutboundMessage(id = "a", recipient = "+15551112222", body = "hi", commandId = "cmd-1"))
        dispatcher.submit(OutboundMessage(id = "b", recipient = "+15551112222", body = "hi", commandId = "cmd-1"))
        advanceUntilIdle()

        assertEquals(1, transport.sent.size)
    }
}
