package com.luno.gateway.backend.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
enum class FrameKind {
    @SerialName("command")
    COMMAND,

    @SerialName("event")
    EVENT,

    @SerialName("ack")
    ACK,

    @SerialName("control")
    CONTROL,
}

/**
 * The one envelope every frame shares (§8.1). [payload] is carried raw so the
 * envelope itself is transport- and type-neutral; [ProtocolCodec] is the only
 * place that knows how to (de)serialize a payload for a given [kind]+[type].
 */
@Serializable
data class Envelope(
    val v: Int,
    val kind: FrameKind,
    val id: String,
    val ts: String,
    val deviceId: String,
    val type: String,
    val seq: Long,
    val payload: JsonObject,
)

object ProtocolVersion {
    const val CURRENT = 1
    val SUPPORTED = setOf(1)

    /** Highest version this node and the peer both speak, or null if none (§8.5). */
    fun negotiate(peerSupported: Set<Int>): Int? = SUPPORTED.intersect(peerSupported).maxOrNull()
}
