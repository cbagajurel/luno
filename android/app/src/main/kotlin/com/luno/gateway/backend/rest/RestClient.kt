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
)

@Serializable
data class EnrollRequest(
    val pairingCode: String,
    val deviceInfo: DeviceInfo,
)

@Serializable
data class EnrollResponse(
    val deviceId: String,
    val credential: String,
    val wsUrl: String? = null,
)

class EnrollException(
    val error: PairingError,
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

/**
 * HTTPS client for enrollment and (later) degraded event fallback. Plain HTTP is
 * refused — the credential must never cross the wire in the clear (§8, threat
 * model) — unless [allowInsecure] is set, which the app only does in debug builds
 * for LAN pairing. Endpoints are runtime config from pairing, never compiled in.
 */
class RestClient(
    private val client: OkHttpClient = OkHttpClient(),
    private val json: Json = Json { ignoreUnknownKeys = true },
    private val allowInsecure: Boolean = false,
) {
    suspend fun enroll(backendUrl: String, body: EnrollRequest): EnrollResponse =
        withContext(Dispatchers.IO) {
            val secure = backendUrl.startsWith("https://")
            if (!secure && !(allowInsecure && backendUrl.startsWith("http://"))) {
                throw EnrollException(PairingError.NOT_SECURE, "enrollment must use https")
            }
            val url = backendUrl.trimEnd('/') + "/enroll"
            val request =
                Request.Builder()
                    .url(url)
                    .post(json.encodeToString(EnrollRequest.serializer(), body).toRequestBody(JSON))
                    .build()

            val response =
                try {
                    client.newCall(request).execute()
                } catch (e: IOException) {
                    throw EnrollException(PairingError.NETWORK, e.message ?: "network error", e)
                }

            response.use {
                val text = it.body?.string().orEmpty()
                when {
                    it.isSuccessful ->
                        try {
                            json.decodeFromString(EnrollResponse.serializer(), text)
                        } catch (e: Exception) {
                            throw EnrollException(PairingError.SERVER, "malformed enroll response", e)
                        }

                    it.code in REJECTED_CODES ->
                        throw EnrollException(PairingError.INVALID_CODE, "pairing code rejected (${it.code})")

                    else ->
                        throw EnrollException(PairingError.SERVER, "enroll failed (${it.code})")
                }
            }
        }

    companion object {
        private val JSON = "application/json; charset=utf-8".toMediaType()
        private val REJECTED_CODES = setOf(400, 401, 403, 409, 410)
    }
}
