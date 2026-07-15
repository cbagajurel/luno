package com.luno.gateway.model

enum class OutboxStatus {
    QUEUED,
    SENDING,
    SENT,
    DELIVERED,
    UNDELIVERED,
    FAILED_RETRYABLE,
    FAILED_TERMINAL,
    CANCELLED;

    val isTerminal: Boolean
        get() = this == DELIVERED ||
            this == UNDELIVERED ||
            this == FAILED_TERMINAL ||
            this == CANCELLED
}
