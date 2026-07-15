package com.luno.gateway.model

data class DeviceState(
    val sims: List<SimInfo> = emptyList(),
    val battery: BatteryStatus? = null,
    val signals: Map<Int, SignalInfo> = emptyMap(),
)
