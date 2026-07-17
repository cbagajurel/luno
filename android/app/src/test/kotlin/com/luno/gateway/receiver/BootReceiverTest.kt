package com.luno.gateway.receiver

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BootReceiverTest {
    @Test
    fun `auto-starts only when paired`() {
        assertTrue(BootReceiver.shouldAutoStart(paired = true))
        assertFalse(BootReceiver.shouldAutoStart(paired = false))
    }
}
