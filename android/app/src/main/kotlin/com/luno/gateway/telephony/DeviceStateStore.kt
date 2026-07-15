package com.luno.gateway.telephony

import com.luno.gateway.model.BatteryStatus
import com.luno.gateway.model.DeviceState
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
}
