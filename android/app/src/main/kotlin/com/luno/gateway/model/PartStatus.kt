package com.luno.gateway.model

/** Per-part send outcome (the sent-report side of a multipart message). */
enum class PartSentStatus {
    SENDING,
    SENT,
    FAILED_RETRYABLE,
    FAILED_TERMINAL,
}

/** Per-part delivery outcome (the delivery-report side; may never arrive). */
enum class PartDeliveryStatus {
    NONE,
    PENDING,
    DELIVERED,
    UNDELIVERED,
}
