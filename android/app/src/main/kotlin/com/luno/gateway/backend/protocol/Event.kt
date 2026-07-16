package com.luno.gateway.backend.protocol

import kotlinx.serialization.Serializable

/**
 * Events the node sends to the backend (§8.3). These are wire DTOs, kept
 * deliberately separate from the Kotlin domain models — domain↔wire mapping is
 * the agent's job (M14), so the protocol stays a neutral, transport-agnostic
 * contract.
 */
sealed interface Event {
    val type: String

    @Serializable
    data class SmsAccepted(
        val commandId: String,
        val messageId: String,
    ) : Event {
        override val type: String get() = TYPE

        companion object {
            const val TYPE = "sms_accepted"
        }
    }

    @Serializable
    data class SmsSent(
        val messageId: String,
        val parts: List<PartSent>,
    ) : Event {
        override val type: String get() = TYPE

        companion object {
            const val TYPE = "sms_sent"
        }
    }

    @Serializable
    data class DeliveryReport(
        val messageId: String,
        val part: Int,
        val status: String,
        val at: Long,
    ) : Event {
        override val type: String get() = TYPE

        companion object {
            const val TYPE = "delivery_report"
        }
    }

    @Serializable
    data class SmsReceived(
        val from: String,
        val body: String,
        val subscriptionId: Int? = null,
        val receivedAt: Long,
        val parts: Int = 1,
    ) : Event {
        override val type: String get() = TYPE

        companion object {
            const val TYPE = "sms_received"
        }
    }

    @Serializable
    data class DeviceStatus(
        val battery: BatteryDto? = null,
        val network: NetworkDto? = null,
        val sims: List<SimDto> = emptyList(),
    ) : Event {
        override val type: String get() = TYPE

        companion object {
            const val TYPE = "device_status"
        }
    }

    @Serializable
    data class Heartbeat(
        val queueDepth: Int,
        val battery: Int? = null,
        val signals: List<SignalDto> = emptyList(),
        val transports: List<String> = emptyList(),
    ) : Event {
        override val type: String get() = TYPE

        companion object {
            const val TYPE = "heartbeat"
        }
    }

    @Serializable
    data class Log(
        val level: String,
        val tag: String,
        val msg: String,
        val at: Long,
    ) : Event {
        override val type: String get() = TYPE

        companion object {
            const val TYPE = "log"
        }
    }

    @Serializable
    data class Error(
        val code: String,
        val message: String,
        val ref: String? = null,
    ) : Event {
        override val type: String get() = TYPE

        companion object {
            const val TYPE = "error"
        }
    }
}

@Serializable
data class PartSent(
    val index: Int,
    val status: String,
    val errorCode: String? = null,
)

@Serializable
data class BatteryDto(
    val levelPercent: Int,
    val charging: Boolean,
    val plugged: String,
    val health: String,
)

@Serializable
data class NetworkDto(
    val connected: Boolean,
    val validated: Boolean,
    val transport: String,
    val metered: Boolean,
)

@Serializable
data class SimDto(
    val subscriptionId: Int,
    val slotIndex: Int,
    val carrierName: String,
    val displayName: String,
    val embedded: Boolean,
    val simState: String,
)

@Serializable
data class SignalDto(
    val subscriptionId: Int,
    val dbm: Int? = null,
    val level: Int,
)
