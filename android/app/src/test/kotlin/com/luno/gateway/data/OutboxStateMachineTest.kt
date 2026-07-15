package com.luno.gateway.data

import com.luno.gateway.data.repository.OutboxStateMachine
import com.luno.gateway.model.DomainError
import com.luno.gateway.model.ErrorClass
import com.luno.gateway.model.OutboxStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OutboxStateMachineTest {
    @Test
    fun `happy path transitions are legal`() {
        assertTrue(OutboxStateMachine.canTransition(OutboxStatus.QUEUED, OutboxStatus.SENDING))
        assertTrue(OutboxStateMachine.canTransition(OutboxStatus.SENDING, OutboxStatus.SENT))
        assertTrue(OutboxStateMachine.canTransition(OutboxStatus.SENT, OutboxStatus.DELIVERED))
    }

    @Test
    fun `retry loop transitions are legal`() {
        assertTrue(OutboxStateMachine.canTransition(OutboxStatus.SENDING, OutboxStatus.FAILED_RETRYABLE))
        assertTrue(OutboxStateMachine.canTransition(OutboxStatus.FAILED_RETRYABLE, OutboxStatus.QUEUED))
    }

    @Test
    fun `cancel is only legal from queued`() {
        assertTrue(OutboxStateMachine.canTransition(OutboxStatus.QUEUED, OutboxStatus.CANCELLED))
        assertFalse(OutboxStateMachine.canTransition(OutboxStatus.SENDING, OutboxStatus.CANCELLED))
    }

    @Test
    fun `illegal transitions are rejected`() {
        assertFalse(OutboxStateMachine.canTransition(OutboxStatus.QUEUED, OutboxStatus.DELIVERED))
        assertFalse(OutboxStateMachine.canTransition(OutboxStatus.SENT, OutboxStatus.SENDING))
        assertFalse(OutboxStateMachine.canTransition(OutboxStatus.DELIVERED, OutboxStatus.SENT))
    }

    @Test
    fun `terminal states allow no transitions`() {
        listOf(
            OutboxStatus.DELIVERED,
            OutboxStatus.UNDELIVERED,
            OutboxStatus.FAILED_TERMINAL,
            OutboxStatus.CANCELLED,
        ).forEach { terminal ->
            OutboxStatus.entries.forEach { to ->
                assertFalse(OutboxStateMachine.canTransition(terminal, to))
            }
        }
    }

    @Test
    fun `error class picks the failure state`() {
        assertEquals(
            OutboxStatus.FAILED_RETRYABLE,
            OutboxStateMachine.failureStateFor(DomainError(ErrorClass.TRANSIENT, "radio_off", "")),
        )
        assertEquals(
            OutboxStatus.FAILED_RETRYABLE,
            OutboxStateMachine.failureStateFor(DomainError(ErrorClass.THROTTLED, "rate_limit", "")),
        )
        assertEquals(
            OutboxStatus.FAILED_TERMINAL,
            OutboxStateMachine.failureStateFor(DomainError(ErrorClass.TERMINAL, "bad_number", "")),
        )
    }
}
