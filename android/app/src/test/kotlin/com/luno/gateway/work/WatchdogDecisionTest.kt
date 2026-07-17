package com.luno.gateway.work

import org.junit.Assert.assertEquals
import org.junit.Test

class WatchdogDecisionTest {
    @Test
    fun `revives only a paired node whose agent is down`() {
        assertEquals(WatchdogAction.REVIVE, WatchdogDecision.decide(paired = true, running = false))
    }

    @Test
    fun `does nothing when already running or unpaired`() {
        assertEquals(WatchdogAction.NONE, WatchdogDecision.decide(paired = true, running = true))
        assertEquals(WatchdogAction.NONE, WatchdogDecision.decide(paired = false, running = false))
        assertEquals(WatchdogAction.NONE, WatchdogDecision.decide(paired = false, running = true))
    }
}
