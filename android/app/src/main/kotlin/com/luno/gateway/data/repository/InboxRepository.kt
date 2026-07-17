package com.luno.gateway.data.repository

import com.luno.gateway.data.db.dao.InboxDao
import com.luno.gateway.data.db.entity.InboxEntity
import com.luno.gateway.logging.LunoLogger
import com.luno.gateway.model.InboundMessage
import com.luno.gateway.model.InboxStatus
import com.luno.gateway.util.Clock
import com.luno.gateway.util.SystemClock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

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
    // PII-at-rest boundary: body + sender are sealed on capture and opened on read.
    // Identity by default (tests); AgentGraph injects the Keystore-backed CryptoBox.
    private val seal: (String) -> String = { it },
    private val open: (String) -> String = { it },
) {
    suspend fun capture(message: InboundMessage): CaptureResult {
        val rowId = dao.insertIfAbsent(
            InboxEntity(
                id = message.id,
                sender = seal(message.sender),
                body = seal(message.body),
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

    /** Inbound captured but not yet reported to the backend — the sms_received work list. */
    fun observePending(): Flow<List<InboxEntity>> =
        dao.observeByStatus(InboxStatus.RECEIVED).map { rows -> rows.map(::decrypt) }

    suspend fun markReported(id: String) = dao.updateStatus(id, InboxStatus.REPORTED)

    suspend fun markAcked(id: String) = dao.updateStatus(id, InboxStatus.ACKED)

    suspend fun count(): Int = dao.count()

    suspend fun recent(limit: Int = DEFAULT_RECENT_LIMIT): List<InboxEntity> = dao.recent(limit).map(::decrypt)

    fun observeRecent(limit: Int = DEFAULT_RECENT_LIMIT): Flow<List<InboxEntity>> =
        dao.observeRecent(limit).map { rows -> rows.map(::decrypt) }

    suspend fun clearAll() = dao.deleteAll()

    private fun decrypt(row: InboxEntity): InboxEntity =
        row.copy(sender = open(row.sender), body = open(row.body))

    companion object {
        private const val TAG = "InboxRepository"
        private const val DEFAULT_RECENT_LIMIT = 50
    }
}
