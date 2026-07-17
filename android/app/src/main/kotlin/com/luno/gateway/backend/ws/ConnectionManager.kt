package com.luno.gateway.backend.ws

import com.luno.gateway.agent.ConnectionEvent
import com.luno.gateway.agent.ConnectionState
import com.luno.gateway.agent.ConnectionStateMachine
import com.luno.gateway.backend.auth.DeviceCredential
import com.luno.gateway.backend.protocol.Ack
import com.luno.gateway.backend.protocol.Control
import com.luno.gateway.backend.protocol.DecodeResult
import com.luno.gateway.backend.protocol.Event
import com.luno.gateway.backend.protocol.FrameBody
import com.luno.gateway.backend.protocol.ProtocolCodec
import com.luno.gateway.backend.protocol.ProtocolFrame
import com.luno.gateway.backend.protocol.ProtocolVersion
import com.luno.gateway.logging.LunoLogger
import com.luno.gateway.util.Clock
import com.luno.gateway.util.Ids
import java.time.Instant
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Drives the §6 connection lifecycle: it feeds socket events, network changes,
 * and backoff timers into [ConnectionStateMachine] and performs the matching
 * side effects (connect, handshake, reconnect). All state mutation happens on a
 * single consumer coroutine, so socket-callback threads, the network collector,
 * and backoff timers never race. Each socket gets a [generation] tag so a late
 * callback from a torn-down socket can't perturb the current one.
 *
 * Reaching READY is a three-step handshake once the WSS upgrade succeeds (the
 * credential having already been accepted at the header): version_negotiate →
 * AUTHENTICATED, then resync → READY. The resync *contents* (redelivery cursors)
 * are placeholders here; wiring them to the real queues is M14/M15.
 */
class ConnectionManager(
    private val socket: Socket,
    private val codec: ProtocolCodec,
    private val reconnectPolicy: ReconnectPolicy,
    private val scope: CoroutineScope,
    private val clock: Clock,
    private val online: StateFlow<Boolean>,
    private val credentialProvider: () -> DeviceCredential?,
    private val logger: LunoLogger,
    private val lastAckedInboundSeq: () -> Long = { 0L },
    private val outstandingOutboxIds: () -> List<String> = { emptyList() },
) : EventSink {
    private val _state = MutableStateFlow(ConnectionState.DISCONNECTED)
    val state: StateFlow<ConnectionState> = _state.asStateFlow()

    private val _incoming = MutableSharedFlow<ProtocolFrame>(extraBufferCapacity = 64)
    val incoming: Flow<ProtocolFrame> = _incoming.asSharedFlow()

    private val events = Channel<Internal>(Channel.UNLIMITED)
    private var loopJob: Job? = null
    private var netJob: Job? = null
    private var backoffJob: Job? = null

    private var generation = 0
    private val seq = AtomicLong(0)

    override val isReady: Boolean get() = _state.value == ConnectionState.READY

    private sealed interface Internal {
        data class Net(val online: Boolean) : Internal

        data class Sock(val generation: Int, val event: SocketEvent) : Internal

        data object BackoffFired : Internal

        data object Kick : Internal

        data object Disconnect : Internal

        data object Stop : Internal
    }

    fun start() {
        if (loopJob != null) return
        loopJob = scope.launch { loop() }
        netJob = scope.launch { online.collect { events.trySend(Internal.Net(it)) } }
    }

    fun stop() {
        events.trySend(Internal.Stop)
    }

    /** Attempt a connect now (e.g. right after pairing) without restarting the loop. */
    fun kick() {
        events.trySend(Internal.Kick)
    }

    /** Tear down the current connection but keep running (e.g. after unpair). */
    fun disconnect() {
        events.trySend(Internal.Disconnect)
    }

    /**
     * Send a node→backend event. [id] is the stable idempotency key (reused across
     * resends so the backend dedupes); returns false if there's no credential yet.
     * Only meaningful once READY — callers gate on [isReady]/[state].
     */
    override fun sendEvent(event: Event, id: String): Boolean =
        sendFrame(id, FrameBody.EventBody(event))

    /** Acknowledge receipt of a specific backend frame (§8.4). Fire-and-forget. */
    override fun sendAck(ackedId: String): Boolean =
        sendFrame(Ids.newId(), FrameBody.AckBody(Ack(ackedId)))

    private suspend fun loop() {
        for (event in events) {
            when (event) {
                is Internal.Net -> onNet(event.online)
                is Internal.Sock -> if (event.generation == generation) onSocket(event.event)
                Internal.BackoffFired -> onBackoffFired()
                Internal.Kick -> startConnecting()
                Internal.Disconnect -> {
                    closeSocket()
                    backoffJob?.cancel()
                    reconnectPolicy.reset()
                    if (online.value) _state.value = ConnectionState.DISCONNECTED
                    else _state.value = ConnectionState.OFFLINE_NO_NETWORK
                }
                Internal.Stop -> {
                    cleanup()
                    return
                }
            }
        }
    }

    private fun onNet(isOnline: Boolean) {
        if (!isOnline) {
            closeSocket()
            backoffJob?.cancel()
            reconnectPolicy.onConnectionEnded(clock.nowMillis())
            apply(ConnectionEvent.NETWORK_LOST)
            return
        }
        when (_state.value) {
            ConnectionState.OFFLINE_NO_NETWORK -> {
                apply(ConnectionEvent.NETWORK_AVAILABLE)
                startConnecting()
            }
            ConnectionState.DISCONNECTED -> startConnecting()
            else -> Unit
        }
    }

    private fun onSocket(event: SocketEvent) {
        when (event) {
            SocketEvent.Open -> onOpen()
            is SocketEvent.Message -> onMessage(event.text)
            is SocketEvent.Closed -> onDrop()
            is SocketEvent.Failure -> onFailure(event.httpCode)
        }
    }

    private fun onOpen() {
        if (apply(ConnectionEvent.SOCKET_OPENED)) {
            sendControl(Control.VersionNegotiate(ProtocolVersion.SUPPORTED.toList()))
        }
    }

    private fun onMessage(text: String) {
        val frame =
            when (val decoded = codec.decode(text)) {
                is DecodeResult.Ok -> decoded.frame
                is DecodeResult.Unsupported -> {
                    logger.w(TAG, "dropping unsupported frame: ${decoded.reason}")
                    return
                }
                is DecodeResult.Malformed -> {
                    logger.w(TAG, "dropping malformed frame: ${decoded.reason}")
                    return
                }
            }
        handleFrame(frame)
    }

    private fun handleFrame(frame: ProtocolFrame) {
        val body = frame.body
        when (_state.value) {
            ConnectionState.CONNECTED ->
                if (body is FrameBody.ControlBody && body.control is Control.VersionNegotiate) {
                    if (apply(ConnectionEvent.AUTH_OK)) {
                        sendControl(
                            Control.Resync(
                                lastAckedInboundSeq = lastAckedInboundSeq(),
                                outstandingOutboxIds = outstandingOutboxIds(),
                            ),
                        )
                    }
                }

            ConnectionState.AUTHENTICATED ->
                if (body is FrameBody.AckBody || (body is FrameBody.ControlBody && body.control is Control.Resync)) {
                    if (apply(ConnectionEvent.READY_UP)) {
                        reconnectPolicy.onReady(clock.nowMillis())
                        logger.i(TAG, "connection READY")
                    }
                }

            else -> Unit
        }

        if (body is FrameBody.ControlBody && body.control is Control.Ping) sendControl(Control.Pong)
        // Commands/events are forwarded to the agent; backend acks are forwarded only
        // once READY, so the AUTHENTICATED-state handshake ack isn't mistaken for an
        // application ack (the EventPublisher ignores unknown ackedIds either way).
        when {
            body is FrameBody.CommandBody || body is FrameBody.EventBody -> _incoming.tryEmit(frame)
            body is FrameBody.AckBody && _state.value == ConnectionState.READY -> _incoming.tryEmit(frame)
        }
    }

    private fun onFailure(httpCode: Int?) {
        if (httpCode == HTTP_UNAUTHORIZED || httpCode == HTTP_FORBIDDEN) {
            // Credential rejected: pause, don't reconnect-loop (§10 AUTH). The SM has
            // no CONNECTING→DISCONNECTED(auth) edge, so the pause is enforced here.
            closeSocket()
            backoffJob?.cancel()
            _state.value = ConnectionState.DISCONNECTED
            logger.w(TAG, "credential rejected ($httpCode); re-enroll required")
            return
        }
        onDrop()
    }

    private fun onDrop() {
        val event =
            if (_state.value == ConnectionState.CONNECTING) {
                ConnectionEvent.CONNECT_FAILED
            } else {
                ConnectionEvent.SOCKET_DROPPED
            }
        closeSocket()
        reconnectPolicy.onConnectionEnded(clock.nowMillis())
        if (apply(event)) scheduleBackoff()
    }

    private fun scheduleBackoff() {
        if (!apply(ConnectionEvent.BACKOFF_SCHEDULED)) return
        val delayMillis = reconnectPolicy.nextDelayMillis()
        logger.i(TAG, "reconnecting in ${delayMillis}ms (attempt ${reconnectPolicy.attempt})")
        backoffJob?.cancel()
        backoffJob =
            scope.launch {
                delay(delayMillis)
                events.trySend(Internal.BackoffFired)
            }
    }

    private fun onBackoffFired() {
        if (_state.value != ConnectionState.BACKING_OFF) return
        if (apply(ConnectionEvent.BACKOFF_ELAPSED)) connectSocketOrStand()
    }

    private fun startConnecting() {
        if (_state.value != ConnectionState.DISCONNECTED) return
        if (!online.value) return
        if (credentialProvider() == null) return
        if (apply(ConnectionEvent.CONNECT)) connectSocketOrStand()
    }

    private fun connectSocketOrStand() {
        val credential = credentialProvider()
        if (credential == null || !online.value) {
            _state.value = ConnectionState.DISCONNECTED
            return
        }
        val gen = generation
        socket.connect(credential.wsUrl, credential.credential) { event ->
            events.trySend(Internal.Sock(gen, event))
        }
    }

    private fun closeSocket() {
        generation++
        socket.close()
    }

    private fun cleanup() {
        closeSocket()
        backoffJob?.cancel()
        netJob?.cancel()
        loopJob = null
        _state.value = ConnectionState.DISCONNECTED
    }

    private fun sendControl(control: Control) {
        sendFrame(Ids.newId(), FrameBody.ControlBody(control))
    }

    /** Stamps [body] with a fresh envelope (device id, monotonic seq, ts) and sends it. */
    private fun sendFrame(id: String, body: FrameBody): Boolean {
        val credential = credentialProvider() ?: return false
        val frame =
            ProtocolFrame(
                v = ProtocolVersion.CURRENT,
                id = id,
                ts = Instant.ofEpochMilli(clock.nowMillis()).toString(),
                deviceId = credential.deviceId,
                seq = seq.incrementAndGet(),
                body = body,
            )
        return socket.send(codec.encode(frame))
    }

    private fun apply(event: ConnectionEvent): Boolean {
        val next = ConnectionStateMachine.transition(_state.value, event) ?: return false
        if (next != _state.value) {
            logger.i(TAG, "connection ${_state.value} -> $next ($event)")
            _state.value = next
        }
        return true
    }

    companion object {
        private const val TAG = "ConnectionManager"
        private const val HTTP_UNAUTHORIZED = 401
        private const val HTTP_FORBIDDEN = 403
    }
}
