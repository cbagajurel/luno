package com.luno.gateway.backend.auth

import java.io.UnsupportedEncodingException
import java.net.URLDecoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Everything a node needs to attempt an enrollment, however it was delivered —
 * typed manually or scanned from a QR code. Deliberately thin: it carries the
 * *inputs* to enrollment and none of the policy governing it. Expiry, how many
 * devices a session admits and whether approval is required are backend state
 * and never travel in the payload, so a stale QR code cannot mislead the node
 * into thinking it is still valid.
 *
 * [sessionId] is a non-secret handle for logging and for backends that prefer to
 * look the session up before verifying the code. [pin] is an optional base64
 * SHA-256 SPKI pin, which lets QR pairing bootstrap certificate pinning for
 * self-hosted deployments (see `security/Pinning`).
 */
data class PairingPayload(
    val backendUrl: String,
    val pairingCode: String,
    val sessionId: String? = null,
    val label: String? = null,
    val pin: String? = null,
)

sealed interface PairingPayloadResult {
    data class Ok(val payload: PairingPayload) : PairingPayloadResult

    /** A newer node format. Tell the operator to update rather than guess at the fields. */
    data class UnsupportedVersion(val version: Int) : PairingPayloadResult

    data class Malformed(val reason: String) : PairingPayloadResult
}

/**
 * Parses the two wire forms a backend may put in a QR code:
 *
 * ```
 * luno://pair?v=1&u=https%3A%2F%2Fgw.example.com&c=ABCD-1234&s=ses_9f3&l=Acme&p=sha256%2F...
 * {"v":1,"u":"https://gw.example.com","c":"ABCD-1234","s":"ses_9f3","l":"Acme","p":"sha256/..."}
 * ```
 *
 * Both are versioned so the format can evolve without breaking installed nodes.
 * Parsing is pure (no `android.net.Uri`) to keep it unit-testable off-device and
 * reusable by future desktop/IoT nodes.
 */
object PairingPayloadCodec {
    const val VERSION = 1
    const val SCHEME_PREFIX = "luno://pair"

    private val json = Json { ignoreUnknownKeys = true }

    fun parse(raw: String): PairingPayloadResult {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return PairingPayloadResult.Malformed("empty payload")
        return when {
            trimmed.startsWith("{") -> parseJson(trimmed)
            trimmed.startsWith(SCHEME_PREFIX, ignoreCase = true) -> parseUri(trimmed)
            else -> PairingPayloadResult.Malformed("not a Luno pairing code")
        }
    }

    private fun parseUri(raw: String): PairingPayloadResult {
        val query = raw.substringAfter('?', missingDelimiterValue = "")
        if (query.isEmpty()) return PairingPayloadResult.Malformed("pairing link has no parameters")

        val fields = mutableMapOf<String, String>()
        for (pair in query.split('&')) {
            if (pair.isEmpty()) continue
            val key = pair.substringBefore('=')
            val value = pair.substringAfter('=', missingDelimiterValue = "")
            val decoded =
                try {
                    URLDecoder.decode(value, Charsets.UTF_8.name())
                } catch (e: UnsupportedEncodingException) {
                    return PairingPayloadResult.Malformed("undecodable parameter '$key'")
                } catch (e: IllegalArgumentException) {
                    return PairingPayloadResult.Malformed("undecodable parameter '$key'")
                }
            fields[key.lowercase()] = decoded
        }

        val version = fields["v"]?.toIntOrNull() ?: return PairingPayloadResult.Malformed("missing version")
        return build(version, fields["u"], fields["c"], fields["s"], fields["l"], fields["p"])
    }

    private fun parseJson(raw: String): PairingPayloadResult {
        val obj =
            try {
                json.parseToJsonElement(raw) as? JsonObject
            } catch (e: Exception) {
                null
            } ?: return PairingPayloadResult.Malformed("not valid pairing JSON")

        fun str(key: String): String? = (obj[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

        val version =
            obj["v"]?.jsonPrimitive?.intOrNull
                ?: return PairingPayloadResult.Malformed("missing version")
        return build(version, str("u"), str("c"), str("s"), str("l"), str("p"))
    }

    private fun build(
        version: Int,
        url: String?,
        code: String?,
        sessionId: String?,
        label: String?,
        pin: String?,
    ): PairingPayloadResult {
        if (version != VERSION) return PairingPayloadResult.UnsupportedVersion(version)

        val backendUrl = url?.trim()?.trimEnd('/').orEmpty()
        if (backendUrl.isEmpty()) return PairingPayloadResult.Malformed("missing backend URL")
        if (!backendUrl.startsWith("https://", ignoreCase = true) &&
            !backendUrl.startsWith("http://", ignoreCase = true)
        ) {
            return PairingPayloadResult.Malformed("backend URL must be http(s)")
        }

        val pairingCode = code?.trim().orEmpty()
        if (pairingCode.isEmpty()) return PairingPayloadResult.Malformed("missing pairing code")

        return PairingPayloadResult.Ok(
            PairingPayload(
                backendUrl = backendUrl,
                pairingCode = pairingCode,
                sessionId = sessionId?.trim()?.takeIf { it.isNotEmpty() },
                label = label?.trim()?.takeIf { it.isNotEmpty() },
                pin = pin?.trim()?.takeIf { it.isNotEmpty() },
            ),
        )
    }
}
