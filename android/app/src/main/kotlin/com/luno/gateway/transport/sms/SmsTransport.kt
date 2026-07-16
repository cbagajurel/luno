package com.luno.gateway.transport.sms

import com.luno.gateway.logging.LunoLogger
import com.luno.gateway.model.DomainError
import com.luno.gateway.model.ErrorClass
import com.luno.gateway.model.InboundMessage
import com.luno.gateway.model.OutboundMessage
import com.luno.gateway.model.SendHandle
import com.luno.gateway.model.TransportState
import com.luno.gateway.transport.Transport
import com.luno.gateway.transport.TransportCapability
import com.luno.gateway.transport.TransportId
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Real single-part SMS transport (M9). [send] hands the message to the radio and
 * suspends until the platform's `sentIntent` reports the outcome, so the returned
 * [SendHandle] reflects SENT/FAILED rather than merely "handed off". Receiving
 * (M11) and delivery reports (M10) land later, hence the minimal capability set.
 */
class SmsTransport(
    private val sender: SmsSender,
    private val router: SentReportRouter,
    private val logger: LunoLogger,
    private val sendTimeoutMillis: Long = DEFAULT_SEND_TIMEOUT_MILLIS,
) : Transport {
    override val id: TransportId = TransportId.SMS
    override val capabilities: Set<TransportCapability> = setOf(TransportCapability.SEND)

    private val transportState = MutableStateFlow(TransportState.READY)

    override suspend fun send(request: OutboundMessage): SendHandle {
        val deferred = CompletableDeferred<SmsSendResult>()
        val armed = router.arm(deferred)
        return try {
            sender.send(request.recipient, request.body, request.subscriptionId, armed.sentIntent)
            when (val result = withTimeoutOrNull(sendTimeoutMillis) { deferred.await() }) {
                is SmsSendResult.Sent -> SendHandle.Sent(request.id, armed.requestId)
                is SmsSendResult.Failed -> SendHandle.Failed(request.id, result.error)
                null -> SendHandle.Failed(
                    request.id,
                    DomainError(ErrorClass.TRANSIENT, "send_timeout", "No sent report within timeout"),
                )
            }
        } catch (e: SecurityException) {
            logger.w(TAG, "SEND_SMS denied for ${request.id}: ${e.message}")
            SendHandle.Failed(
                request.id,
                DomainError(ErrorClass.TERMINAL, "permission_denied", "SEND_SMS permission not granted"),
            )
        } finally {
            router.disarm(armed.requestId)
        }
    }

    override fun incoming(): Flow<InboundMessage> = emptyFlow()

    override fun state(): Flow<TransportState> = transportState.asStateFlow()

    companion object {
        private const val TAG = "SmsTransport"
        private const val DEFAULT_SEND_TIMEOUT_MILLIS = 60_000L
    }
}
