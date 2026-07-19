package com.luno.gateway.backend.rest

import com.luno.gateway.backend.auth.PairingError
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

@Serializable
data class DeviceInfo(
    val model: String,
    val manufacturer: String,
    val androidSdk: Int,
    val appVersion: String,
    val installId: String,
    val platform: String = "android",
)

/**
 * The enrollment request (§3). [nonce] is fresh per attempt so a backend can
 * reject a replayed body, and [publicKey] is a reserved slot for the future
 * request-signing / mTLS upgrade — reserving it now keeps that change additive.
 */
@Serializable
data class EnrollRequest(
    val pairingCode: String,
    val nonce: String,
    val deviceInfo: DeviceInfo,
    val protocolVersion: Int = ENROLL_PROTOCOL_VERSION,
    val sessionId: String? = null,
    val publicKey: String? = null,
)

@Serializable
data class EnrollStatusRequest(
    val enrollmentId: String,
    val nonce: String,
    val protocolVersion: Int = ENROLL_PROTOCOL_VERSION,
)

/**
 * The backend's verdict. [status] is what keeps pairing policy backend-owned: a
 * server that requires operator approval answers `pending` with an
 * [enrollmentId] to poll, one that does not answers `approved` with a
 * credential. A missing [status] means approved, so servers written against the
 * original two-field response keep working.
 */
@Serializable
data class EnrollResponse(
    val status: String = STATUS_APPROVED,
    val deviceId: String? = null,
    val credential: String? = null,
    val wsUrl: String? = null,
    val enrollmentId: String? = null,
    val retryAfterMs: Long? = null,
)

@Serializable
data class EnrollErrorBody(
    val error: String? = null,
    val message: String? = null,
)

const val ENROLL_PROTOCOL_VERSION = 1
const val STATUS_APPROVED = "approved"
const val STATUS_PENDING = "pending"
const val STATUS_DENIED = "denied"

class EnrollException(
    val error: PairingError,
    message: String,
    val rawCode: String? = null,
    cause: Throwable? = null,
) : Exception(message, cause)

/**
 * HTTPS client for enrollment and (later) degraded event fallback. Plain HTTP is
 * refused — the pairing code and credential must never cross the wire in the
 * clear (§8, threat model) — unless [allowInsecure] is set, which the app only
 * does in debug builds for LAN pairing. Endpoints are runtime config from
 * pairing, never compiled in.
 */
class RestClient(
    private val client: OkHttpClient = OkHttpClient(),
    private val json: Json = Json { ignoreUnknownKeys = true },
    private val allowInsecure: Boolean = false,
) {
    suspend fun enroll(
        backendUrl: String,
        body: EnrollRequest,
    ): EnrollResponse = post(backendUrl, "/enroll", json.encodeToString(EnrollRequest.serializer(), body))

    /**
     * Polls a pending enrollment. Separate from [enroll] so a wait never re-spends
     * the pairing code — under the default single-use policy a second `/enroll`
     * would be rejected as exhausted.
     */
    suspend fun enrollStatus(
        backendUrl: String,
        body: EnrollStatusRequest,
    ): EnrollResponse =
        post(backendUrl, "/enroll/status", json.encodeToString(EnrollStatusRequest.serializer(), body))

    private suspend fun post(
        backendUrl: String,
        path: String,
        payload: String,
    ): EnrollResponse =
        withContext(Dispatchers.IO) {
            val secure = backendUrl.startsWith("https://", ignoreCase = true)
            if (!secure && !(allowInsecure && backendUrl.startsWith("http://", ignoreCase = true))) {
                throw EnrollException(PairingError.NOT_SECURE, "enrollment must use https")
            }
            val request =
                Request.Builder()
                    .url(backendUrl.trimEnd('/') + path)
                    .post(payload.toRequestBody(JSON))
                    .build()

            val response =
                try {
                    client.newCall(request).execute()
                } catch (e: IOException) {
                    throw EnrollException(PairingError.NETWORK, e.message ?: "network error", cause = e)
                }

            response.use {
                val text = it.body?.string().orEmpty()
                if (it.isSuccessful) {
                    try {
                        json.decodeFromString(EnrollResponse.serializer(), text)
                    } catch (e: Exception) {
                        throw EnrollException(PairingError.SERVER, "malformed enroll response", cause = e)
                    }
                } else {
                    throw failureFor(it.code, text)
                }
            }
        }

    /**
     * The backend's own `error` code wins; the HTTP status is only a fallback for
     * servers that don't send one. Unknown codes survive as [PairingError.UNKNOWN]
     * plus the raw string, so a new backend verdict reaches the UI without
     * needing a node release.
     */
    private fun failureFor(
        code: Int,
        text: String,
    ): EnrollException {
        val body =
            try {
                json.decodeFromString(EnrollErrorBody.serializer(), text)
            } catch (e: Exception) {
                null
            }
        val wireCode = body?.error
        val mapped = PairingError.fromWireCode(wireCode)
        val message = body?.message ?: wireCode ?: "enroll failed ($code)"
        return EnrollException(
            error =
                mapped ?: if (code in REJECTED_CODES) PairingError.INVALID_CODE else PairingError.SERVER,
            message = message,
            // Only a real code is worth passing on; prose is already in the message.
            rawCode = wireCode.takeIf { mapped != null },
        )
    }

    companion object {
        private val JSON = "application/json; charset=utf-8".toMediaType()
        private val REJECTED_CODES = setOf(400, 401, 403, 409, 410)
    }
}
