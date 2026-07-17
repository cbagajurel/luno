package com.luno.gateway.agent

import com.luno.gateway.backend.protocol.Event
import com.luno.gateway.backend.protocol.FrameBody
import com.luno.gateway.backend.protocol.ProtocolFrame
import com.luno.gateway.backend.ws.EventPublisher
import com.luno.gateway.backend.ws.Heartbeat
import com.luno.gateway.data.db.entity.InboxEntity
import com.luno.gateway.data.repository.InboxRepository
import com.luno.gateway.data.repository.OutboxDispatcher
import com.luno.gateway.logging.LunoLogger
import com.luno.gateway.model.DeliveryReport
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class AgentState { STOPPED, RUNNING }

/**
 * The agent's orchestration hub (§2): it connects the live backend link (M12–M13)
 * to the durable SMS layer (M8–M11). Backend commands become durable outbox work
 * and are acked; outbox/inbox/delivery transitions become node→backend events with
 * at-least-once delivery; a heartbeat keeps the backend's view of the node live.
 *
 * Orchestration is process-scoped and started once (guarded): the collectors and
 * heartbeat sit idle whenever the [connectionState] isn't READY and resume when it
 * is, so the service can stop/restart the connection underneath without duplicating
 * or leaking wiring. [AgentState] (STOPPED/RUNNING) remains the coarse signal the
 * Flutter UI reads.
 */
class AgentController(
    private val logger: LunoLogger,
    private val scope: CoroutineScope? = null,
    private val incoming: Flow<ProtocolFrame>? = null,
    private val connectionState: StateFlow<ConnectionState>? = null,
    private val events: EventPublisher? = null,
    private val router: CommandRouter? = null,
    private val heartbeat: Heartbeat? = null,
    private val dispatcher: OutboxDispatcher? = null,
    private val inbox: InboxRepository? = null,
    private val deliveryReports: Flow<DeliveryReport>? = null,
) {
    private val _state = MutableStateFlow(AgentState.STOPPED)
    val state: StateFlow<AgentState> = _state.asStateFlow()

    val isRunning: Boolean get() = _state.value == AgentState.RUNNING

    private val lastCommandSeq = AtomicLong(0)

    /** Highest backend command seq processed — the inbound resync cursor (§7.4). */
    fun lastAckedInboundSeq(): Long = lastCommandSeq.get()

    private var orchestrationStarted = false
    private val reportedInbound = HashSet<String>()

    fun onServiceStarted() {
        if (_state.value != AgentState.RUNNING) {
            logger.i(TAG, "agent entered RUNNING (foreground service up)")
            _state.value = AgentState.RUNNING
        }
        startOrchestration()
    }

    fun onServiceStopped() {
        if (_state.value != AgentState.STOPPED) {
            logger.i(TAG, "agent entered STOPPED (foreground service down)")
            _state.value = AgentState.STOPPED
        }
    }

    private fun startOrchestration() {
        val scope = scope ?: return
        if (orchestrationStarted) return
        orchestrationStarted = true

        events?.start(requireNotNull(connectionState))
        heartbeat?.start()

        incoming?.let { flow -> scope.launch { flow.collect { onFrame(it) } } }
        connectionState?.let { flow ->
            scope.launch { flow.collect { if (it == ConnectionState.READY) onReady() } }
        }
        deliveryReports?.let { flow -> scope.launch { flow.collect { onDeliveryReport(it) } } }
        inbox?.let { repo -> scope.launch { repo.observePending().collect { reportPending(it) } } }
        logger.i(TAG, "agent orchestration started")
    }

    private suspend fun onFrame(frame: ProtocolFrame) {
        when (val body = frame.body) {
            is FrameBody.CommandBody -> router?.handle(frame)?.let { advanceCommandCursor(it) }
            is FrameBody.AckBody -> events?.onBackendAck(body.ack.ackedId)
            else -> logger.w(TAG, "ignoring unexpected ${body.kind}/${body.type} from backend")
        }
    }

    private fun advanceCommandCursor(seq: Long) {
        var current = lastCommandSeq.get()
        while (seq > current && !lastCommandSeq.compareAndSet(current, seq)) {
            current = lastCommandSeq.get()
        }
    }

    private suspend fun onReady() {
        // Flush anything that queued while offline; buffered events resend via EventPublisher.
        dispatcher?.drainQueued()
    }

    private suspend fun onDeliveryReport(report: DeliveryReport) {
        events?.reliable(
            Event.DeliveryReport(
                messageId = report.messageId,
                part = report.partIndex,
                status = if (report.delivered) "delivered" else "undelivered",
                at = report.at,
            ),
            EventKeys.delivery(report.messageId, report.partIndex),
        )
    }

    private suspend fun reportPending(rows: List<InboxEntity>) {
        val inbox = inbox ?: return
        val events = events ?: return
        for (row in rows) {
            if (!reportedInbound.add(row.id)) continue
            events.reliable(
                Event.SmsReceived(
                    from = row.sender,
                    body = row.body,
                    subscriptionId = row.subscriptionId,
                    receivedAt = row.receivedAt,
                    parts = row.parts,
                ),
                EventKeys.received(row.id),
                correlationId = row.id,
            )
            inbox.markReported(row.id)
        }
    }

    companion object {
        private const val TAG = "AgentController"
    }
}
