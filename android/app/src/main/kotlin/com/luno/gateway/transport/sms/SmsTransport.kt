package com.luno.gateway.transport.sms

import com.luno.gateway.logging.LunoLogger
import com.luno.gateway.model.DeliveryReport
import com.luno.gateway.model.DomainError
import com.luno.gateway.model.ErrorClass
import com.luno.gateway.model.InboundMessage
import com.luno.gateway.model.OutboundMessage
import com.luno.gateway.model.SendHandle
import com.luno.gateway.model.SentPart
import com.luno.gateway.model.TransportState
import com.luno.gateway.transport.Transport
import com.luno.gateway.transport.TransportCapability
import com.luno.gateway.transport.TransportId
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Real SMS transport (M9 single-part, M10 multipart + delivery reports). [send]
 * splits the body, hands every part to the radio, and suspends until all *sent*
 * reports arrive — rolling them up into one [SendHandle] (all sent → SENT; any
 * failure → worst-case). Delivery reports (SENT → DELIVERED) arrive later on
 * [deliveryReports], one per part, correlated by (messageId, partIndex).
 */
class SmsTransport(
    private val sender: SmsSender,
    private val sentReportRouter: SentReportRouter,
    private val deliveryReportRouter: DeliveryReportRouter,
    private val logger: LunoLogger,
    private val sendTimeoutMillis: Long = DEFAULT_SEND_TIMEOUT_MILLIS,
) : Transport {
    override val id: TransportId = TransportId.SMS
    override val capabilities: Set<TransportCapability> =
        setOf(TransportCapability.SEND, TransportCapability.DELIVERY_REPORT)

    private val transportState = MutableStateFlow(TransportState.READY)

    override suspend fun send(request: OutboundMessage): SendHandle {
        val armed = mutableListOf<SentReportRouter.Armed>()
        return try {
            val manager = sender.managerFor(request.subscriptionId)
            val parts = MultipartAssembler.split(manager, request.body)
            val deferreds = parts.indices.map {
                val deferred = CompletableDeferred<SmsSendResult>()
                armed += sentReportRouter.arm(deferred)
                deferred
            }
            val deliveryIntents = if (request.requestDeliveryReport) {
                parts.indices.map { deliveryReportRouter.deliveryIntentFor(request.id, it) }
            } else {
                null
            }
            logger.i(
                TAG,
                "sending ${request.id}: ${parts.size} part(s), ${request.body.length} chars, " +
                    "subId=${request.subscriptionId ?: "default"}",
            )
            if (parts.size == 1) {
                sender.sendSinglePart(manager, request.recipient, parts[0], armed[0].sentIntent, deliveryIntents?.get(0))
            } else {
                sender.sendMultipart(manager, request.recipient, parts, armed.map { it.sentIntent }, deliveryIntents)
            }

            val results = withTimeoutOrNull(sendTimeoutMillis) { deferreds.awaitAll() }
                ?: return SendHandle.Failed(
                    request.id,
                    DomainError(ErrorClass.TRANSIENT, "send_timeout", "No sent report within timeout"),
                )
            rollUp(request, armed, results)
        } catch (e: SecurityException) {
            // SEND_SMS can be revoked (or auto-reset) at any time; surface it as AUTH so
            // it prompts a re-grant instead of failing terminally or crashing (§10).
            logger.w(TAG, "SEND_SMS denied for ${request.id}: ${e.message}")
            SendHandle.Failed(
                request.id,
                DomainError(ErrorClass.AUTH, "sms_permission_revoked", "SEND_SMS permission not granted"),
            )
        } finally {
            armed.forEach { sentReportRouter.disarm(it.requestId) }
        }
    }

    private fun rollUp(
        request: OutboundMessage,
        armed: List<SentReportRouter.Armed>,
        results: List<SmsSendResult>,
    ): SendHandle {
        val worstFailure = results.filterIsInstance<SmsSendResult.Failed>()
            .map { it.error }
            .minByOrNull { if (it.retryable) 1 else 0 }
        if (worstFailure != null) {
            return SendHandle.Failed(request.id, worstFailure)
        }
        val parts = armed.mapIndexed { index, a ->
            SentPart(index = index, transportRef = a.requestId, deliveryTracked = request.requestDeliveryReport)
        }
        return SendHandle.Sent(request.id, transportRef = armed.first().requestId, parts = parts)
    }

    override fun incoming(): Flow<InboundMessage> = emptyFlow()

    override fun state(): Flow<TransportState> = transportState.asStateFlow()

    override fun deliveryReports(): Flow<DeliveryReport> = deliveryReportRouter.reports()

    companion object {
        private const val TAG = "SmsTransport"
        private const val DEFAULT_SEND_TIMEOUT_MILLIS = 60_000L
    }
}
