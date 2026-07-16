package com.luno.gateway.transport.fake

import com.luno.gateway.model.DeliveryReport
import com.luno.gateway.model.InboundMessage
import com.luno.gateway.model.OutboundMessage
import com.luno.gateway.model.SendHandle
import com.luno.gateway.model.TransportState
import com.luno.gateway.transport.Transport
import com.luno.gateway.transport.TransportCapability
import com.luno.gateway.transport.TransportId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Scriptable stand-in for a real transport, used to drive the outbox/inbox
 * without a radio or backend. [sendBehavior] decides each send's outcome;
 * [deliver] feeds inbound messages into [incoming].
 */
class FakeTransport(
    private val transportState: MutableStateFlow<TransportState> = MutableStateFlow(TransportState.READY),
) : Transport {
    override val id: TransportId = TransportId.FAKE
    override val capabilities: Set<TransportCapability> =
        setOf(TransportCapability.SEND, TransportCapability.RECEIVE, TransportCapability.DELIVERY_REPORT)

    val sent = mutableListOf<OutboundMessage>()

    var sendBehavior: (OutboundMessage) -> SendHandle = { message ->
        SendHandle.Sent(message.id, "fake-ref-${message.id}")
    }

    private val inbound = MutableSharedFlow<InboundMessage>(extraBufferCapacity = 64)
    private val deliveries = MutableSharedFlow<DeliveryReport>(extraBufferCapacity = 64)

    override suspend fun send(request: OutboundMessage): SendHandle {
        sent += request
        return sendBehavior(request)
    }

    override fun incoming(): Flow<InboundMessage> = inbound

    override fun state(): Flow<TransportState> = transportState.asStateFlow()

    override fun deliveryReports(): Flow<DeliveryReport> = deliveries

    suspend fun deliver(message: InboundMessage) {
        inbound.emit(message)
    }

    suspend fun deliverReport(report: DeliveryReport) {
        deliveries.emit(report)
    }
}
