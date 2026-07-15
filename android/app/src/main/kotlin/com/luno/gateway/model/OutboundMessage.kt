package com.luno.gateway.model

/**
 * A message the node has been asked to send, independent of any transport. The
 * backend's command id ([commandId]) is the idempotency key; [id] is our own
 * durable message id. [ref] is the caller's correlation token echoed on events.
 */
data class OutboundMessage(
    val id: String,
    val recipient: String,
    val body: String,
    val subscriptionId: Int? = null,
    val requestDeliveryReport: Boolean = true,
    val commandId: String? = null,
    val ref: String? = null,
)
