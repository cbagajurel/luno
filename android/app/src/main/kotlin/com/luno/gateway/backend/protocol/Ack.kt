package com.luno.gateway.backend.protocol

import kotlinx.serialization.Serializable

/** Confirms receipt of a specific frame, both directions (§8.4). */
@Serializable
data class Ack(
    val ackedId: String,
) {
    val type: String get() = TYPE

    companion object {
        const val TYPE = "ack"
    }
}
