package com.luno.gateway.model

/**
 * The immediate outcome of handing a message to a transport. Delivery reports
 * (SENT → DELIVERED) arrive later and are not part of the handle.
 */
sealed interface SendHandle {
    val messageId: String

    data class Sent(override val messageId: String, val transportRef: String) : SendHandle

    data class Failed(override val messageId: String, val error: DomainError) : SendHandle
}
