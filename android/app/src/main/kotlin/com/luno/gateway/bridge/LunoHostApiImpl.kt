package com.luno.gateway.bridge

import com.luno.gateway.bridge.generated.LunoHostApi
import com.luno.gateway.data.db.dao.OutboxPartDao
import com.luno.gateway.data.db.entity.InboxEntity
import com.luno.gateway.data.db.entity.OutboxEntity
import com.luno.gateway.data.repository.InboxRepository
import com.luno.gateway.data.repository.OutboxDispatcher
import com.luno.gateway.data.repository.OutboxRepository
import com.luno.gateway.model.OutboundMessage
import com.luno.gateway.telephony.DeviceStateStore
import com.luno.gateway.util.Ids
import kotlinx.coroutines.runBlocking
import com.luno.gateway.bridge.generated.DeviceState as DeviceStateDto
import com.luno.gateway.bridge.generated.InboundEntry as InboundEntryDto
import com.luno.gateway.bridge.generated.OutboxEntry as OutboxEntryDto

class LunoHostApiImpl(
    private val host: AgentHost,
    private val deviceStateStore: DeviceStateStore,
    private val outboxRepository: OutboxRepository,
    private val outboxDispatcher: OutboxDispatcher,
    private val outboxPartDao: OutboxPartDao,
    private val inboxRepository: InboxRepository,
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

    override fun getRecentOutbox(): List<OutboxEntryDto> = runBlocking {
        outboxRepository.recent().map { row ->
            row.toDto(
                partCount = outboxPartDao.countFor(row.id),
                deliveredCount = outboxPartDao.deliveredCountFor(row.id),
            )
        }
    }

    override fun hasReceiveSmsPermission(): Boolean = host.hasReceiveSmsPermission()

    override fun requestReceiveSmsPermission() = host.requestReceiveSmsPermission()

    override fun getRecentInbox(): List<InboundEntryDto> =
        runBlocking { inboxRepository.recent() }.map { it.toDto() }

    companion object {
        const val ECHO_PREFIX = "Luno-Kotlin echo: "
    }
}

private fun InboxEntity.toDto() = InboundEntryDto(
    id = id,
    sender = sender,
    body = body,
    subscriptionId = subscriptionId?.toLong(),
    receivedAt = receivedAt,
    parts = parts.toLong(),
)

private fun OutboxEntity.toDto(partCount: Int, deliveredCount: Int) = OutboxEntryDto(
    id = id,
    recipient = recipient,
    status = status.name,
    lastError = lastError,
    attempt = attempt.toLong(),
    createdAt = createdAt,
    partCount = partCount.toLong(),
    deliveredCount = deliveredCount.toLong(),
)
