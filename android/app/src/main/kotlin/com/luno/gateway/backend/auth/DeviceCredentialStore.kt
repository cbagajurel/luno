package com.luno.gateway.backend.auth

import com.luno.gateway.logging.LunoLogger
import com.luno.gateway.security.SecretCrypto
import java.util.Base64
import kotlinx.serialization.json.Json

/**
 * The device credential at rest: serialized, sealed by [SecretCrypto], and kept
 * as one opaque string. If the sealed blob can't be opened — e.g. the Keystore
 * key was invalidated by a lockscreen change — the node treats itself as unpaired
 * and re-enrolls rather than crash-looping (§3, M13 edge cases).
 */
class DeviceCredentialStore(
    private val store: KeyValueStore,
    private val crypto: SecretCrypto,
    private val logger: LunoLogger,
    private val json: Json = Json,
) {
    fun isPaired(): Boolean = load() != null

    fun load(): DeviceCredential? {
        val sealed = store.getString(KEY_CREDENTIAL) ?: return null
        return try {
            val plaintext = crypto.decrypt(Base64.getDecoder().decode(sealed))
            json.decodeFromString(DeviceCredential.serializer(), String(plaintext, Charsets.UTF_8))
        } catch (e: Exception) {
            logger.w(TAG, "stored credential unreadable; treating as unpaired", e)
            clear()
            null
        }
    }

    fun save(credential: DeviceCredential) {
        val plaintext = json.encodeToString(DeviceCredential.serializer(), credential)
        val sealed = crypto.encrypt(plaintext.toByteArray(Charsets.UTF_8))
        store.putString(KEY_CREDENTIAL, Base64.getEncoder().encodeToString(sealed))
        logger.i(TAG, "credential stored for device ${credential.deviceId}")
    }

    fun clear() {
        store.remove(KEY_CREDENTIAL)
    }

    companion object {
        private const val TAG = "CredentialStore"
        private const val KEY_CREDENTIAL = "device_credential"
    }
}
