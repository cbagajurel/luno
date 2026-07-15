package com.luno.gateway.model

/**
 * A message that arrived at the SIM. [id] is a dedupe key derived from
 * sender + timestamp + reference (see [com.luno.gateway.util.Ids]).
 */
data class InboundMessage(
    val id: String,
    val sender: String,
    val body: String,
    val subscriptionId: Int? = null,
    val receivedAt: Long,
    val parts: Int = 1,
)
