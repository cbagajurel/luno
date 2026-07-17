package com.luno.gateway.backend.auth

import kotlinx.serialization.Serializable

/**
 * What a paired node needs to reconnect: the enrollment base ([backendUrl]), the
 * resolved [wsUrl] to connect to, who this node is ([deviceId]), and the bearer
 * [credential] it presents on the WSS handshake (§3). Stored only encrypted,
 * never logged.
 */
@Serializable
data class DeviceCredential(
    val backendUrl: String,
    val wsUrl: String,
    val deviceId: String,
    val credential: String,
)
