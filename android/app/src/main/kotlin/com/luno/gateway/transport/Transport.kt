package com.luno.gateway.transport

import com.luno.gateway.model.DeliveryReport
import com.luno.gateway.model.InboundMessage
import com.luno.gateway.model.OutboundMessage
import com.luno.gateway.model.SendHandle
import com.luno.gateway.model.TransportState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * A neutral communication channel. The agent speaks only in these terms, so a
 * new channel (MMS, USSD, …) is one implementation registered beside the rest —
 * no changes to the queue, protocol, or UI. See architecture.md §12.
 */
interface Transport {
    val id: TransportId
    val capabilities: Set<TransportCapability>

    suspend fun send(request: OutboundMessage): SendHandle

    fun incoming(): Flow<InboundMessage>

    fun state(): Flow<TransportState>

    /** Delivery reports arriving after [send] returns; empty for transports without DELIVERY_REPORT. */
    fun deliveryReports(): Flow<DeliveryReport> = emptyFlow()
}
