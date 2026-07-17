package com.luno.gateway.backend

import com.luno.gateway.backend.auth.DeviceCredential
import com.luno.gateway.backend.auth.DeviceCredentialStore
import com.luno.gateway.testutil.FakeCrypto
import com.luno.gateway.testutil.FakeKeyValueStore
import com.luno.gateway.testutil.testLogger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceCredentialStoreTest {
    private val credential =
        DeviceCredential(
            backendUrl = "https://backend.example",
            wsUrl = "wss://backend.example/ws",
            deviceId = "dev-1",
            credential = "secret-token",
        )

    private fun store(kv: FakeKeyValueStore = FakeKeyValueStore()) =
        DeviceCredentialStore(kv, FakeCrypto(), testLogger())

    @Test
    fun `save then load round-trips the credential`() {
        val store = store()
        store.save(credential)
        assertEquals(credential, store.load())
    }

    @Test
    fun `isPaired reflects the stored credential`() {
        val store = store()
        assertFalse(store.isPaired())
        store.save(credential)
        assertTrue(store.isPaired())
    }

    @Test
    fun `clear removes the credential`() {
        val store = store()
        store.save(credential)
        store.clear()
        assertNull(store.load())
        assertFalse(store.isPaired())
    }

    @Test
    fun `an unreadable blob is treated as unpaired and purged`() {
        val kv = FakeKeyValueStore()
        kv.putString("device_credential", "not-base64-or-json!!!")
        val store = store(kv)

        assertNull(store.load())
        assertFalse(kv.contains("device_credential"))
    }
}
