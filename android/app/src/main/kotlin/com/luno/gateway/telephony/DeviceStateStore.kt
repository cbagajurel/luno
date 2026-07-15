package com.luno.gateway.telephony

import com.luno.gateway.model.BatteryStatus
import com.luno.gateway.model.DeviceState
import com.luno.gateway.model.SignalInfo
import com.luno.gateway.model.SimInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Coalesced telemetry state; each device manager writes one slice. StateFlow
 * equality means an update that yields an equal [DeviceState] notifies no one,
 * which is what debounces noisy sources (e.g. battery) without a timer.
 */
class DeviceStateStore {
    private val _state = MutableStateFlow(DeviceState())
    val state: StateFlow<DeviceState> = _state.asStateFlow()

    val current: DeviceState get() = _state.value

    @Synchronized
    fun updateSims(sims: List<SimInfo>) {
        _state.value = _state.value.copy(sims = sims)
    }

    @Synchronized
    fun updateBattery(battery: BatteryStatus?) {
        _state.value = _state.value.copy(battery = battery)
    }

    @Synchronized
    fun updateSignal(signal: SignalInfo) {
        _state.value = _state.value.copy(
            signals = _state.value.signals + (signal.subscriptionId to signal),
        )
    }

    @Synchronized
    fun retainSignals(subscriptionIds: Set<Int>) {
        val kept = _state.value.signals.filterKeys { it in subscriptionIds }
        if (kept.size != _state.value.signals.size) {
            _state.value = _state.value.copy(signals = kept)
        }
    }
}
