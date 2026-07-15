package com.luno.gateway.data

import com.luno.gateway.data.repository.CaptureResult
import com.luno.gateway.data.repository.InboxRepository
import com.luno.gateway.model.InboundMessage
import com.luno.gateway.model.InboxStatus
import com.luno.gateway.testutil.FakeInboxDao
import com.luno.gateway.testutil.testLogger
import com.luno.gateway.util.Ids
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InboxRepositoryTest {
    private val dao = FakeInboxDao()
    private val repo = InboxRepository(dao, testLogger()) { 0L }

    private fun inbound(id: String) =
        InboundMessage(id = id, sender = "+15550000000", body = "ping", receivedAt = 111L)

    @Test
    fun `capture persists as RECEIVED`() = runTest {
        val result = repo.capture(inbound("i1"))
        assertTrue(result is CaptureResult.Captured)
        assertEquals(InboxStatus.RECEIVED, dao.findById("i1")!!.status)
    }

    @Test
    fun `replaying an inbound id does not double-insert`() = runTest {
        val key = Ids.inboundKey("+15550000000", 111L, "ref-1")
        val first = repo.capture(inbound(key))
        val second = repo.capture(inbound(key))
        assertTrue(first is CaptureResult.Captured)
        assertTrue(second is CaptureResult.Duplicate)
        assertEquals(1, repo.count())
    }
}
