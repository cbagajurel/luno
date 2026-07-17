package com.luno.gateway.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RateLimiterTest {
    @Test
    fun `unlimited by default`() {
        val limiter = RateLimiter()
        repeat(1000) { assertTrue(limiter.tryAcquire(it.toLong())) }
    }

    @Test
    fun `caps at the configured per-minute rate`() {
        val limiter = RateLimiter(perMinute = 2)
        assertTrue(limiter.tryAcquire(0))
        assertTrue(limiter.tryAcquire(1))
        assertFalse(limiter.tryAcquire(2)) // third within the window is denied
    }

    @Test
    fun `window slides so capacity returns after 60s`() {
        val limiter = RateLimiter(perMinute = 1)
        assertTrue(limiter.tryAcquire(0))
        assertFalse(limiter.tryAcquire(30_000))
        assertTrue(limiter.tryAcquire(60_000)) // first hit has aged out
    }

    @Test
    fun `setLimit reconfigures and clears the window`() {
        val limiter = RateLimiter(perMinute = 1)
        assertTrue(limiter.tryAcquire(0))
        assertFalse(limiter.tryAcquire(1))
        limiter.setLimit(5)
        assertTrue(limiter.tryAcquire(2))
    }
}
