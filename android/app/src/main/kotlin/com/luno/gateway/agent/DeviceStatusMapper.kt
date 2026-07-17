package com.luno.gateway.agent

import com.luno.gateway.backend.protocol.BatteryDto
import com.luno.gateway.backend.protocol.Event
import com.luno.gateway.backend.protocol.NetworkDto
import com.luno.gateway.backend.protocol.SignalDto
import com.luno.gateway.backend.protocol.SimDto
import com.luno.gateway.model.DeviceState

/** Maps the domain [DeviceState] onto the §8.3 `device_status` / `heartbeat` wire DTOs. */
fun DeviceState.toDeviceStatusEvent(): Event.DeviceStatus =
    Event.DeviceStatus(
        battery = battery?.let { BatteryDto(it.levelPercent, it.isCharging, it.plugged, it.health) },
        network = network?.let { NetworkDto(it.connected, it.validated, it.transport, it.metered) },
        sims = sims.map { SimDto(it.subscriptionId, it.slotIndex, it.carrierName, it.displayName, it.isEmbedded, it.simState) },
    )

fun DeviceState.signalDtos(): List<SignalDto> =
    signals.values.map { SignalDto(it.subscriptionId, it.dbm, it.level) }
