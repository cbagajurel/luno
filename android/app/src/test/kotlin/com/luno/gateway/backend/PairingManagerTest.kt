package com.luno.gateway.backend

import com.luno.gateway.backend.auth.DeviceCredentialStore
import com.luno.gateway.backend.auth.PairingError
import com.luno.gateway.backend.auth.PairingManager
import com.luno.gateway.backend.auth.PairingResult
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
import org.junit.Assert.assertTrue
import org.junit.Test

class PairingManagerTest {
    private val https = MockHttps()
    private val kv = FakeKeyValueStore()
    private val store = DeviceCredentialStore(kv, FakeCrypto(), testLogger())
    private val manager =
        PairingManager(
            restClient = RestClient(client = https.client),
            credentialStore = store,
            deviceInfo = DeviceInfo("Pixel", "Google", 34, "1.0"),
            logger = testLogger(),
        )

    @After
    fun tearDown() = https.close()

    @Test
    fun `a successful pair persists the credential and resolves the ws url`() = runTest {
        https.server.enqueue(
            MockResponse().setBody("""{"deviceId":"dev-9","credential":"tok","wsUrl":"wss://x/ws"}"""),
        )

        val result = manager.pair(https.url(), "code-123")

        assertTrue(result is PairingResult.Success)
        assertEquals("dev-9", (result as PairingResult.Success).deviceId)
        assertTrue(manager.isPaired())
        assertEquals("wss://x/ws", store.load()!!.wsUrl)
    }

    @Test
    fun `a missing ws url falls back to one derived from the backend host`() = runTest {
        https.server.enqueue(MockResponse().setBody("""{"deviceId":"dev-9","credential":"tok"}"""))

        manager.pair(https.url(), "code-123")

        assertTrue(store.load()!!.wsUrl.startsWith("wss://"))
    }

    @Test
    fun `a rejected code fails without persisting anything`() = runTest {
        https.server.enqueue(MockResponse().setResponseCode(401))

        val result = manager.pair(https.url(), "bad-code")

        assertTrue(result is PairingResult.Failure)
        assertEquals(PairingError.INVALID_CODE, (result as PairingResult.Failure).error)
        assertFalse(manager.isPaired())
    }

    @Test
    fun `unpair clears a stored credential`() = runTest {
        https.server.enqueue(MockResponse().setBody("""{"deviceId":"dev-9","credential":"tok"}"""))
        manager.pair(https.url(), "code-123")
        assertTrue(manager.isPaired())

        manager.unpair()

        assertFalse(manager.isPaired())
    }
}
