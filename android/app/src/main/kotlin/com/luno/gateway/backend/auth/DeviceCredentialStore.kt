package com.luno.gateway.backend.auth

import com.luno.gateway.logging.LunoLogger
import com.luno.gateway.security.SecretCrypto
import kotlinx.serialization.json.Json

/** The device credential at rest, sealed by [SecretCrypto]. */
class DeviceCredentialStore(
    store: KeyValueStore,
    crypto: SecretCrypto,
    private val logger: LunoLogger,
    json: Json = Json,
) {
    private val sealed =
        SealedValueStore(store, crypto, logger, KEY_CREDENTIAL, DeviceCredential.serializer(), TAG, json)

    fun isPaired(): Boolean = sealed.exists()

    fun load(): DeviceCredential? = sealed.load()

    fun save(credential: DeviceCredential) {
        sealed.save(credential)
        logger.i(TAG, "credential stored for device ${credential.deviceId}")
    }

    fun clear() = sealed.clear()

    companion object {
        private const val TAG = "CredentialStore"
        private const val KEY_CREDENTIAL = "device_credential"
    }
}
