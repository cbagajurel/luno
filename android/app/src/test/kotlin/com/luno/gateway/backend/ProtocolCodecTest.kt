package com.luno.gateway.backend

import com.luno.gateway.backend.protocol.Ack
import com.luno.gateway.backend.protocol.BatteryDto
import com.luno.gateway.backend.protocol.Command
import com.luno.gateway.backend.protocol.Control
import com.luno.gateway.backend.protocol.DecodeResult
import com.luno.gateway.backend.protocol.Event
import com.luno.gateway.backend.protocol.FrameBody
import com.luno.gateway.backend.protocol.NetworkDto
import com.luno.gateway.backend.protocol.PartSent
import com.luno.gateway.backend.protocol.ProtocolCodec
import com.luno.gateway.backend.protocol.ProtocolFrame
import com.luno.gateway.backend.protocol.ProtocolVersion
import com.luno.gateway.backend.protocol.SignalDto
import com.luno.gateway.backend.protocol.SimDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProtocolCodecTest {
    private val codec = ProtocolCodec()

    private fun frameFor(body: FrameBody) =
        ProtocolFrame(v = 1, id = "id-1", ts = "2026-07-16T09:00:00Z", deviceId = "dev_abc", seq = 128, body = body)

    private fun roundTrip(body: FrameBody) {
        val original = frameFor(body)
        val decoded = codec.decode(codec.encode(original))
        assertTrue("expected Ok for $body, got $decoded", decoded is DecodeResult.Ok)
        assertEquals(original, (decoded as DecodeResult.Ok).frame)
    }

    @Test
    fun `every command round-trips`() {
        listOf(
            Command.SendSms(to = "+15551234", body = "hello", subscriptionId = 2, ref = "r1"),
            Command.SendSms(to = "+15551234", body = "no optionals"),
            Command.CancelSms(commandId = "cmd-9"),
            Command.GetStatus,
            Command.ConfigUpdate(heartbeatSec = 30, rateLimitPerMinute = 10, allowlist = listOf("+1", "+2"), credential = "tok"),
            Command.ConfigUpdate(),
            Command.Revoke,
            Command.Wipe,
        ).forEach { roundTrip(FrameBody.CommandBody(it)) }
    }

    @Test
    fun `every event round-trips`() {
        listOf(
            Event.SmsAccepted(commandId = "c1", messageId = "m1"),
            Event.SmsSent(messageId = "m1", parts = listOf(PartSent(0, "SENT"), PartSent(1, "FAILED", "no_service"))),
            Event.DeliveryReport(messageId = "m1", part = 0, status = "DELIVERED", at = 1_700_000_000_000),
            Event.SmsReceived(from = "+199", body = "hi", subscriptionId = 1, receivedAt = 1_700_000_000_000, parts = 2),
            Event.DeviceStatus(
                battery = BatteryDto(82, charging = true, plugged = "AC", health = "GOOD"),
                network = NetworkDto(connected = true, validated = true, transport = "WIFI", metered = false),
                sims = listOf(SimDto(1, 0, "Carrier", "SIM 1", embedded = false, simState = "READY")),
            ),
            Event.Heartbeat(queueDepth = 3, battery = 80, signals = listOf(SignalDto(1, -95, 3)), transports = listOf("SMS")),
            Event.Log(level = "INFO", tag = "agent", msg = "up", at = 1_700_000_000_000),
            Event.Error(code = "bad_number", message = "invalid recipient", ref = "r1"),
        ).forEach { roundTrip(FrameBody.EventBody(it)) }
    }

    @Test
    fun `ack round-trips`() {
        roundTrip(FrameBody.AckBody(Ack(ackedId = "frame-7")))
    }

    @Test
    fun `every control frame round-trips`() {
        listOf(
            Control.Resync(lastAckedInboundSeq = 42, outstandingOutboxIds = listOf("o1", "o2")),
            Control.VersionNegotiate(supported = listOf(1), selected = 1),
            Control.Ping,
            Control.Pong,
        ).forEach { roundTrip(FrameBody.ControlBody(it)) }
    }

    @Test
    fun `unknown fields are ignored for forward compatibility`() {
        val wire =
            """
            {"v":1,"kind":"command","id":"id-1","ts":"2026-07-16T09:00:00Z","deviceId":"dev_abc",
             "type":"send_sms","seq":5,
             "payload":{"to":"+1","body":"hi","futureField":"ignored"},
             "unknownTopLevel":true}
            """.trimIndent()

        val decoded = codec.decode(wire)
        assertTrue(decoded is DecodeResult.Ok)
        val body = (decoded as DecodeResult.Ok).frame.body
        assertEquals(Command.SendSms(to = "+1", body = "hi"), (body as FrameBody.CommandBody).command)
    }

    @Test
    fun `malformed text is quarantined, not thrown`() {
        assertTrue(codec.decode("this is not json") is DecodeResult.Malformed)
        assertTrue(codec.decode("""{"kind":"command"}""") is DecodeResult.Malformed)
    }

    @Test
    fun `unknown type is unsupported, not thrown`() {
        val wire =
            """{"v":1,"kind":"command","id":"i","ts":"t","deviceId":"d","type":"teleport","seq":1,"payload":{}}"""
        val decoded = codec.decode(wire)
        assertTrue(decoded is DecodeResult.Unsupported)
        assertEquals("teleport", (decoded as DecodeResult.Unsupported).envelope.type)
    }

    @Test
    fun `version negotiation picks highest mutually supported`() {
        assertEquals(1, ProtocolVersion.negotiate(setOf(1, 2, 3)))
        assertEquals(1, ProtocolVersion.negotiate(setOf(1)))
        assertEquals(null, ProtocolVersion.negotiate(setOf(2, 3)))
        assertEquals(null, ProtocolVersion.negotiate(emptySet()))
    }
}
