package com.luno.gateway.backend.auth

import com.luno.gateway.logging.LunoLogger
import com.luno.gateway.security.SecretCrypto
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * An enrollment the backend accepted but has not yet approved. Held durably so a
 * device awaiting operator approval survives the app being closed or the process
 * being killed, and resumes polling on next start instead of forcing the operator
 * to burn a second pairing code.
 *
 * The pairing code itself is deliberately **not** kept: under the default
 * single-use policy it is already spent, and [enrollmentId] is the handle the
 * backend gave us to finish with.
 */
@Serializable
data class PendingEnrollment(
    val backendUrl: String,
    val enrollmentId: String,
    val retryAfterMillis: Long,
    val startedAtMillis: Long,
    val label: String? = null,
)

class PendingEnrollmentStore(
    store: KeyValueStore,
    crypto: SecretCrypto,
    logger: LunoLogger,
    json: Json = Json,
) {
    private val sealed =
        SealedValueStore(store, crypto, logger, KEY_PENDING, PendingEnrollment.serializer(), TAG, json)

    fun load(): PendingEnrollment? = sealed.load()

    fun save(pending: PendingEnrollment) = sealed.save(pending)

    fun clear() = sealed.clear()

    companion object {
        private const val TAG = "PendingEnrollment"
        private const val KEY_PENDING = "pending_enrollment"
    }
}
