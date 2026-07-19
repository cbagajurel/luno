package com.luno.gateway.backend

import com.luno.gateway.backend.auth.DeviceCredentialStore
import com.luno.gateway.backend.auth.PairingError
import com.luno.gateway.backend.auth.PairingManager
import com.luno.gateway.backend.auth.PairingResult
import com.luno.gateway.backend.auth.PendingEnrollmentStore
import com.luno.gateway.backend.rest.DeviceInfo
import com.luno.gateway.backend.rest.RestClient
import com.luno.gateway.testutil.FakeCrypto
import com.luno.gateway.testutil.FakeKeyValueStore
import com.luno.gateway.testutil.MockHttps
import com.luno.gateway.testutil.testLogger
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PairingManagerTest {
    private val https = MockHttps()
    private val kv = FakeKeyValueStore()
    private val store = DeviceCredentialStore(kv, FakeCrypto(), testLogger())
    private val pendingStore = PendingEnrollmentStore(kv, FakeCrypto(), testLogger())
    private val manager =
        PairingManager(
            restClient = RestClient(client = https.client),
            credentialStore = store,
            pendingStore = pendingStore,
            deviceInfo = DeviceInfo("Pixel", "Google", 34, "1.0", installId = "install-1"),
            logger = testLogger(),
        )

    @After
    fun tearDown() = https.close()

    private fun respond(
        body: String,
        code: Int = 200,
    ) = https.server.enqueue(MockResponse().setResponseCode(code).setBody(body))

    @Test
    fun `a successful pair persists the credential and resolves the ws url`() = runTest {
        respond("""{"deviceId":"dev-9","credential":"tok","wsUrl":"wss://x/ws"}""")

        val result = manager.pair(https.url(), "code-123")

        assertTrue(result is PairingResult.Success)
        assertEquals("dev-9", (result as PairingResult.Success).deviceId)
        assertTrue(manager.isPaired())
        assertEquals("wss://x/ws", store.load()!!.wsUrl)
    }

    @Test
    fun `a missing ws url falls back to one derived from the backend host`() = runTest {
        respond("""{"deviceId":"dev-9","credential":"tok"}""")

        manager.pair(https.url(), "code-123")

        assertTrue(store.load()!!.wsUrl.startsWith("wss://"))
    }

    @Test
    fun `a rejected code fails without persisting anything`() = runTest {
        respond("", code = 401)

        val result = manager.pair(https.url(), "bad-code")

        assertTrue(result is PairingResult.Failure)
        assertEquals(PairingError.INVALID_CODE, (result as PairingResult.Failure).error)
        assertFalse(manager.isPaired())
    }

    @Test
    fun `the backend's own verdict wins over the http status`() = runTest {
        respond("""{"error":"session_exhausted","message":"no slots left"}""", code = 403)

        val result = manager.pair(https.url(), "code-123") as PairingResult.Failure

        assertEquals(PairingError.SESSION_EXHAUSTED, result.error)
        assertEquals("no slots left", result.message)
    }

    @Test
    fun `an unrecognised verdict survives as UNKNOWN with its raw code`() = runTest {
        respond("""{"error":"org_quota_exceeded","message":"out of seats"}""", code = 403)

        val result = manager.pair(https.url(), "code-123") as PairingResult.Failure

        assertEquals(PairingError.UNKNOWN, result.error)
        assertEquals("org_quota_exceeded", result.rawCode)
        assertEquals("out of seats", result.message)
    }

    @Test
    fun `prose in the error field is a message, not a verdict`() = runTest {
        respond("""{"error":"pairing code rejected"}""", code = 401)

        val result = manager.pair(https.url(), "code-123") as PairingResult.Failure

        assertEquals(PairingError.INVALID_CODE, result.error)
        assertEquals("pairing code rejected", result.message)
        assertNull(result.rawCode)
    }

    @Test
    fun `an approval-gated backend parks the enrollment durably`() = runTest {
        respond("""{"status":"pending","enrollmentId":"enr-7","retryAfterMs":5000}""")

        val result = manager.pair(https.url(), "code-123", label = "Acme")

        assertEquals(PairingResult.Pending("enr-7", 5_000), result)
        assertFalse(manager.isPaired())
        val pending = manager.pendingEnrollment()
        assertNotNull(pending)
        assertEquals("enr-7", pending!!.enrollmentId)
        assertEquals("Acme", pending.label)
    }

    @Test
    fun `polling a pending enrollment issues the credential once approved`() = runTest {
        respond("""{"status":"pending","enrollmentId":"enr-7"}""")
        manager.pair(https.url(), "code-123")

        respond("""{"status":"approved","deviceId":"dev-9","credential":"tok"}""")
        val result = manager.checkPendingApproval()

        assertTrue(result is PairingResult.Success)
        assertTrue(manager.isPaired())
        assertNull(manager.pendingEnrollment())
    }

    @Test
    fun `polling uses the status endpoint so the pairing code is never re-spent`() = runTest {
        respond("""{"status":"pending","enrollmentId":"enr-7"}""")
        manager.pair(https.url(), "code-123")
        https.server.takeRequest()

        respond("""{"status":"pending","enrollmentId":"enr-7"}""")
        manager.checkPendingApproval()

        assertEquals("/enroll/status", https.server.takeRequest().path)
    }

    @Test
    fun `a denial clears the pending enrollment`() = runTest {
        respond("""{"status":"pending","enrollmentId":"enr-7"}""")
        manager.pair(https.url(), "code-123")

        respond("""{"status":"denied"}""")
        val result = manager.checkPendingApproval() as PairingResult.Failure

        assertEquals(PairingError.APPROVAL_DENIED, result.error)
        assertNull(manager.pendingEnrollment())
    }

    @Test
    fun `a server blip while waiting leaves the pending enrollment intact`() = runTest {
        respond("""{"status":"pending","enrollmentId":"enr-7"}""")
        manager.pair(https.url(), "code-123")

        respond("", code = 500)
        val result = manager.checkPendingApproval()

        assertTrue(result is PairingResult.Failure)
        assertNotNull(manager.pendingEnrollment())
    }

    @Test
    fun `checking with nothing pending is a no-op`() = runTest {
        assertNull(manager.checkPendingApproval())
    }

    @Test
    fun `an approval without a credential is refused rather than half-stored`() = runTest {
        respond("""{"status":"approved","deviceId":"dev-9"}""")

        val result = manager.pair(https.url(), "code-123") as PairingResult.Failure

        assertEquals(PairingError.SERVER, result.error)
        assertFalse(manager.isPaired())
    }

    @Test
    fun `an unknown enrollment status is refused rather than assumed successful`() = runTest {
        respond("""{"status":"quarantined"}""")

        val result = manager.pair(https.url(), "code-123") as PairingResult.Failure

        assertEquals(PairingError.SERVER, result.error)
        assertFalse(manager.isPaired())
    }

    @Test
    fun `pairing from a scanned payload enrolls and keeps the pin`() = runTest {
        respond("""{"deviceId":"dev-9","credential":"tok"}""")
        val raw = "luno://pair?v=1&u=${https.url()}&c=ABCD&p=sha256%2FAAAA"

        val result = manager.pairFromPayload(raw)

        assertTrue(result is PairingResult.Success)
        assertEquals("sha256/AAAA", store.load()!!.pin)
    }

    @Test
    fun `an unparseable payload never reaches the network`() = runTest {
        val result = manager.pairFromPayload("not-a-luno-code")

        assertTrue(result is PairingResult.Failure)
        assertEquals(0, https.server.requestCount)
    }

    @Test
    fun `unpair clears both the credential and any pending enrollment`() = runTest {
        respond("""{"deviceId":"dev-9","credential":"tok"}""")
        manager.pair(https.url(), "code-123")
        assertTrue(manager.isPaired())

        manager.unpair()

        assertFalse(manager.isPaired())
        assertNull(manager.pendingEnrollment())
    }
}
