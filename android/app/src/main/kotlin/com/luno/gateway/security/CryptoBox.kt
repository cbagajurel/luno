package com.luno.gateway.security

import java.util.Base64

/**
 * String-level authenticated encryption for PII at rest (§ threat model): message
 * bodies and phone numbers are sealed with a Keystore-bound key before they touch
 * the DB, so a stolen/rooted device yields ciphertext, not readable SMS. Wraps a
 * [SecretCrypto] (real key in the AndroidKeyStore) and emits Base64 so callers store
 * one opaque `TEXT` column.
 */
class CryptoBox(private val crypto: SecretCrypto) {
    fun seal(plaintext: String): String =
        Base64.getEncoder().encodeToString(crypto.encrypt(plaintext.toByteArray(Charsets.UTF_8)))

    fun open(sealed: String): String =
        String(crypto.decrypt(Base64.getDecoder().decode(sealed)), Charsets.UTF_8)

    /** Resilient read: a row that predates encryption or whose key was invalidated yields null, never a crash. */
    fun openOrNull(sealed: String): String? =
        try {
            open(sealed)
        } catch (e: Exception) {
            null
        }
}
