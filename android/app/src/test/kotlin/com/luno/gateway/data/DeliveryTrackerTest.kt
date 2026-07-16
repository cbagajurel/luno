package com.luno.gateway.data

import com.luno.gateway.data.repository.DeliveryTracker
import com.luno.gateway.data.repository.OutboxRepository
import com.luno.gateway.model.DeliveryReport
import com.luno.gateway.model.OutboundMessage
import com.luno.gateway.model.OutboxStatus
import com.luno.gateway.model.SentPart
import com.luno.gateway.testutil.FakeOutboxDao
import com.luno.gateway.testutil.FakeOutboxPartDao
import com.luno.gateway.testutil.testLogger
import com.luno.gateway.transport.fake.FakeTransport
import com.luno.gateway.util.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DeliveryTrackerTest {
    private val fixedClock = object : Clock {
        override fun nowMillis(): Long = 1_000L
    }

    private suspend fun sentMessage(outbox: OutboxRepository, id: String) {
        outbox.enqueue(OutboundMessage(id = id, recipient = "+15551112222", body = "hi"))
        outbox.markSending(id)
        outbox.markSent(id)
    }

    private fun TestScope.tracker(
        outbox: OutboxRepository,
        partDao: FakeOutboxPartDao,
        transport: FakeTransport,
        timeoutMillis: Long = 60_000L,
    ): DeliveryTracker {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        return DeliveryTracker(outbox, partDao, scope, testLogger(), fixedClock, timeoutMillis)
            .also { it.start(transport) }
    }

    @Test
    fun `all parts delivered rolls the message up to DELIVERED`() = runTest {
        val outboxDao = FakeOutboxDao()
        val partDao = FakeOutboxPartDao()
        val outbox = OutboxRepository(outboxDao, testLogger())
        val transport = FakeTransport()
        val tracker = tracker(outbox, partDao, transport)

        sentMessage(outbox, "m1")
        tracker.onSent("m1", listOf(SentPart(0, "r0", true), SentPart(1, "r1", true)))

        transport.deliverReport(DeliveryReport("m1", 0, delivered = true, at = 1L))
        transport.deliverReport(DeliveryReport("m1", 1, delivered = true, at = 2L))

        assertEquals(OutboxStatus.DELIVERED, outboxDao.findById("m1")!!.status)
    }

    @Test
    fun `one failed part rolls up to UNDELIVERED`() = runTest {
        val outboxDao = FakeOutboxDao()
        val partDao = FakeOutboxPartDao()
        val outbox = OutboxRepository(outboxDao, testLogger())
        val transport = FakeTransport()
        val tracker = tracker(outbox, partDao, transport)

        sentMessage(outbox, "m1")
        tracker.onSent("m1", listOf(SentPart(0, "r0", true), SentPart(1, "r1", true)))

        transport.deliverReport(DeliveryReport("m1", 0, delivered = true, at = 1L))
        transport.deliverReport(DeliveryReport("m1", 1, delivered = false, at = 2L))

        assertEquals(OutboxStatus.UNDELIVERED, outboxDao.findById("m1")!!.status)
    }

    @Test
    fun `a part that never reports times out to UNDELIVERED`() = runTest {
        val outboxDao = FakeOutboxDao()
        val partDao = FakeOutboxPartDao()
        val outbox = OutboxRepository(outboxDao, testLogger())
        val transport = FakeTransport()
        val tracker = tracker(outbox, partDao, transport, timeoutMillis = 10_000L)

        sentMessage(outbox, "m1")
        tracker.onSent("m1", listOf(SentPart(0, "r0", true), SentPart(1, "r1", true)))

        transport.deliverReport(DeliveryReport("m1", 0, delivered = true, at = 1L))
        // Part 1 never reports; before the timeout it is still SENT.
        assertEquals(OutboxStatus.SENT, outboxDao.findById("m1")!!.status)

        advanceTimeBy(10_001L)

        assertEquals(OutboxStatus.UNDELIVERED, outboxDao.findById("m1")!!.status)
    }

    @Test
    fun `untracked message (no delivery report requested) stays SENT`() = runTest {
        val outboxDao = FakeOutboxDao()
        val partDao = FakeOutboxPartDao()
        val outbox = OutboxRepository(outboxDao, testLogger())
        val transport = FakeTransport()
        val tracker = tracker(outbox, partDao, transport)

        sentMessage(outbox, "m1")
        tracker.onSent("m1", listOf(SentPart(0, "r0", deliveryTracked = false)))

        assertEquals(OutboxStatus.SENT, outboxDao.findById("m1")!!.status)
    }
}
