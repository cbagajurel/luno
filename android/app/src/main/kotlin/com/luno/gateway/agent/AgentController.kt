package com.luno.gateway.agent

import com.luno.gateway.logging.LunoLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Coarse lifecycle state of the agent process, streamed to the UI. */
enum class AgentState { STOPPED, RUNNING }

/**
 * The agent "brain". In later milestones it coordinates transports, the backend
 * connection, and the durable queue (docs/architecture.md). For M3 it owns only
 * one thing: the authoritative [state] of whether the gateway is running.
 *
 * This is a process singleton held by [com.luno.gateway.di.AgentGraph], so it
 * outlives any Flutter Activity. The UI never stores this — it reads it through
 * the bridge and re-reads on reattach (snapshot-then-stream). The foreground
 * service is what drives the transitions via [onServiceStarted]/[onServiceStopped].
 */
class AgentController(private val logger: LunoLogger) {
    private val _state = MutableStateFlow(AgentState.STOPPED)
    val state: StateFlow<AgentState> = _state.asStateFlow()

    val isRunning: Boolean get() = _state.value == AgentState.RUNNING

    /** Called by the service once it is genuinely in the foreground. */
    fun onServiceStarted() {
        if (_state.value != AgentState.RUNNING) {
            logger.i(TAG, "agent entered RUNNING (foreground service up)")
            _state.value = AgentState.RUNNING
        }
    }

    /** Called when the service stops or is destroyed. Idempotent. */
    fun onServiceStopped() {
        if (_state.value != AgentState.STOPPED) {
            logger.i(TAG, "agent entered STOPPED (foreground service down)")
            _state.value = AgentState.STOPPED
        }
    }

    companion object {
        private const val TAG = "AgentController"
    }
}
