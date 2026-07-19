package com.luno.gateway.backend.auth

import kotlinx.serialization.Serializable

/**
 * What a paired node needs to reconnect: the enrollment base ([backendUrl]), the
 * resolved [wsUrl] to connect to, who this node is ([deviceId]), and the bearer
 * [credential] it presents on the WSS handshake (§3). Stored only encrypted,
 * never logged.
 *
 * [pin] carries an SPKI pin captured during QR pairing. It is persisted here so
 * the pin survives to every later reconnect, but `WebSocketClient` does not read
 * it yet — the pinning seam (`security/Pinning`) is still fed from config.
 */
@Serializable
data class DeviceCredential(
    val backendUrl: String,
    val wsUrl: String,
    val deviceId: String,
    val credential: String,
    val pin: String? = null,
)
