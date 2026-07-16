package com.luno.gateway.model

/** One part's sent-side result, carried on a successful [SendHandle.Sent]. */
data class SentPart(
    val index: Int,
    val transportRef: String,
    val deliveryTracked: Boolean,
)

/**
 * The immediate outcome of handing a message to a transport. Delivery reports
 * (SENT → DELIVERED) arrive later and are not part of the handle. [parts]
 * describes the multipart breakdown; empty means the transport did not report a
 * breakdown (treat as a single part).
 */
sealed interface SendHandle {
    val messageId: String

    data class Sent(
        override val messageId: String,
        val transportRef: String,
        val parts: List<SentPart> = emptyList(),
    ) : SendHandle

    data class Failed(override val messageId: String, val error: DomainError) : SendHandle
}
