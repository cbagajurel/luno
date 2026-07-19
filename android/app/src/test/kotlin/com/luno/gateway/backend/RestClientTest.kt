package com.luno.gateway.backend

import com.luno.gateway.backend.auth.PairingError
import com.luno.gateway.backend.rest.DeviceInfo
import com.luno.gateway.backend.rest.EnrollException
import com.luno.gateway.backend.rest.EnrollRequest
import com.luno.gateway.backend.rest.RestClient
import com.luno.gateway.testutil.MockHttps
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class RestClientTest {
    private val https = MockHttps()
    private val deviceInfo = DeviceInfo("Pixel", "Google", 34, "1.0", installId = "install-1")
    private val request = EnrollRequest("code-123", nonce = "nonce-1", deviceInfo = deviceInfo)

    private fun client() = RestClient(client = https.client)

    @After
    fun tearDown() = https.close()

    private fun expectError(expected: PairingError, block: suspend () -> Unit) = runTest {
        try {
            block()
            fail("expected EnrollException($expected)")
        } catch (e: EnrollException) {
            assertEquals(expected, e.error)
        }
    }

    @Test
    fun `successful enroll parses the response`() = runTest {
        https.server.enqueue(
            MockResponse().setBody(
                """{"deviceId":"dev-9","credential":"tok","wsUrl":"wss://x/ws"}""",
            ),
        )
        val response = client().enroll(https.url(), request)

        assertEquals("dev-9", response.deviceId)
        assertEquals("tok", response.credential)
        assertEquals("wss://x/ws", response.wsUrl)

        val recorded = https.server.takeRequest()
        assertEquals("/enroll", recorded.path)
    }

    @Test
    fun `unknown fields in the response are ignored`() = runTest {
        https.server.enqueue(
            MockResponse().setBody("""{"deviceId":"dev-9","credential":"tok","futureField":42}"""),
        )
        val response = client().enroll(https.url(), request)
        assertEquals("dev-9", response.deviceId)
    }

    @Test
    fun `a plain http url is refused before any request`() =
        expectError(PairingError.NOT_SECURE) {
            client().enroll("http://insecure.example", request)
        }

    @Test
    fun `a rejected pairing code maps to INVALID_CODE`() =
        expectError(PairingError.INVALID_CODE) {
            https.server.enqueue(MockResponse().setResponseCode(401))
            client().enroll(https.url(), request)
        }

    @Test
    fun `a server error maps to SERVER`() =
        expectError(PairingError.SERVER) {
            https.server.enqueue(MockResponse().setResponseCode(500))
            client().enroll(https.url(), request)
        }

    @Test
    fun `a malformed success body maps to SERVER`() =
        expectError(PairingError.SERVER) {
            https.server.enqueue(MockResponse().setBody("not json"))
            client().enroll(https.url(), request)
        }
}
