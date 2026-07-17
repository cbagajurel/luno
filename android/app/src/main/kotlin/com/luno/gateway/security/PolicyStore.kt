package com.luno.gateway.security

import com.luno.gateway.backend.auth.KeyValueStore
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Backend-pushed send policy (§8.2 `config_update`), persisted so it survives restart. */
@Serializable
data class SendPolicy(
    val rateLimitPerMinute: Int = 0,
    val allowlist: List<String> = emptyList(),
)

/**
 * Durable home for the send policy. The backend is authoritative (it pushes the
 * policy via `config_update`), but the node enforces it locally and across restarts,
 * so the safety guarantees hold even when offline or against an untrusted backend.
 */
class PolicyStore(
    private val store: KeyValueStore,
    private val json: Json = Json,
) {
    fun load(): SendPolicy =
        store.getString(KEY)?.let {
            try {
                json.decodeFromString(SendPolicy.serializer(), it)
            } catch (e: Exception) {
                SendPolicy()
            }
        } ?: SendPolicy()

    /** Merge in whichever fields the `config_update` actually set; returns the new policy. */
    fun update(rateLimitPerMinute: Int?, allowlist: List<String>?): SendPolicy {
        val current = load()
        val next = current.copy(
            rateLimitPerMinute = rateLimitPerMinute ?: current.rateLimitPerMinute,
            allowlist = allowlist ?: current.allowlist,
        )
        store.putString(KEY, json.encodeToString(SendPolicy.serializer(), next))
        return next
    }

    fun clear() = store.remove(KEY)

    companion object {
        private const val KEY = "send_policy"
    }
}
