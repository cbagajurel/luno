package com.luno.gateway.util

import java.util.UUID

object Ids {
    fun newId(): String = UUID.randomUUID().toString()

    /** Dedupe key for inbound SMS: identical sender + timestamp + reference is the same message. */
    fun inboundKey(sender: String, timestampMillis: Long, reference: String? = null): String =
        "$sender|$timestampMillis|${reference ?: ""}"
}
