package com.luno.gateway.model

enum class InboxStatus {
    RECEIVED,
    REASSEMBLED,
    REASSEMBLY_TIMEOUT,
    REPORTED,
    ACKED;

    val isTerminal: Boolean
        get() = this == ACKED
}
