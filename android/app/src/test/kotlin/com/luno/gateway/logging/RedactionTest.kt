package com.luno.gateway.logging

import org.junit.Assert.assertEquals
import org.junit.Test

class RedactionTest {
    @Test
    fun `masks a phone number keeping the last two digits`() {
        assertEquals("sent to +*********22", Redaction.redact("sent to +15551112222"))
    }

    @Test
    fun `masks a bare number without a plus`() {
        assertEquals("to ********22", Redaction.redact("to 5551112222"))
    }

    @Test
    fun `leaves short numbers and non-numeric text untouched`() {
        assertEquals("subId=2 seq=128 ok", Redaction.redact("subId=2 seq=128 ok"))
    }

    @Test
    fun `is idempotent`() {
        val once = Redaction.redact("call +15551112222 now")
        assertEquals(once, Redaction.redact(once))
    }
}
