package com.luno.gateway.backend.protocol

import kotlinx.serialization.Serializable

/**
 * Commands the backend sends to the node (§8.2). Each payload is a wire DTO;
 * [type] is the envelope discriminator and is deliberately not a serialized
 * field of the payload (it lives once, in the [Envelope]).
 */
sealed interface Command {
    val type: String

    @Serializable
    data class SendSms(
        val to: String,
        val body: String,
        val subscriptionId: Int? = null,
        val deliveryReport: Boolean = true,
        val ref: String? = null,
    ) : Command {
        override val type: String get() = TYPE

        companion object {
            const val TYPE = "send_sms"
        }
    }

    @Serializable
    data class CancelSms(
        val commandId: String,
    ) : Command {
        override val type: String get() = TYPE

        companion object {
            const val TYPE = "cancel_sms"
        }
    }

    @Serializable
    data object GetStatus : Command {
        override val type: String get() = "get_status"
    }

    @Serializable
    data class ConfigUpdate(
        val heartbeatSec: Int? = null,
        val rateLimitPerMinute: Int? = null,
        val allowlist: List<String>? = null,
        val credential: String? = null,
    ) : Command {
        override val type: String get() = TYPE

        companion object {
            const val TYPE = "config_update"
        }
    }

    @Serializable
    data object Revoke : Command {
        override val type: String get() = "revoke"
    }

    @Serializable
    data object Wipe : Command {
        override val type: String get() = "wipe"
    }
}
