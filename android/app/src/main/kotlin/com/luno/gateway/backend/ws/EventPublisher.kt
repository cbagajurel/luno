package com.luno.gateway.backend.ws

import com.luno.gateway.agent.ConnectionState
import com.luno.gateway.backend.protocol.Event
import com.luno.gateway.backend.protocol.ProtocolCodec
import com.luno.gateway.data.db.dao.EventOutboxDao
import com.luno.gateway.data.db.entity.EventOutboxEntity
import com.luno.gateway.logging.LunoLogger
import com.luno.gateway.util.Clock
import com.luno.gateway.util.Ids
import com.luno.gateway.util.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/**
 * The node→backend event path with at-least-once, *durable* delivery (§7.1, §7.4).
 * A [reliable] event is persisted to the [EventOutboxDao] under a caller-supplied
 * *stable* idempotency id, sent when the link is READY, resent on every reconnect,
 * and deleted only when the backend acks it — so a socket dropped mid-exchange, a
 * force-stop, or a reboot never loses or duplicates an event (the backend dedupes
 * on the id). Because the buffer lives in Room, unacked events survive process
 * death and resend on the next READY.
 *
 * On ack, [onEventAcked] runs the one durable follow-up we have — marking an inbox
 * row ACKED for `sms_received` — keyed off the persisted `correlationId` rather
 * than an in-memory closure (a closure can't survive the restart the durability is
 * for).
 *
 * [ephemeral] events (heartbeat, status snapshots) skip the store — dropping one
 * is harmless because the next supersedes it.
 */
class EventPublisher(
    private val sink: EventSink,
    private val scope: CoroutineScope,
    private val logger: LunoLogger,
    private val dao: EventOutboxDao,
    private val codec: ProtocolCodec = ProtocolCodec(),
    private val clock: Clock = SystemClock,
    private val onEventAcked: suspend (type: String, correlationId: String?) -> Unit = { _, _ -> },
    // PII-at-rest boundary: the serialized payload (an sms_received carries a body/number)
    // is sealed before it's stored. Identity by default (tests); AgentGraph injects CryptoBox.
    private val seal: (String) -> String = { it },
    private val open: (String) -> String = { it },
) {
    private var resendJob: Job? = null

    /** Resend everything unacked whenever the link (re)reaches READY. */
    fun start(state: Flow<ConnectionState>) {
        if (resendJob != null) return
        resendJob = scope.launch {
            state.collect { if (it == ConnectionState.READY) resendUnacked() }
        }
    }

    suspend fun reliable(event: Event, id: String, correlationId: String? = null) {
        dao.insertOrReplace(
            EventOutboxEntity(
                id = id,
                type = event.type,
                payload = seal(codec.encodeEventPayload(event)),
                correlationId = correlationId,
                createdAt = clock.nowMillis(),
            ),
        )
        if (sink.isReady) sink.sendEvent(event, id)
    }

    suspend fun ephemeral(event: Event) {
        if (sink.isReady) sink.sendEvent(event, Ids.newId())
    }

    /** A backend ack for one of our event frames: run its follow-up and stop resending. */
    suspend fun onBackendAck(ackedId: String) {
        val row = dao.findById(ackedId) ?: return
        dao.deleteById(ackedId)
        onEventAcked(row.type, row.correlationId)
        logger.i(TAG, "event ${row.type} ($ackedId) acked")
    }

    suspend fun outstandingCount(): Int = dao.count()

    private suspend fun resendUnacked() {
        val rows = dao.getAllOrdered()
        if (rows.isEmpty()) return
        logger.i(TAG, "resending ${rows.size} unacked event(s) after READY")
        for (row in rows) {
            val event = codec.decodeEventPayload(row.type, open(row.payload))
            if (event == null) {
                logger.w(TAG, "dropping unrecoverable event ${row.type} (${row.id})")
                dao.deleteById(row.id)
                continue
            }
            sink.sendEvent(event, row.id)
        }
    }

    companion object {
        private const val TAG = "EventPublisher"
    }
}
