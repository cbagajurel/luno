package com.luno.gateway.data.repository

import com.luno.gateway.model.DomainError
import com.luno.gateway.model.OutboxStatus
import com.luno.gateway.model.OutboxStatus.CANCELLED
import com.luno.gateway.model.OutboxStatus.DELIVERED
import com.luno.gateway.model.OutboxStatus.FAILED_RETRYABLE
import com.luno.gateway.model.OutboxStatus.FAILED_TERMINAL
import com.luno.gateway.model.OutboxStatus.QUEUED
import com.luno.gateway.model.OutboxStatus.SENDING
import com.luno.gateway.model.OutboxStatus.SENT
import com.luno.gateway.model.OutboxStatus.UNDELIVERED

/** The outbound message state machine from architecture.md §5. Pure, no I/O. */
object OutboxStateMachine {
    private val allowed: Map<OutboxStatus, Set<OutboxStatus>> = mapOf(
        QUEUED to setOf(SENDING, CANCELLED),
        SENDING to setOf(SENT, FAILED_RETRYABLE, FAILED_TERMINAL),
        SENT to setOf(DELIVERED, UNDELIVERED),
        FAILED_RETRYABLE to setOf(QUEUED),
        DELIVERED to emptySet(),
        UNDELIVERED to emptySet(),
        FAILED_TERMINAL to emptySet(),
        CANCELLED to emptySet(),
    )

    fun canTransition(from: OutboxStatus, to: OutboxStatus): Boolean =
        to in allowed.getValue(from)

    /** A retryable error re-enters the queue with backoff; everything else is terminal. */
    fun failureStateFor(error: DomainError): OutboxStatus =
        if (error.retryable) FAILED_RETRYABLE else FAILED_TERMINAL
}
