package com.luno.gateway.backend.protocol

import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/** A fully-typed frame: envelope metadata (§8.1) plus its decoded [body]. */
data class ProtocolFrame(
    val v: Int,
    val id: String,
    val ts: String,
    val deviceId: String,
    val seq: Long,
    val body: FrameBody,
) {
    companion object {
        fun command(v: Int, id: String, ts: String, deviceId: String, seq: Long, command: Command) =
            ProtocolFrame(v, id, ts, deviceId, seq, FrameBody.CommandBody(command))

        fun event(v: Int, id: String, ts: String, deviceId: String, seq: Long, event: Event) =
            ProtocolFrame(v, id, ts, deviceId, seq, FrameBody.EventBody(event))

        fun ack(v: Int, id: String, ts: String, deviceId: String, seq: Long, ack: Ack) =
            ProtocolFrame(v, id, ts, deviceId, seq, FrameBody.AckBody(ack))

        fun control(v: Int, id: String, ts: String, deviceId: String, seq: Long, control: Control) =
            ProtocolFrame(v, id, ts, deviceId, seq, FrameBody.ControlBody(control))
    }
}

/** The four frame families, tagging a payload with its [FrameKind] and [type]. */
sealed interface FrameBody {
    val kind: FrameKind
    val type: String

    data class CommandBody(val command: Command) : FrameBody {
        override val kind get() = FrameKind.COMMAND
        override val type get() = command.type
    }

    data class EventBody(val event: Event) : FrameBody {
        override val kind get() = FrameKind.EVENT
        override val type get() = event.type
    }

    data class AckBody(val ack: Ack) : FrameBody {
        override val kind get() = FrameKind.ACK
        override val type get() = Ack.TYPE
    }

    data class ControlBody(val control: Control) : FrameBody {
        override val kind get() = FrameKind.CONTROL
        override val type get() = control.type
    }
}

sealed interface DecodeResult {
    data class Ok(val frame: ProtocolFrame) : DecodeResult

    /** Envelope parsed, but its (kind, type) isn't one we know — quarantine, don't crash. */
    data class Unsupported(val envelope: Envelope, val reason: String) : DecodeResult

    /** Not even a well-formed envelope — quarantine the raw text. */
    data class Malformed(val raw: String, val reason: String) : DecodeResult
}

/**
 * Encodes/decodes the wire protocol (§8). The single place that maps a
 * (kind, type) to a concrete payload serializer; unknown fields are ignored and
 * malformed frames are quarantined rather than throwing, so a newer backend and
 * an older node interoperate (§8.5).
 */
class ProtocolCodec(
    private val json: Json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            explicitNulls = false
        },
) {
    fun encode(frame: ProtocolFrame): String {
        val envelope =
            Envelope(
                v = frame.v,
                kind = frame.body.kind,
                id = frame.id,
                ts = frame.ts,
                deviceId = frame.deviceId,
                type = frame.body.type,
                seq = frame.seq,
                payload = payloadOf(frame.body),
            )
        return json.encodeToString(Envelope.serializer(), envelope)
    }

    fun decode(text: String): DecodeResult {
        val envelope =
            try {
                json.decodeFromString(Envelope.serializer(), text)
            } catch (e: Exception) {
                return DecodeResult.Malformed(text, "envelope: ${e.message}")
            }

        val body =
            try {
                bodyOf(envelope)
            } catch (e: Exception) {
                return DecodeResult.Malformed(text, "payload: ${e.message}")
            } ?: return DecodeResult.Unsupported(envelope, "unknown ${envelope.kind}/${envelope.type}")

        return DecodeResult.Ok(
            ProtocolFrame(envelope.v, envelope.id, envelope.ts, envelope.deviceId, envelope.seq, body),
        )
    }

    private fun payloadOf(body: FrameBody): JsonObject =
        when (body) {
            is FrameBody.CommandBody -> encodeCommand(body.command)
            is FrameBody.EventBody -> encodeEvent(body.event)
            is FrameBody.AckBody -> encode(Ack.serializer(), body.ack)
            is FrameBody.ControlBody -> encodeControl(body.control)
        }

    private fun bodyOf(envelope: Envelope): FrameBody? =
        when (envelope.kind) {
            FrameKind.COMMAND -> decodeCommand(envelope.type, envelope.payload)?.let(FrameBody::CommandBody)
            FrameKind.EVENT -> decodeEvent(envelope.type, envelope.payload)?.let(FrameBody::EventBody)
            FrameKind.ACK -> FrameBody.AckBody(decode(Ack.serializer(), envelope.payload))
            FrameKind.CONTROL -> decodeControl(envelope.type, envelope.payload)?.let(FrameBody::ControlBody)
        }

    private fun encodeCommand(command: Command): JsonObject =
        when (command) {
            is Command.SendSms -> encode(Command.SendSms.serializer(), command)
            is Command.CancelSms -> encode(Command.CancelSms.serializer(), command)
            is Command.GetStatus -> encode(Command.GetStatus.serializer(), command)
            is Command.ConfigUpdate -> encode(Command.ConfigUpdate.serializer(), command)
            is Command.Revoke -> encode(Command.Revoke.serializer(), command)
            is Command.Wipe -> encode(Command.Wipe.serializer(), command)
        }

    private fun decodeCommand(type: String, payload: JsonObject): Command? =
        when (type) {
            Command.SendSms.TYPE -> decode(Command.SendSms.serializer(), payload)
            Command.CancelSms.TYPE -> decode(Command.CancelSms.serializer(), payload)
            Command.GetStatus.type -> decode(Command.GetStatus.serializer(), payload)
            Command.ConfigUpdate.TYPE -> decode(Command.ConfigUpdate.serializer(), payload)
            Command.Revoke.type -> decode(Command.Revoke.serializer(), payload)
            Command.Wipe.type -> decode(Command.Wipe.serializer(), payload)
            else -> null
        }

    private fun encodeEvent(event: Event): JsonObject =
        when (event) {
            is Event.SmsAccepted -> encode(Event.SmsAccepted.serializer(), event)
            is Event.SmsSent -> encode(Event.SmsSent.serializer(), event)
            is Event.DeliveryReport -> encode(Event.DeliveryReport.serializer(), event)
            is Event.SmsReceived -> encode(Event.SmsReceived.serializer(), event)
            is Event.DeviceStatus -> encode(Event.DeviceStatus.serializer(), event)
            is Event.Heartbeat -> encode(Event.Heartbeat.serializer(), event)
            is Event.Log -> encode(Event.Log.serializer(), event)
            is Event.Error -> encode(Event.Error.serializer(), event)
        }

    private fun decodeEvent(type: String, payload: JsonObject): Event? =
        when (type) {
            Event.SmsAccepted.TYPE -> decode(Event.SmsAccepted.serializer(), payload)
            Event.SmsSent.TYPE -> decode(Event.SmsSent.serializer(), payload)
            Event.DeliveryReport.TYPE -> decode(Event.DeliveryReport.serializer(), payload)
            Event.SmsReceived.TYPE -> decode(Event.SmsReceived.serializer(), payload)
            Event.DeviceStatus.TYPE -> decode(Event.DeviceStatus.serializer(), payload)
            Event.Heartbeat.TYPE -> decode(Event.Heartbeat.serializer(), payload)
            Event.Log.TYPE -> decode(Event.Log.serializer(), payload)
            Event.Error.TYPE -> decode(Event.Error.serializer(), payload)
            else -> null
        }

    private fun encodeControl(control: Control): JsonObject =
        when (control) {
            is Control.Resync -> encode(Control.Resync.serializer(), control)
            is Control.VersionNegotiate -> encode(Control.VersionNegotiate.serializer(), control)
            is Control.Ping -> encode(Control.Ping.serializer(), control)
            is Control.Pong -> encode(Control.Pong.serializer(), control)
        }

    private fun decodeControl(type: String, payload: JsonObject): Control? =
        when (type) {
            Control.Resync.TYPE -> decode(Control.Resync.serializer(), payload)
            Control.VersionNegotiate.TYPE -> decode(Control.VersionNegotiate.serializer(), payload)
            Control.Ping.type -> decode(Control.Ping.serializer(), payload)
            Control.Pong.type -> decode(Control.Pong.serializer(), payload)
            else -> null
        }

    private fun <T> encode(serializer: KSerializer<T>, value: T): JsonObject =
        json.encodeToJsonElement(serializer, value).jsonObject

    private fun <T> decode(serializer: KSerializer<T>, payload: JsonObject): T =
        json.decodeFromJsonElement(serializer, payload)
}
