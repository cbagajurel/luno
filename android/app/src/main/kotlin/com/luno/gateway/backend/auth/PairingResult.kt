package com.luno.gateway.backend.auth

/**
 * Why an enrollment attempt did not yield a credential.
 *
 * The node enforces **no** pairing policy of its own: expiry, per-session
 * enrollment limits, revocation and approval are all the backend's decision.
 * These values are therefore a rendering of the backend's verdict, not a rule
 * the node applies. Unknown wire codes map to [UNKNOWN] with the raw string
 * preserved on the failure, so a backend can introduce new reasons without
 * waiting for a node release.
 */
enum class PairingError(val wireCode: String) {
    INVALID_CODE("invalid_code"),
    SESSION_EXPIRED("session_expired"),
    SESSION_EXHAUSTED("session_exhausted"),
    SESSION_REVOKED("session_revoked"),
    ALREADY_ENROLLED("already_enrolled"),
    APPROVAL_DENIED("approval_denied"),
    POLICY_REJECTED("policy_rejected"),
    NOT_SECURE("not_secure"),
    NETWORK("network"),
    SERVER("server"),
    INTERNAL("internal"),
    UNKNOWN("unknown"),
    ;

    companion object {
        private val byWireCode = entries.associateBy { it.wireCode }
        private val TOKEN = Regex("^[a-z0-9][a-z0-9_.:-]*$")

        /**
         * Null when the backend gave us nothing to classify on, so the caller falls
         * back to the HTTP status. Prose in the `error` field — plenty of servers
         * put a sentence there — is not a code and must not be mistaken for one,
         * or a plainly-rejected code would degrade to [UNKNOWN].
         */
        fun fromWireCode(code: String?): PairingError? =
            code?.trim()?.lowercase()?.takeIf { TOKEN.matches(it) }?.let { byWireCode[it] ?: UNKNOWN }
    }
}

sealed interface PairingResult {
    data class Success(val deviceId: String) : PairingResult

    /**
     * The backend accepted the pairing code but its policy requires an operator to
     * approve this device before a credential is issued. [enrollmentId] is a
     * non-secret handle the node polls with until the decision lands.
     */
    data class Pending(
        val enrollmentId: String,
        val retryAfterMillis: Long,
    ) : PairingResult

    data class Failure(
        val error: PairingError,
        val message: String,
        val rawCode: String? = null,
    ) : PairingResult
}
