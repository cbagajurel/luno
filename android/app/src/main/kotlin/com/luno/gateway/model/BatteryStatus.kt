package com.luno.gateway.model

/**
 * Transport-neutral battery snapshot (M5). Feeds the dashboard now and the
 * backend heartbeat later. Temperature is intentionally excluded: it changes
 * constantly, and since [DeviceState] change-detection is equality-based,
 * carrying it would defeat the natural debounce and spam observers.
 */
data class BatteryStatus(
    /** Charge level 0..100, or -1 if unknown. */
    val levelPercent: Int,
    /** True while charging or full. */
    val isCharging: Boolean,
    /** Power source: NONE, AC, USB, WIRELESS, DOCK, or UNKNOWN. */
    val plugged: String,
    /** Battery health: GOOD, OVERHEAT, DEAD, OVER_VOLTAGE, COLD, or UNKNOWN. */
    val health: String,
)
