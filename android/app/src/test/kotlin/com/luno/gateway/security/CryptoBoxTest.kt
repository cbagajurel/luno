package com.luno.gateway.security

import com.luno.gateway.testutil.FakeCrypto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CryptoBoxTest {
    private val box = CryptoBox(FakeCrypto())

    @Test
    fun `seal then open round-trips and the sealed form is not the plaintext`() {
        val plaintext = "meet me at the pier +15551112222"
        val sealed = box.seal(plaintext)
        assertNotEquals(plaintext, sealed)
        assertEquals(plaintext, box.open(sealed))
    }

    @Test
    fun `openOrNull yields null on garbage instead of throwing`() {
        assertNull(box.openOrNull("not-base64-@@@"))
    }
}
