package com.luno.gateway.data

import com.luno.gateway.data.repository.EnqueueResult
import com.luno.gateway.data.repository.OutboxRepository
import com.luno.gateway.model.DomainError
import com.luno.gateway.model.ErrorClass
import com.luno.gateway.model.OutboundMessage
import com.luno.gateway.model.OutboxStatus
import com.luno.gateway.testutil.FakeOutboxDao
import com.luno.gateway.testutil.testLogger
import com.luno.gateway.util.Clock
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OutboxRepositoryTest {
    private val dao = FakeOutboxDao()
    private var tick = 0L
    private val clock = Clock { tick++ }
    private fun repo(maxTerminalRetained: Int = 500) =
        OutboxRepository(dao, testLogger(), clock, maxTerminalRetained)

    private fun message(id: String, commandId: String? = null) =
        OutboundMessage(id = id, recipient = "+15551234567", body = "hi", commandId = commandId)

    @Test
    fun `body and recipient are sealed at rest but returned in the clear`() = runTest {
        val sealed = FakeOutboxDao()
        val repo = OutboxRepository(
            sealed, testLogger(), clock,
            seal = { "sealed:$it" },
            open = { it.removePrefix("sealed:") },
        )
        repo.enqueue(message("m1")) // body "hi", recipient "+15551234567"

        val stored = sealed.rows.getValue("m1")
        assertEquals("sealed:hi", stored.body)
        assertEquals("sealed:+15551234567", stored.recipient)

        val readBack = repo.findById("m1")!!
        assertEquals("hi", readBack.body)
        assertEquals("+15551234567", readBack.recipient)
    }

    @Test
    fun `clearAll empties the outbox`() = runTest {
        val repo = repo()
        repo.enqueue(message("m1"))
        repo.enqueue(message("m2"))
        repo.clearAll()
        assertTrue(dao.rows.isEmpty())
    }

    @Test
    fun `enqueue persists as QUEUED before returning`() = runTest {
        val result = repo().enqueue(message("m1"))
        assertTrue(result is EnqueueResult.Enqueued)
        val row = dao.findById("m1")
        assertNotNull(row)
        assertEquals(OutboxStatus.QUEUED, row!!.status)
    }

    @Test
    fun `replaying a command id does not double-enqueue`() = runTest {
        val repo = repo()
        val first = repo.enqueue(message("m1", commandId = "cmd-A"))
        val second = repo.enqueue(message("m2", commandId = "cmd-A"))
        assertTrue(first is EnqueueResult.Enqueued)
        assertTrue(second is EnqueueResult.Duplicate)
        assertEquals("m1", second.id)
        assertEquals(1, dao.rows.size)
    }

    @Test
    fun `replaying a message id does not double-enqueue`() = runTest {
        val repo = repo()
        repo.enqueue(message("m1"))
        val second = repo.enqueue(message("m1"))
        assertTrue(second is EnqueueResult.Duplicate)
        assertEquals(1, dao.rows.size)
    }

    @Test
    fun `full happy path drives QUEUED to DELIVERED`() = runTest {
        val repo = repo()
        repo.enqueue(message("m1"))
        assertTrue(repo.markSending("m1"))
        assertTrue(repo.markSent("m1"))
        assertTrue(repo.markDelivered("m1"))
        assertEquals(OutboxStatus.DELIVERED, dao.findById("m1")!!.status)
    }

    @Test
    fun `illegal transition is rejected and state is unchanged`() = runTest {
        val repo = repo()
        repo.enqueue(message("m1"))
        assertFalse(repo.markDelivered("m1"))
        assertEquals(OutboxStatus.QUEUED, dao.findById("m1")!!.status)
    }

    @Test
    fun `transient failure is retryable and can requeue with incremented attempt`() = runTest {
        val repo = repo()
        repo.enqueue(message("m1"))
        repo.markSending("m1")
        assertTrue(repo.markFailed("m1", DomainError(ErrorClass.TRANSIENT, "radio_off", "no service")))
        val failed = dao.findById("m1")!!
        assertEquals(OutboxStatus.FAILED_RETRYABLE, failed.status)
        assertEquals(1, failed.attempt)
        assertTrue(repo.requeue("m1"))
        assertEquals(OutboxStatus.QUEUED, dao.findById("m1")!!.status)
    }

    @Test
    fun `terminal failure does not retry`() = runTest {
        val repo = repo()
        repo.enqueue(message("m1"))
        repo.markSending("m1")
        repo.markFailed("m1", DomainError(ErrorClass.TERMINAL, "bad_number", "invalid"))
        assertEquals(OutboxStatus.FAILED_TERMINAL, dao.findById("m1")!!.status)
        assertFalse(repo.requeue("m1"))
    }

    @Test
    fun `outstanding cursor lists non-terminal command ids and drops completed ones`() = runTest {
        val repo = repo()
        repo.enqueue(message("m1", commandId = "cmd-A")) // QUEUED — outstanding
        repo.enqueue(message("m2", commandId = "cmd-B"))
        repo.markSending("m2")
        repo.markSent("m2") // SENT — still outstanding (awaiting delivery)
        repo.enqueue(message("m3", commandId = "cmd-C"))
        repo.markSending("m3")
        repo.markSent("m3")
        repo.markDelivered("m3") // terminal — dropped
        repo.enqueue(message("m4")) // no commandId — never listed

        assertEquals(listOf("cmd-A", "cmd-B"), repo.observeOutstandingCommandIds().first())
    }

    @Test
    fun `retention prunes oldest terminal rows beyond the cap`() = runTest {
        val repo = repo(maxTerminalRetained = 1)
        repo.enqueue(message("old"))
        repo.markSending("old")
        repo.markFailed("old", DomainError(ErrorClass.TERMINAL, "bad_number", ""))
        repo.enqueue(message("new"))
        repo.markSending("new")
        repo.markFailed("new", DomainError(ErrorClass.TERMINAL, "bad_number", ""))

        // pruneTerminal runs on the next enqueue.
        repo.enqueue(message("keep"))

        assertFalse(dao.rows.containsKey("old"))
        assertTrue(dao.rows.containsKey("new"))
        assertTrue(dao.rows.containsKey("keep"))
    }
}
