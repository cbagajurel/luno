package com.luno.gateway.backend.ws

import kotlin.math.pow
import kotlin.random.Random

/**
 * Exponential backoff with full jitter, capped (§7.1). The attempt counter grows
 * on every reconnect attempt and resets **only after a connection has been READY
 * for at least [stableReadyMillis]** (§6), so a flapping link keeps escalating
 * instead of hammering the backend.
 */
class ReconnectPolicy(
    private val baseDelayMillis: Long = 1_000,
    private val maxDelayMillis: Long = 60_000,
    private val factor: Double = 2.0,
    private val stableReadyMillis: Long = 5_000,
    private val random: Random = Random.Default,
) {
    var attempt: Int = 0
        private set

    private var readyAtMillis: Long? = null

    /** The un-jittered ceiling for the current attempt: base·factorᵃᵗᵗᵉᵐᵖᵗ, capped. */
    fun ceilingMillis(): Long {
        val raw = baseDelayMillis.toDouble() * factor.pow(attempt)
        return raw.coerceIn(0.0, maxDelayMillis.toDouble()).toLong()
    }

    /** A full-jitter delay in [0, ceiling] for the current attempt, then advances. */
    fun nextDelayMillis(): Long {
        val ceiling = ceilingMillis()
        val delay = if (ceiling <= 0L) 0L else random.nextLong(ceiling + 1)
        attempt++
        return delay
    }

    fun onReady(nowMillis: Long) {
        readyAtMillis = nowMillis
    }

    /** Call when a connection ends; resets backoff only if READY was stable. */
    fun onConnectionEnded(nowMillis: Long) {
        val readyAt = readyAtMillis
        if (readyAt != null && nowMillis - readyAt >= stableReadyMillis) reset()
        readyAtMillis = null
    }

    fun reset() {
        attempt = 0
    }
}
