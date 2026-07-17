package com.luno.gateway.testutil

import com.luno.gateway.backend.auth.KeyValueStore
import com.luno.gateway.security.SecretCrypto

class FakeKeyValueStore : KeyValueStore {
    val map = mutableMapOf<String, String>()

    override fun getString(key: String): String? = map[key]

    override fun putString(key: String, value: String) {
        map[key] = value
    }

    override fun remove(key: String) {
        map.remove(key)
    }

    override fun contains(key: String): Boolean = map.containsKey(key)

    override fun clear() = map.clear()
}

/** Identity "encryption": round-trips bytes so the store can be tested off-device. */
class FakeCrypto : SecretCrypto {
    override fun encrypt(plaintext: ByteArray): ByteArray = plaintext

    override fun decrypt(payload: ByteArray): ByteArray = payload
}
