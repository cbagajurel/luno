package com.luno.gateway.security

/**
 * Authenticated encryption of small secrets at rest. The real implementation
 * ([KeystoreManager]) binds the key to the device's hardware keystore; tests use
 * an in-memory fake. Both [encrypt] and [decrypt] round-trip a self-describing
 * blob (IV prepended), so callers store only the opaque output.
 */
interface SecretCrypto {
    fun encrypt(plaintext: ByteArray): ByteArray

    fun decrypt(payload: ByteArray): ByteArray
}
