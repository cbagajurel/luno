package com.luno.gateway.agent

/**
 * Stable idempotency keys for node→backend event frames. Because they are derived
 * from durable ids (command id, message id, inbox id) rather than random, the same
 * logical event produces the same frame id on every resend — so the backend dedupes
 * it (§7.4), and a reconnect can never turn one event into two.
 */
object EventKeys {
    fun accepted(commandId: String) = "acc:$commandId"

    fun sent(messageId: String) = "sent:$messageId"

    fun delivery(messageId: String, partIndex: Int) = "dlv:$messageId:$partIndex"

    fun error(messageId: String) = "err:$messageId"

    fun received(inboxId: String) = "rcv:$inboxId"
}
