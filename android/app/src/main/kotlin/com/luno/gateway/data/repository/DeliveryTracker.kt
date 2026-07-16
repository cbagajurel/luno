package com.luno.gateway.data.repository

import com.luno.gateway.data.db.dao.OutboxPartDao
import com.luno.gateway.data.db.entity.OutboxPartEntity
import com.luno.gateway.logging.LunoLogger
import com.luno.gateway.model.DeliveryReport
import com.luno.gateway.model.OutboxStatus
import com.luno.gateway.model.PartDeliveryStatus
import com.luno.gateway.model.PartSentStatus
import com.luno.gateway.model.SentPart
import com.luno.gateway.transport.Transport
import com.luno.gateway.util.Clock
import com.luno.gateway.util.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * Owns the delivery side of the outbox: persists each message's parts once sent,
 * folds incoming [DeliveryReport]s into per-part state, and rolls the message up
 * to DELIVERED/UNDELIVERED (§5, §7.3). Reports can arrive out of order, minutes
 * later, or never — a [deliveryTimeoutMillis] timer flips still-PENDING parts to
 * UNDELIVERED so a message never hangs. Message transitions go through
 * [OutboxRepository] so the state machine stays authoritative.
 */
class DeliveryTracker(
    private val outbox: OutboxRepository,
    private val partDao: OutboxPartDao,
    private val scope: CoroutineScope,
    private val logger: LunoLogger,
    private val clock: Clock = SystemClock,
    private val deliveryTimeoutMillis: Long = DEFAULT_DELIVERY_TIMEOUT_MILLIS,
) {
    fun start(transport: Transport) {
        scope.launch {
            transport.deliveryReports()
                .onEach { apply(it) }
                .collect()
        }
    }

    suspend fun onSent(messageId: String, parts: List<SentPart>) {
        if (parts.isEmpty()) return
        val now = clock.nowMillis()
        partDao.upsertAll(
            parts.map {
                OutboxPartEntity(
                    messageId = messageId,
                    partIndex = it.index,
                    transportRef = it.transportRef,
                    sentStatus = PartSentStatus.SENT,
                    deliveryStatus = if (it.deliveryTracked) PartDeliveryStatus.PENDING else PartDeliveryStatus.NONE,
                    updatedAt = now,
                )
            },
        )
        if (parts.any { it.deliveryTracked }) scheduleTimeout(messageId)
    }

    private suspend fun apply(report: DeliveryReport) {
        partDao.updateDeliveryStatus(
            messageId = report.messageId,
            partIndex = report.partIndex,
            status = if (report.delivered) PartDeliveryStatus.DELIVERED else PartDeliveryStatus.UNDELIVERED,
            updatedAt = clock.nowMillis(),
        )
        rollUp(report.messageId)
    }

    private fun scheduleTimeout(messageId: String) {
        scope.launch {
            delay(deliveryTimeoutMillis)
            partDao.resolvePending(
                messageId = messageId,
                from = PartDeliveryStatus.PENDING,
                to = PartDeliveryStatus.UNDELIVERED,
                updatedAt = clock.nowMillis(),
            )
            rollUp(messageId)
        }
    }

    private suspend fun rollUp(messageId: String) {
        when (OutboxDeliveryRollup.status(partDao.partsFor(messageId))) {
            OutboxStatus.DELIVERED -> outbox.markDelivered(messageId)
            OutboxStatus.UNDELIVERED -> outbox.markUndelivered(messageId)
            else -> Unit
        }
    }

    companion object {
        private const val DEFAULT_DELIVERY_TIMEOUT_MILLIS = 5 * 60 * 1000L
    }
}
