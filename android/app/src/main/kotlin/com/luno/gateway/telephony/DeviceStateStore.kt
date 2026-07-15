package com.luno.gateway.telephony

import com.luno.gateway.model.BatteryStatus
import com.luno.gateway.model.DeviceState
import com.luno.gateway.model.SimInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The single coalesced telemetry state (plan.md Phase 2). Each read-only device
 * manager owns one slice and writes it here; the [state] flow emits the merged
 * [DeviceState] whenever any slice changes. M4 fills [DeviceState.sims], M5
 * [DeviceState.battery]; M6/M7 add signal/network the same way.
 *
 * Change detection is StateFlow structural equality, so an update that produces
 * an equal [DeviceState] notifies no one — this is what debounces noisy sources
 * (e.g. battery broadcasts) without any timer.
 *
 * Updates are `@Synchronized` because the read-modify-write `copy` must be
 * atomic even though, in practice, all current writers post on the main thread.
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
