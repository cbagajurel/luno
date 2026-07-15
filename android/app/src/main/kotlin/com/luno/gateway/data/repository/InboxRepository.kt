package com.luno.gateway.data.repository

import com.luno.gateway.data.db.dao.InboxDao
import com.luno.gateway.data.db.entity.InboxEntity
import com.luno.gateway.logging.LunoLogger
import com.luno.gateway.model.InboundMessage
import com.luno.gateway.model.InboxStatus
import com.luno.gateway.util.Clock
import com.luno.gateway.util.SystemClock

sealed interface CaptureResult {
    val id: String

    data class Captured(override val id: String) : CaptureResult

    data class Duplicate(override val id: String) : CaptureResult
}

/**
 * Captures inbound messages durably before anything else acts on them, and
 * dedupes on the message id (sender + timestamp + ref).
 */
class InboxRepository(
    private val dao: InboxDao,
    private val logger: LunoLogger,
    private val clock: Clock = SystemClock,
) {
    suspend fun capture(message: InboundMessage): CaptureResult {
        val rowId = dao.insertIfAbsent(
            InboxEntity(
                id = message.id,
                sender = message.sender,
                body = message.body,
                subscriptionId = message.subscriptionId,
                receivedAt = message.receivedAt,
                parts = message.parts,
                status = InboxStatus.RECEIVED,
                createdAt = clock.nowMillis(),
            ),
        )
        return if (rowId == -1L) {
            logger.i(TAG, "inbound ${message.id} already captured; ignoring duplicate")
            CaptureResult.Duplicate(message.id)
        } else {
            CaptureResult.Captured(message.id)
        }
    }

    suspend fun markReported(id: String) = dao.updateStatus(id, InboxStatus.REPORTED)

    suspend fun markAcked(id: String) = dao.updateStatus(id, InboxStatus.ACKED)

    suspend fun count(): Int = dao.count()

    companion object {
        private const val TAG = "InboxRepository"
    }
}
