package com.luno.gateway.agent

import com.luno.gateway.logging.LunoLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AgentState { STOPPED, RUNNING }

class AgentController(private val logger: LunoLogger) {
    private val _state = MutableStateFlow(AgentState.STOPPED)
    val state: StateFlow<AgentState> = _state.asStateFlow()

    val isRunning: Boolean get() = _state.value == AgentState.RUNNING

    fun onServiceStarted() {
        if (_state.value != AgentState.RUNNING) {
            logger.i(TAG, "agent entered RUNNING (foreground service up)")
            _state.value = AgentState.RUNNING
        }
    }

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
