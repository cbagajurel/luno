package com.luno.gateway.security

/**
 * A client-side sliding-window cap on outbound sends (§ threat model: "the node as a
 * spam cannon / SIM ban"). This is a *safety* control enforced independently of the
 * backend — even a compromised or buggy backend can't blast the SIM past the limit.
 * `perMinute <= 0` means unlimited (no backend policy set). Clock is injected; the
 * limiter is otherwise pure and thread-safe.
 */
class RateLimiter(
    perMinute: Int = 0,
    private val windowMillis: Long = 60_000L,
) {
    private var perMinute: Int = perMinute
    private val hits = ArrayDeque<Long>()

    @Synchronized
    fun tryAcquire(now: Long): Boolean {
        if (perMinute <= 0) return true
        while (hits.isNotEmpty() && now - hits.first() >= windowMillis) hits.removeFirst()
        if (hits.size >= perMinute) return false
        hits.addLast(now)
        return true
    }

    @Synchronized
    fun setLimit(perMinute: Int) {
        this.perMinute = perMinute
        hits.clear()
    }
}
