package com.luno.gateway.backend.auth

/** Normalized pairing failure reasons, aligned with the §10 taxonomy. */
enum class PairingError {
    INVALID_CODE,
    NOT_SECURE,
    NETWORK,
    SERVER,
    INTERNAL,
}

sealed interface PairingResult {
    data class Success(val deviceId: String) : PairingResult

    data class Failure(val error: PairingError, val message: String) : PairingResult
}
