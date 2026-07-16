package com.luno.gateway.backend

import com.luno.gateway.backend.ws.ReconnectPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReconnectPolicyTest {
    @Test
    fun `ceiling grows exponentially and caps at max`() {
        val policy = ReconnectPolicy(baseDelayMillis = 1_000, maxDelayMillis = 60_000, factor = 2.0)
        val ceilings = mutableListOf<Long>()
        repeat(10) {
            ceilings.add(policy.ceilingMillis())
            policy.nextDelayMillis()
        }
        assertEquals(1_000, ceilings[0])
        assertEquals(2_000, ceilings[1])
        assertEquals(4_000, ceilings[2])
        assertEquals(8_000, ceilings[3])
        assertEquals(60_000, ceilings[9])
        assertTrue(ceilings.all { it <= 60_000 })
    }

    @Test
    fun `full jitter stays within zero and the current ceiling`() {
        val policy = ReconnectPolicy()
        repeat(200) {
            val ceiling = policy.ceilingMillis()
            val delay = policy.nextDelayMillis()
            assertTrue("delay $delay outside [0, $ceiling]", delay in 0..ceiling)
        }
    }

    @Test
    fun `backoff resets only after a stable READY`() {
        val policy = ReconnectPolicy(stableReadyMillis = 5_000)
        repeat(3) { policy.nextDelayMillis() }
        assertEquals(3, policy.attempt)

        policy.onReady(1_000)
        policy.onConnectionEnded(1_000 + 4_999)
        assertEquals("a brief READY must not reset backoff", 3, policy.attempt)

        policy.onReady(10_000)
        policy.onConnectionEnded(10_000 + 5_000)
        assertEquals("a stable READY resets backoff", 0, policy.attempt)
    }

    @Test
    fun `connection ending without a prior READY never resets`() {
        val policy = ReconnectPolicy(stableReadyMillis = 5_000)
        repeat(2) { policy.nextDelayMillis() }
        policy.onConnectionEnded(1_000_000)
        assertEquals(2, policy.attempt)
    }

    @Test
    fun `reset returns to the base delay`() {
        val policy = ReconnectPolicy(baseDelayMillis = 1_000)
        repeat(5) { policy.nextDelayMillis() }
        policy.reset()
        assertEquals(0, policy.attempt)
        assertEquals(1_000, policy.ceilingMillis())
    }
}
