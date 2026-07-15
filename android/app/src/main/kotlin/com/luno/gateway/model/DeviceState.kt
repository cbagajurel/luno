package com.luno.gateway.model

/**
 * Coalesced snapshot of device telemetry streamed to the dashboard. M4 fills in
 * [sims]; battery (M5), signal (M6), and network (M7) add fields to this same
 * aggregate so the UI has a single source of truth (docs/milestones.md Phase 2).
 *
 * A data class so structural equality drives change detection — a StateFlow of
 * DeviceState only notifies observers when something actually changed.
 */
data class DeviceState(
    val sims: List<SimInfo> = emptyList(),
    val battery: BatteryStatus? = null,
)
