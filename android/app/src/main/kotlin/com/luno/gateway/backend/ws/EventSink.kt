package com.luno.gateway.backend.ws

import com.luno.gateway.backend.protocol.Event

/**
 * The narrow slice of the connection [EventPublisher] needs: send a node→backend
 * event stamped with a stable idempotency [id], and know whether the link is
 * ready. Keeping it an interface lets the publisher be unit-tested without a
 * live socket. [ConnectionManager] is the production implementation.
 */
interface EventSink {
    val isReady: Boolean

    fun sendEvent(event: Event, id: String): Boolean

    fun sendAck(ackedId: String): Boolean
}
