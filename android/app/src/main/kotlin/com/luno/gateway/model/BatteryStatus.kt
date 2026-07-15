package com.luno.gateway.model

data class BatteryStatus(
    val levelPercent: Int,
    val isCharging: Boolean,
    val plugged: String,
    val health: String,
)
