package com.luno.gateway.bridge

import com.luno.gateway.bridge.generated.LunoHostApi
import com.luno.gateway.data.db.entity.OutboxEntity
import com.luno.gateway.data.repository.OutboxDispatcher
import com.luno.gateway.data.repository.OutboxRepository
import com.luno.gateway.model.OutboundMessage
import com.luno.gateway.telephony.DeviceStateStore
import com.luno.gateway.util.Ids
import kotlinx.coroutines.runBlocking
import com.luno.gateway.bridge.generated.DeviceState as DeviceStateDto
import com.luno.gateway.bridge.generated.OutboxEntry as OutboxEntryDto

class LunoHostApiImpl(
    private val host: AgentHost,
    private val deviceStateStore: DeviceStateStore,
    private val outboxRepository: OutboxRepository,
    private val outboxDispatcher: OutboxDispatcher,
) : LunoHostApi {
    override fun ping(message: String): String = "$ECHO_PREFIX$message"

    override fun startAgent() = host.startAgent()

    override fun stopAgent() = host.stopAgent()

    override fun isAgentRunning(): Boolean = host.isAgentRunning()

    override fun requestNotificationPermission() = host.requestNotificationPermission()

    override fun getDeviceState(): DeviceStateDto = deviceStateStore.current.toDto()

    override fun hasPhonePermission(): Boolean = host.hasPhonePermission()

    override fun requestPhonePermission() = host.requestPhonePermission()

    override fun hasSmsPermission(): Boolean = host.hasSmsPermission()

    override fun requestSmsPermission() = host.requestSmsPermission()

    override fun sendSms(recipient: String, body: String, subscriptionId: Long?): String {
        val message = OutboundMessage(
            id = Ids.newId(),
            recipient = recipient,
            body = body,
            subscriptionId = subscriptionId?.toInt(),
        )
        return outboxDispatcher.submit(message)
    }

    override fun getRecentOutbox(): List<OutboxEntryDto> =
        runBlocking { outboxRepository.recent() }.map { it.toDto() }

    companion object {
        const val ECHO_PREFIX = "Luno-Kotlin echo: "
    }
}

private fun OutboxEntity.toDto() = OutboxEntryDto(
    id = id,
    recipient = recipient,
    status = status.name,
    lastError = lastError,
    attempt = attempt.toLong(),
    createdAt = createdAt,
)
