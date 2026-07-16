package com.luno.gateway.data.repository

import com.luno.gateway.data.db.entity.OutboxEntity
import com.luno.gateway.logging.LunoLogger
import com.luno.gateway.model.OutboundMessage
import com.luno.gateway.model.SendHandle
import com.luno.gateway.model.SentPart
import com.luno.gateway.transport.TransportId
import com.luno.gateway.transport.TransportRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * The bridge between the durable outbox and a live transport: it marks a queued
 * message SENDING, hands it to the SMS transport, and records the outcome
 * (SENT or the taxonomy-derived failure state). It never changes state directly
 * — every transition goes through [OutboxRepository] so the state machine and
 * persist-before-act guarantees hold.
 */
class OutboxDispatcher(
    private val outbox: OutboxRepository,
    private val transports: TransportRegistry,
    private val logger: LunoLogger,
    private val scope: CoroutineScope,
    private val transportId: TransportId = TransportId.SMS,
    private val onSent: suspend (messageId: String, parts: List<SentPart>) -> Unit = { _, _ -> },
) {
    /** Enqueue then dispatch off the caller's thread; returns the durable id immediately. */
    fun submit(message: OutboundMessage): String {
        scope.launch {
            val result = outbox.enqueue(message)
            if (result is EnqueueResult.Enqueued) {
                dispatch(result.id)
            } else {
                logger.i(TAG, "submit: ${message.id} deduped to ${result.id}, not re-sending")
            }
        }
        return message.id
    }

    suspend fun dispatch(id: String): Boolean {
        val row = outbox.findById(id) ?: run {
            logger.w(TAG, "dispatch: $id not found")
            return false
        }
        val transport = transports.get(transportId) ?: run {
            logger.w(TAG, "dispatch: no $transportId transport registered")
            return false
        }
        if (!outbox.markSending(id)) return false
        val handle = transport.send(row.toOutboundMessage())
        return when (handle) {
            is SendHandle.Sent -> {
                val sent = outbox.markSent(id)
                if (sent) onSent(id, handle.parts)
                sent
            }
            is SendHandle.Failed -> outbox.markFailed(id, handle.error)
        }
    }

    suspend fun drainQueued() {
        outbox.queued().forEach { dispatch(it.id) }
    }

    companion object {
        private const val TAG = "OutboxDispatcher"
    }
}

private fun OutboxEntity.toOutboundMessage() = OutboundMessage(
    id = id,
    recipient = recipient,
    body = body,
    subscriptionId = subscriptionId,
    requestDeliveryReport = requestDeliveryReport,
    commandId = commandId,
    ref = ref,
)
