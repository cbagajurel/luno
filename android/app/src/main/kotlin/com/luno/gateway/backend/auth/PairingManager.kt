package com.luno.gateway.backend.auth

import com.luno.gateway.backend.rest.DeviceInfo
import com.luno.gateway.backend.rest.EnrollException
import com.luno.gateway.backend.rest.EnrollRequest
import com.luno.gateway.backend.rest.RestClient
import com.luno.gateway.logging.LunoLogger

/**
 * Runs the one-time enrollment (§3): POST /enroll with a short-lived pairing
 * code, then persist the returned device credential sealed to the Keystore.
 * The WS endpoint comes from the backend's response (runtime config); if omitted
 * it's derived from the enrollment host as a convenience fallback.
 */
class PairingManager(
    private val restClient: RestClient,
    private val credentialStore: DeviceCredentialStore,
    private val deviceInfo: DeviceInfo,
    private val logger: LunoLogger,
) {
    suspend fun pair(backendUrl: String, pairingCode: String): PairingResult {
        val normalizedUrl = backendUrl.trim()
        return try {
            val response = restClient.enroll(normalizedUrl, EnrollRequest(pairingCode.trim(), deviceInfo))
            val wsUrl = response.wsUrl ?: deriveWsUrl(normalizedUrl)
            credentialStore.save(
                DeviceCredential(
                    backendUrl = normalizedUrl,
                    wsUrl = wsUrl,
                    deviceId = response.deviceId,
                    credential = response.credential,
                ),
            )
            logger.i(TAG, "paired as device ${response.deviceId}")
            PairingResult.Success(response.deviceId)
        } catch (e: EnrollException) {
            logger.w(TAG, "pairing failed: ${e.error}")
            PairingResult.Failure(e.error, e.message ?: e.error.name)
        } catch (e: Exception) {
            logger.e(TAG, "pairing crashed", e)
            PairingResult.Failure(PairingError.INTERNAL, e.message ?: "internal error")
        }
    }

    fun unpair() {
        credentialStore.clear()
        logger.i(TAG, "unpaired; credential cleared")
    }

    fun isPaired(): Boolean = credentialStore.isPaired()

    private fun deriveWsUrl(backendUrl: String): String =
        backendUrl.replaceFirst("https://", "wss://").trimEnd('/') + "/ws"

    companion object {
        private const val TAG = "PairingManager"
    }
}
