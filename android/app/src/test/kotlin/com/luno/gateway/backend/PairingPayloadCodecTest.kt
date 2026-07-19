package com.luno.gateway.backend

import com.luno.gateway.backend.auth.PairingPayloadCodec
import com.luno.gateway.backend.auth.PairingPayloadResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PairingPayloadCodecTest {
    private fun ok(raw: String) = PairingPayloadCodec.parse(raw) as PairingPayloadResult.Ok

    @Test
    fun `parses a full pairing link`() {
        val payload =
            ok(
                "luno://pair?v=1&u=https%3A%2F%2Fgw.example.com&c=ABCD-1234" +
                    "&s=ses_9f3&l=Acme%20Gateway&p=sha256%2FAAAA",
            ).payload

        assertEquals("https://gw.example.com", payload.backendUrl)
        assertEquals("ABCD-1234", payload.pairingCode)
        assertEquals("ses_9f3", payload.sessionId)
        assertEquals("Acme Gateway", payload.label)
        assertEquals("sha256/AAAA", payload.pin)
    }

    @Test
    fun `parses the JSON form to the same payload`() {
        val payload = ok("""{"v":1,"u":"https://gw.example.com","c":"ABCD-1234"}""").payload

        assertEquals("https://gw.example.com", payload.backendUrl)
        assertEquals("ABCD-1234", payload.pairingCode)
        assertNull(payload.sessionId)
    }

    @Test
    fun `optional fields are absent rather than blank`() {
        val payload = ok("luno://pair?v=1&u=https%3A%2F%2Fgw.example.com&c=X&s=&l=").payload

        assertNull(payload.sessionId)
        assertNull(payload.label)
        assertNull(payload.pin)
    }

    @Test
    fun `a trailing slash on the backend url is normalized away`() {
        assertEquals(
            "https://gw.example.com",
            ok("luno://pair?v=1&u=https%3A%2F%2Fgw.example.com%2F&c=X").payload.backendUrl,
        )
    }

    @Test
    fun `a newer format version is reported, not guessed at`() {
        val result = PairingPayloadCodec.parse("luno://pair?v=2&u=https%3A%2F%2Fgw.example.com&c=X")

        assertEquals(PairingPayloadResult.UnsupportedVersion(2), result)
    }

    @Test
    fun `payloads missing required fields are malformed`() {
        val cases =
            listOf(
                "luno://pair?v=1&c=X",
                "luno://pair?v=1&u=https%3A%2F%2Fgw.example.com",
                "luno://pair?u=https%3A%2F%2Fgw.example.com&c=X",
                "luno://pair",
                """{"v":1,"c":"X"}""",
            )

        cases.forEach {
            assertTrue("expected malformed for '$it'", PairingPayloadCodec.parse(it) is PairingPayloadResult.Malformed)
        }
    }

    @Test
    fun `a non-http backend url is rejected before any request is attempted`() {
        val result = PairingPayloadCodec.parse("luno://pair?v=1&u=ftp%3A%2F%2Fgw.example.com&c=X")

        assertTrue(result is PairingPayloadResult.Malformed)
    }

    @Test
    fun `unrelated QR content is rejected`() {
        listOf("", "   ", "https://example.com", "hello world", "{not json").forEach {
            assertTrue("expected malformed for '$it'", PairingPayloadCodec.parse(it) is PairingPayloadResult.Malformed)
        }
    }
}
