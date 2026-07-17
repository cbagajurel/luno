package com.luno.gateway.backend.ws

import com.luno.gateway.agent.signalDtos
import com.luno.gateway.backend.protocol.Event
import com.luno.gateway.logging.LunoLogger
import com.luno.gateway.model.DeviceState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * The application-layer heartbeat (§7.2): every [intervalSeconds] it sends a cheap
 * `heartbeat{queueDepth, battery, signals, transports}` so the backend can mark the
 * node offline after N misses and the dashboard stays live without full status
 * spam. Heartbeats are [EventPublisher.ephemeral] — dropping one is fine, the next
 * supersedes it — and are only emitted while the link is READY.
 */
class Heartbeat(
    private val events: EventPublisher,
    private val deviceState: () -> DeviceState,
    private val queueDepth: () -> Int,
    private val transports: () -> List<String>,
    private val scope: CoroutineScope,
    private val logger: LunoLogger,
    initialIntervalSeconds: Int = DEFAULT_INTERVAL_SECONDS,
) {
    @Volatile private var intervalMillis: Long = initialIntervalSeconds * 1000L
    private var job: Job? = null

    fun start() {
        if (job != null) return
        job = scope.launch {
            while (isActive) {
                delay(intervalMillis)
                emit()
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    fun setIntervalSeconds(seconds: Int) {
        if (seconds <= 0) return
        intervalMillis = seconds * 1000L
        logger.i(TAG, "heartbeat interval set to ${seconds}s")
    }

    private suspend fun emit() {
        val state = deviceState()
        events.ephemeral(
            Event.Heartbeat(
                queueDepth = queueDepth(),
                battery = state.battery?.levelPercent,
                signals = state.signalDtos(),
                transports = transports(),
            ),
        )
    }

    companion object {
        private const val TAG = "Heartbeat"
        private const val DEFAULT_INTERVAL_SECONDS = 30
    }
}
