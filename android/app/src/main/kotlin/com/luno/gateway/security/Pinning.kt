package com.luno.gateway.security

import okhttp3.CertificatePinner

/**
 * Builds an OkHttp [CertificatePinner] from backend-supplied SHA-256 pins, or null
 * when none are configured (WSS still requires a valid CA chain regardless). Pins are
 * expected in OkHttp's `sha256/BASE64` form and should include a backup pin for cert
 * rotation. This is the seam (§ threat model: "Cleartext transport / MITM"); pins are
 * delivered via config later, so pinning can be turned on without touching the socket.
 */
object Pinning {
    fun buildPinner(host: String, pins: List<String>): CertificatePinner? {
        if (host.isBlank() || pins.isEmpty()) return null
        return CertificatePinner.Builder()
            .apply { pins.forEach { add(host, it) } }
            .build()
    }
}
