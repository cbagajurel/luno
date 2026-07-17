package com.luno.gateway.backend

import com.luno.gateway.backend.protocol.Event
import com.luno.gateway.backend.ws.EventPublisher
import com.luno.gateway.backend.ws.Heartbeat
import com.luno.gateway.model.BatteryStatus
import com.luno.gateway.model.DeviceState
import com.luno.gateway.testutil.FakeEventOutboxDao
import com.luno.gateway.testutil.FakeEventSink
import com.luno.gateway.testutil.testLogger
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HeartbeatTest {
    private fun deviceState() = DeviceState(battery = BatteryStatus(80, isCharging = true, plugged = "ac", health = "good"))

    @Test
    fun `emits a heartbeat carrying queue depth and battery while ready`() = runTest {
        val sink = FakeEventSink(ready = true)
        val events = EventPublisher(sink, backgroundScope, testLogger(), dao = FakeEventOutboxDao())
        val hb = Heartbeat(events, ::deviceState, queueDepth = { 3 }, transports = { listOf("SMS") }, scope = backgroundScope, logger = testLogger(), initialIntervalSeconds = 1)

        hb.start()
        advanceTimeBy(1_100)
        runCurrent()
        hb.stop()

        val beats = sink.events.map { it.event }.filterIsInstance<Event.Heartbeat>()
        assertEquals(1, beats.size)
        assertEquals(3, beats.first().queueDepth)
        assertEquals(80, beats.first().battery)
        assertEquals(listOf("SMS"), beats.first().transports)
    }

    @Test
    fun `no heartbeat is sent while the link is down`() = runTest {
        val sink = FakeEventSink(ready = false)
        val events = EventPublisher(sink, backgroundScope, testLogger(), dao = FakeEventOutboxDao())
        val hb = Heartbeat(events, ::deviceState, queueDepth = { 0 }, transports = { emptyList() }, scope = backgroundScope, logger = testLogger(), initialIntervalSeconds = 1)

        hb.start()
        advanceTimeBy(2_100)
        runCurrent()
        hb.stop()

        assertEquals(0, sink.events.size)
    }
}
