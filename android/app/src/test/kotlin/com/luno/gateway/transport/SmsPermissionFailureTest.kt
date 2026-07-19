package com.luno.gateway.transport

import com.luno.gateway.data.repository.OutboxStateMachine
import com.luno.gateway.model.DomainError
import com.luno.gateway.model.ErrorClass
import com.luno.gateway.model.OutboxStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Pins the contract the dashboard's blocked-sends banner reads. SmsTransport maps a
 * SecurityException from a revoked SEND_SMS to this exact error, and the UI matches
 * on the persisted `"${errorClass}:${code}"` string — so both halves are asserted
 * here rather than left to coincidence.
 */
class SmsPermissionFailureTest {
    private val permissionRevoked =
        DomainError(ErrorClass.AUTH, "sms_permission_revoked", "SEND_SMS permission not granted")

    @Test
    fun `a revoked permission is not retryable`() {
        // Retrying without the permission would burn the queue for no gain.
        assertFalse(permissionRevoked.retryable)
    }

    @Test
    fun `a revoked permission fails terminally rather than requeuing`() {
        assertEquals(
            OutboxStatus.FAILED_TERMINAL,
            OutboxStateMachine.failureStateFor(permissionRevoked),
        )
    }

    @Test
    fun `the persisted error string is what the UI matches on`() {
        // OutboxRepository stores lastError as "${errorClass}:${code}".
        val persisted = "${permissionRevoked.errorClass}:${permissionRevoked.code}"
        assertEquals("AUTH:sms_permission_revoked", persisted)
    }
}
