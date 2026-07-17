package com.luno.gateway.security

import com.luno.gateway.testutil.FakeKeyValueStore
import org.junit.Assert.assertEquals
import org.junit.Test

class PolicyStoreTest {
    private val store = FakeKeyValueStore()
    private val policy = PolicyStore(store)

    @Test
    fun `defaults to unlimited and no allowlist`() {
        assertEquals(SendPolicy(), policy.load())
    }

    @Test
    fun `update merges only the provided fields and persists`() {
        policy.update(rateLimitPerMinute = 10, allowlist = null)
        policy.update(rateLimitPerMinute = null, allowlist = listOf("+15551112222"))

        val loaded = PolicyStore(store).load() // fresh instance reads the same store
        assertEquals(10, loaded.rateLimitPerMinute)
        assertEquals(listOf("+15551112222"), loaded.allowlist)
    }

    @Test
    fun `clear resets to defaults`() {
        policy.update(rateLimitPerMinute = 5, allowlist = listOf("+1"))
        policy.clear()
        assertEquals(SendPolicy(), policy.load())
    }
}
