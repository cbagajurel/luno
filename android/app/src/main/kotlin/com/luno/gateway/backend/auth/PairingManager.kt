package com.luno.gateway.backend.auth

import com.luno.gateway.backend.rest.DeviceInfo
import com.luno.gateway.backend.rest.EnrollException
import com.luno.gateway.backend.rest.EnrollRequest
import com.luno.gateway.backend.rest.EnrollResponse
import com.luno.gateway.backend.rest.EnrollStatusRequest
import com.luno.gateway.backend.rest.RestClient
import com.luno.gateway.backend.rest.STATUS_APPROVED
import com.luno.gateway.backend.rest.STATUS_DENIED
import com.luno.gateway.backend.rest.STATUS_PENDING
import com.luno.gateway.logging.LunoLogger
import java.security.SecureRandom
import java.util.Base64

/**
 * Runs enrollment (§3): submit a pairing code — typed or scanned — and persist
 * whatever credential the backend issues.
 *
 * The node is a **pairing client only**. It does not know or check whether a
 * pairing session has expired, how many devices it admits, whether it was
 * revoked, or whether approval is required; it submits the code and renders the
 * backend's verdict. That is what keeps every enrollment policy configurable
 * server-side without an app release.
 */
class PairingManager(
    private val restClient: RestClient,
    private val credentialStore: DeviceCredentialStore,
    private val pendingStore: PendingEnrollmentStore,
    private val deviceInfo: DeviceInfo,
    private val logger: LunoLogger,
    private val clock: () -> Long = System::currentTimeMillis,
    private val nonceSource: () -> String = ::randomNonce,
) {
    fun parsePayload(raw: String): PairingPayloadResult = PairingPayloadCodec.parse(raw)

    suspend fun pairFromPayload(raw: String): PairingResult =
        when (val parsed = parsePayload(raw)) {
            is PairingPayloadResult.Ok -> pair(parsed.payload)
            is PairingPayloadResult.UnsupportedVersion ->
                PairingResult.Failure(
                    PairingError.UNKNOWN,
                    "This pairing code needs a newer version of Luno (format v${parsed.version}).",
                )
            is PairingPayloadResult.Malformed ->
                PairingResult.Failure(PairingError.INVALID_CODE, parsed.reason)
        }

    suspend fun pair(payload: PairingPayload): PairingResult =
        pair(
            backendUrl = payload.backendUrl,
            pairingCode = payload.pairingCode,
            sessionId = payload.sessionId,
            label = payload.label,
            pin = payload.pin,
        )

    suspend fun pair(
        backendUrl: String,
        pairingCode: String,
        sessionId: String? = null,
        label: String? = null,
        pin: String? = null,
    ): PairingResult {
        val normalizedUrl = backendUrl.trim().trimEnd('/')
        return runEnrollment(normalizedUrl, label, pin) {
            restClient.enroll(
                normalizedUrl,
                EnrollRequest(
                    pairingCode = pairingCode.trim(),
                    nonce = nonceSource(),
                    deviceInfo = deviceInfo,
                    sessionId = sessionId,
                ),
            )
        }
    }

    fun pendingEnrollment(): PendingEnrollment? = pendingStore.load()

    /**
     * Asks the backend whether a pending enrollment has been approved. Returns
     * null when nothing is pending, so a caller can poll unconditionally.
     */
    suspend fun checkPendingApproval(): PairingResult? {
        val pending = pendingStore.load() ?: return null
        return runEnrollment(pending.backendUrl, pending.label, pin = null) {
            restClient.enrollStatus(
                pending.backendUrl,
                EnrollStatusRequest(enrollmentId = pending.enrollmentId, nonce = nonceSource()),
            )
        }
    }

    /** Abandons a wait for approval. The backend keeps its own record; this only frees the node. */
    fun cancelPendingApproval() {
        pendingStore.clear()
    }

    fun unpair() {
        credentialStore.clear()
        pendingStore.clear()
        logger.i(TAG, "unpaired; credential cleared")
    }

    fun isPaired(): Boolean = credentialStore.isPaired()

    private suspend fun runEnrollment(
        backendUrl: String,
        label: String?,
        pin: String?,
        request: suspend () -> EnrollResponse,
    ): PairingResult =
        try {
            resolve(backendUrl, request(), label, pin)
        } catch (e: EnrollException) {
            logger.w(TAG, "enrollment refused: ${e.error} (${e.rawCode ?: "no code"})")
            PairingResult.Failure(e.error, e.message ?: e.error.name, e.rawCode)
        } catch (e: Exception) {
            logger.e(TAG, "enrollment crashed", e)
            PairingResult.Failure(PairingError.INTERNAL, e.message ?: "internal error")
        }

    private fun resolve(
        backendUrl: String,
        response: EnrollResponse,
        label: String?,
        pin: String?,
    ): PairingResult =
        when (response.status.trim().lowercase()) {
            STATUS_APPROVED -> approve(backendUrl, response, pin)
            STATUS_PENDING -> awaitApproval(backendUrl, response, label)
            STATUS_DENIED -> {
                pendingStore.clear()
                logger.i(TAG, "enrollment denied")
                PairingResult.Failure(PairingError.APPROVAL_DENIED, "This device was not approved.")
            }
            else ->
                PairingResult.Failure(
                    PairingError.SERVER,
                    "Unsupported enrollment status '${response.status}'.",
                )
        }

    private fun approve(
        backendUrl: String,
        response: EnrollResponse,
        pin: String?,
    ): PairingResult {
        val deviceId = response.deviceId
        val credential = response.credential
        if (deviceId.isNullOrBlank() || credential.isNullOrBlank()) {
            return PairingResult.Failure(PairingError.SERVER, "Backend approved enrollment without a credential.")
        }
        credentialStore.save(
            DeviceCredential(
                backendUrl = backendUrl,
                wsUrl = response.wsUrl ?: deriveWsUrl(backendUrl),
                deviceId = deviceId,
                credential = credential,
                pin = pin,
            ),
        )
        pendingStore.clear()
        logger.i(TAG, "paired as device $deviceId")
        return PairingResult.Success(deviceId)
    }

    private fun awaitApproval(
        backendUrl: String,
        response: EnrollResponse,
        label: String?,
    ): PairingResult {
        val enrollmentId = response.enrollmentId
        if (enrollmentId.isNullOrBlank()) {
            return PairingResult.Failure(PairingError.SERVER, "Backend asked for approval without an enrollment id.")
        }
        val retryAfter = (response.retryAfterMs ?: DEFAULT_RETRY_MILLIS).coerceIn(MIN_RETRY_MILLIS, MAX_RETRY_MILLIS)
        val existing = pendingStore.load()?.takeIf { it.enrollmentId == enrollmentId }
        pendingStore.save(
            PendingEnrollment(
                backendUrl = backendUrl,
                enrollmentId = enrollmentId,
                retryAfterMillis = retryAfter,
                startedAtMillis = existing?.startedAtMillis ?: clock(),
                label = label ?: existing?.label,
            ),
        )
        logger.i(TAG, "enrollment $enrollmentId awaiting approval")
        return PairingResult.Pending(enrollmentId, retryAfter)
    }

    private fun deriveWsUrl(backendUrl: String): String =
        backendUrl
            .replaceFirst("https://", "wss://")
            .replaceFirst("http://", "ws://")
            .trimEnd('/') + "/ws"

    companion object {
        private const val TAG = "PairingManager"
        private const val DEFAULT_RETRY_MILLIS = 3_000L
        private const val MIN_RETRY_MILLIS = 1_000L
        private const val MAX_RETRY_MILLIS = 60_000L
    }
}

private val secureRandom = SecureRandom()

private fun randomNonce(): String {
    val bytes = ByteArray(16)
    secureRandom.nextBytes(bytes)
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
}
