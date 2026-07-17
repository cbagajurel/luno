package com.luno.gateway.backend.ws

import com.luno.gateway.agent.ConnectionState
import com.luno.gateway.backend.protocol.Event
import com.luno.gateway.logging.LunoLogger
import com.luno.gateway.util.Ids
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The node→backend event path with at-least-once delivery (§7.1). A [reliable]
 * event is buffered under a caller-supplied *stable* idempotency id, sent when the
 * link is READY, resent on every reconnect, and cleared only when the backend acks
 * it — so a socket dropped mid-exchange never loses or duplicates an event (the
 * backend dedupes on the id). The buffer is in-memory: surviving process death is
 * the durable-resync job of M15.
 *
 * [ephemeral] events (heartbeat, status snapshots) skip the buffer — dropping one
 * is harmless because the next supersedes it.
 */
class EventPublisher(
    private val sink: EventSink,
    private val scope: CoroutineScope,
    private val logger: LunoLogger,
) {
    private class Pending(val event: Event, val id: String, val onAck: suspend () -> Unit)

    private val unacked = LinkedHashMap<String, Pending>()
    private val mutex = Mutex()
    private var resendJob: Job? = null

    /** Resend everything unacked whenever the link (re)reaches READY. */
    fun start(state: Flow<ConnectionState>) {
        if (resendJob != null) return
        resendJob = scope.launch {
            state.collect { if (it == ConnectionState.READY) resendUnacked() }
        }
    }

    suspend fun reliable(event: Event, id: String, onAck: suspend () -> Unit = {}) {
        mutex.withLock { unacked[id] = Pending(event, id, onAck) }
        if (sink.isReady) sink.sendEvent(event, id)
    }

    suspend fun ephemeral(event: Event) {
        if (sink.isReady) sink.sendEvent(event, Ids.newId())
    }

    /** A backend ack for one of our event frames: run its follow-up and stop resending. */
    suspend fun onBackendAck(ackedId: String) {
        val pending = mutex.withLock { unacked.remove(ackedId) } ?: return
        pending.onAck()
        logger.i(TAG, "event ${pending.event.type} ($ackedId) acked")
    }

    suspend fun outstandingCount(): Int = mutex.withLock { unacked.size }

    private suspend fun resendUnacked() {
        val pendings = mutex.withLock { unacked.values.toList() }
        if (pendings.isEmpty()) return
        logger.i(TAG, "resending ${pendings.size} unacked event(s) after READY")
        pendings.forEach { sink.sendEvent(it.event, it.id) }
    }

    companion object {
        private const val TAG = "EventPublisher"
    }
}
