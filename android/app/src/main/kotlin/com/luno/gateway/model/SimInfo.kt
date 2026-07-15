package com.luno.gateway.model

/**
 * Transport-neutral description of one active SIM subscription. Domain model
 * (not a Pigeon DTO) so the agent — queue, backend protocol codec, telemetry —
 * depends on this, never on the Flutter bridge (docs/folder-structure.md).
 * The bridge maps it to a Pigeon type at the boundary (BridgeMappers.kt).
 *
 * The MSISDN (phone number) is deliberately omitted: it is frequently
 * unavailable and would pull in the extra READ_PHONE_NUMBERS permission for
 * little gain (see M4 in docs/milestones.md).
 */
data class SimInfo(
    /** Stable subscription id; the handle used for per-SIM send/telephony later. */
    val subscriptionId: Int,
    /** Physical slot index (0-based), or -1 if unknown (e.g. some eSIMs). */
    val slotIndex: Int,
    /** Carrier/operator name, best-effort. */
    val carrierName: String,
    /** User-facing subscription label (often editable in Settings). */
    val displayName: String,
    /** True for an embedded (eSIM) profile rather than a physical card. */
    val isEmbedded: Boolean,
    /** SIM state name (e.g. READY, ABSENT, PIN_REQUIRED, UNKNOWN). */
    val simState: String,
)
