package com.luno.gateway.backend.protocol

import kotlinx.serialization.Serializable

/**
 * Control frames: the reconnect handshake (§7.4), version negotiation (§8.5),
 * and app-layer keepalive (§8.4).
 */
sealed interface Control {
    val type: String

    @Serializable
    data class Resync(
        val lastAckedInboundSeq: Long,
        val outstandingOutboxIds: List<String> = emptyList(),
    ) : Control {
        override val type: String get() = TYPE

        companion object {
            const val TYPE = "resync"
        }
    }

    @Serializable
    data class VersionNegotiate(
        val supported: List<Int>,
        val selected: Int? = null,
    ) : Control {
        override val type: String get() = TYPE

        companion object {
            const val TYPE = "version_negotiate"
        }
    }

    @Serializable
    data object Ping : Control {
        override val type: String get() = "ping"
    }

    @Serializable
    data object Pong : Control {
        override val type: String get() = "pong"
    }
}
