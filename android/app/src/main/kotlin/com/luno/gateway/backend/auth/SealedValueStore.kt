package com.luno.gateway.backend.auth

import com.luno.gateway.logging.LunoLogger
import com.luno.gateway.security.SecretCrypto
import java.util.Base64
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json

/**
 * One small secret at rest: serialized, sealed by [SecretCrypto], kept as a
 * single opaque string. If the sealed blob can't be opened — e.g. the Keystore
 * key was invalidated by a lockscreen change — the value is purged and reported
 * absent, so the node re-enrolls instead of crash-looping (§3, M13 edge cases).
 */
class SealedValueStore<T : Any>(
    private val store: KeyValueStore,
    private val crypto: SecretCrypto,
    private val logger: LunoLogger,
    private val key: String,
    private val serializer: KSerializer<T>,
    private val tag: String,
    private val json: Json = Json,
) {
    fun exists(): Boolean = load() != null

    fun load(): T? {
        val sealed = store.getString(key) ?: return null
        return try {
            val plaintext = crypto.decrypt(Base64.getDecoder().decode(sealed))
            json.decodeFromString(serializer, String(plaintext, Charsets.UTF_8))
        } catch (e: Exception) {
            logger.w(tag, "stored value unreadable; discarding", e)
            clear()
            null
        }
    }

    fun save(value: T) {
        val plaintext = json.encodeToString(serializer, value)
        store.putString(key, Base64.getEncoder().encodeToString(crypto.encrypt(plaintext.toByteArray(Charsets.UTF_8))))
    }

    fun clear() {
        store.remove(key)
    }
}
