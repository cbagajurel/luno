package com.luno.gateway.data.repository

import com.luno.gateway.data.db.dao.OutboxDao
import com.luno.gateway.data.db.entity.OutboxEntity
import com.luno.gateway.logging.LunoLogger
import com.luno.gateway.model.DomainError
import com.luno.gateway.model.OutboundMessage
import com.luno.gateway.model.OutboxStatus
import com.luno.gateway.util.Clock
import com.luno.gateway.util.SystemClock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed interface EnqueueResult {
    val id: String

    data class Enqueued(override val id: String) : EnqueueResult

    data class Duplicate(override val id: String) : EnqueueResult
}

/**
 * Owns the outbound state machine (§5) and idempotency. Every mutation persists
 * before it returns (persist-before-act), and illegal transitions are rejected,
 * not silently applied. A mutex enforces single-writer check-then-act.
 */
class OutboxRepository(
    private val dao: OutboxDao,
    private val logger: LunoLogger,
    private val clock: Clock = SystemClock,
    private val maxTerminalRetained: Int = DEFAULT_MAX_TERMINAL_RETAINED,
) {
    private val writeMutex = Mutex()

    suspend fun enqueue(message: OutboundMessage): EnqueueResult = writeMutex.withLock {
        message.commandId?.let { commandId ->
            dao.findByCommandId(commandId)?.let { existing ->
                logger.i(TAG, "enqueue ignored: commandId $commandId already mapped to ${existing.id}")
                return EnqueueResult.Duplicate(existing.id)
            }
        }
        val now = clock.nowMillis()
        val rowId = dao.insertIfAbsent(
            OutboxEntity(
                id = message.id,
                commandId = message.commandId,
                recipient = message.recipient,
                body = message.body,
                subscriptionId = message.subscriptionId,
                requestDeliveryReport = message.requestDeliveryReport,
                ref = message.ref,
                status = OutboxStatus.QUEUED,
                attempt = 0,
                lastError = null,
                createdAt = now,
                updatedAt = now,
            ),
        )
        if (rowId == -1L) {
            EnqueueResult.Duplicate(message.id)
        } else {
            pruneTerminal()
            EnqueueResult.Enqueued(message.id)
        }
    }

    suspend fun markSending(id: String): Boolean = transition(id, OutboxStatus.SENDING)

    suspend fun markSent(id: String): Boolean = transition(id, OutboxStatus.SENT)

    suspend fun markDelivered(id: String): Boolean = transition(id, OutboxStatus.DELIVERED)

    suspend fun markUndelivered(id: String): Boolean = transition(id, OutboxStatus.UNDELIVERED)

    suspend fun cancel(id: String): Boolean = transition(id, OutboxStatus.CANCELLED)

    suspend fun requeue(id: String): Boolean = transition(id, OutboxStatus.QUEUED)

    suspend fun markFailed(id: String, error: DomainError): Boolean {
        val target = OutboxStateMachine.failureStateFor(error)
        return transition(id, target, incrementAttempt = true, error = error)
    }

    suspend fun findById(id: String): OutboxEntity? = dao.findById(id)

    suspend fun queued(): List<OutboxEntity> = dao.findByStatus(OutboxStatus.QUEUED)

    suspend fun recent(limit: Int = DEFAULT_RECENT_LIMIT): List<OutboxEntity> = dao.recent(limit)

    fun observeRecent(limit: Int = DEFAULT_RECENT_LIMIT): Flow<List<OutboxEntity>> = dao.observeRecent(limit)

    fun observeQueueDepth(): Flow<Int> =
        dao.observeDepth(listOf(OutboxStatus.QUEUED, OutboxStatus.SENDING, OutboxStatus.FAILED_RETRYABLE))

    private suspend fun transition(
        id: String,
        to: OutboxStatus,
        incrementAttempt: Boolean = false,
        error: DomainError? = null,
    ): Boolean = writeMutex.withLock {
        val current = dao.findById(id)
        if (current == null) {
            logger.w(TAG, "transition to $to skipped: $id not found")
            return false
        }
        if (!OutboxStateMachine.canTransition(current.status, to)) {
            logger.w(TAG, "illegal transition ${current.status} -> $to for $id")
            return false
        }
        dao.updateStatus(
            id = id,
            status = to,
            attempt = if (incrementAttempt) current.attempt + 1 else current.attempt,
            lastError = error?.let { "${it.errorClass}:${it.code}" } ?: current.lastError,
            updatedAt = clock.nowMillis(),
        )
        true
    }

    private suspend fun pruneTerminal() {
        val terminal = OutboxStatus.entries.filter { it.isTerminal }
        val ids = dao.terminalIdsOldestFirst(terminal)
        if (ids.size > maxTerminalRetained) {
            dao.deleteByIds(ids.take(ids.size - maxTerminalRetained))
        }
    }

    companion object {
        private const val TAG = "OutboxRepository"
        private const val DEFAULT_MAX_TERMINAL_RETAINED = 500
        private const val DEFAULT_RECENT_LIMIT = 50
    }
}
