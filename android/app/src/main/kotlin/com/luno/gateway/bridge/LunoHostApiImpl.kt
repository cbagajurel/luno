package com.luno.gateway.bridge

import com.luno.gateway.backend.auth.PairingManager
import com.luno.gateway.backend.auth.PairingPayloadResult
import com.luno.gateway.backend.auth.PairingResult
import com.luno.gateway.backend.ws.ConnectionManager
import com.luno.gateway.bridge.generated.LunoHostApi
import com.luno.gateway.bridge.generated.PermissionStatus
import com.luno.gateway.data.db.dao.OutboxPartDao
import com.luno.gateway.data.db.entity.InboxEntity
import com.luno.gateway.data.db.entity.OutboxEntity
import com.luno.gateway.data.repository.InboxRepository
import com.luno.gateway.data.repository.OutboxDispatcher
import com.luno.gateway.data.repository.OutboxRepository
import com.luno.gateway.logging.RingBufferLogSink
import com.luno.gateway.model.OutboundMessage
import com.luno.gateway.telephony.DeviceStateStore
import com.luno.gateway.util.Ids
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import com.luno.gateway.bridge.generated.DeviceState as DeviceStateDto
import com.luno.gateway.bridge.generated.InboundEntry as InboundEntryDto
import com.luno.gateway.bridge.generated.LogEntry as LogEntryDto
import com.luno.gateway.bridge.generated.OutboxEntry as OutboxEntryDto
import com.luno.gateway.bridge.generated.PairingOutcome as PairingOutcomeDto
import com.luno.gateway.bridge.generated.PairingPayloadInfo as PairingPayloadInfoDto
import com.luno.gateway.bridge.generated.PairingPayloadParse as PairingPayloadParseDto
import com.luno.gateway.bridge.generated.PairingPayloadStatus as PairingPayloadStatusDto
import com.luno.gateway.bridge.generated.PairingResult as PairingResultDto
import com.luno.gateway.bridge.generated.PendingPairing as PendingPairingDto
import com.luno.gateway.logging.LogRecord

class LunoHostApiImpl(
    private val host: AgentHost,
    private val deviceStateStore: DeviceStateStore,
    private val outboxRepository: OutboxRepository,
    private val outboxDispatcher: OutboxDispatcher,
    private val outboxPartDao: OutboxPartDao,
    private val inboxRepository: InboxRepository,
    private val pairingManager: PairingManager,
    private val connectionManager: ConnectionManager,
    private val logBuffer: RingBufferLogSink,
    private val scope: CoroutineScope,
) : LunoHostApi {
    override fun ping(message: String): String = "$ECHO_PREFIX$message"

    override fun startAgent() = host.startAgent()

    override fun stopAgent() = host.stopAgent()

    override fun isAgentRunning(): Boolean = host.isAgentRunning()

    override fun requestNotificationPermission() = host.requestNotificationPermission()

    override fun getDeviceState(): DeviceStateDto = deviceStateStore.current.toDto()

    override fun phonePermissionStatus(): PermissionStatus = host.phonePermissionStatus()

    override fun requestPhonePermission(callback: (Result<PermissionStatus>) -> Unit) =
        host.requestPhonePermission { callback(Result.success(it)) }

    override fun smsPermissionStatus(): PermissionStatus = host.smsPermissionStatus()

    override fun requestSmsPermission(callback: (Result<PermissionStatus>) -> Unit) =
        host.requestSmsPermission { callback(Result.success(it)) }

    override fun openAppSettings() = host.openAppSettings()

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

    override fun isReceiveSmsSupported(): Boolean = host.isReceiveSmsSupported()

    override fun receiveSmsPermissionStatus(): PermissionStatus = host.receiveSmsPermissionStatus()

    override fun requestReceiveSmsPermission(callback: (Result<PermissionStatus>) -> Unit) =
        host.requestReceiveSmsPermission { callback(Result.success(it)) }

    override fun getRecentInbox(): List<InboundEntryDto> =
        runBlocking { inboxRepository.recent() }.map { it.toDto() }

    override fun startPairing(
        backendUrl: String,
        pairingCode: String,
        callback: (Result<PairingResultDto>) -> Unit,
    ) = enroll(callback) { pairingManager.pair(backendUrl, pairingCode) }

    override fun startPairingFromPayload(
        raw: String,
        callback: (Result<PairingResultDto>) -> Unit,
    ) = enroll(callback) { pairingManager.pairFromPayload(raw) }

    override fun checkPairingApproval(callback: (Result<PairingResultDto?>) -> Unit) {
        scope.launch {
            val dto = pairingManager.checkPendingApproval()?.toDto()
            withContext(Dispatchers.Main) { callback(Result.success(dto)) }
        }
    }

    override fun parsePairingPayload(raw: String): PairingPayloadParseDto =
        when (val parsed = pairingManager.parsePayload(raw)) {
            is PairingPayloadResult.Ok ->
                PairingPayloadParseDto(
                    status = PairingPayloadStatusDto.OK,
                    payload =
                        PairingPayloadInfoDto(
                            backendUrl = parsed.payload.backendUrl,
                            pairingCode = parsed.payload.pairingCode,
                            sessionId = parsed.payload.sessionId,
                            label = parsed.payload.label,
                            pin = parsed.payload.pin,
                        ),
                )
            is PairingPayloadResult.UnsupportedVersion ->
                PairingPayloadParseDto(
                    status = PairingPayloadStatusDto.UNSUPPORTED_VERSION,
                    reason = "This pairing code needs a newer version of Luno (format v${parsed.version}).",
                )
            is PairingPayloadResult.Malformed ->
                PairingPayloadParseDto(status = PairingPayloadStatusDto.MALFORMED, reason = parsed.reason)
        }

    override fun pendingPairing(): PendingPairingDto? =
        pairingManager.pendingEnrollment()?.let {
            PendingPairingDto(
                enrollmentId = it.enrollmentId,
                backendUrl = it.backendUrl,
                retryAfterMs = it.retryAfterMillis,
                startedAtMs = it.startedAtMillis,
                label = it.label,
            )
        }

    override fun cancelPendingPairing() = pairingManager.cancelPendingApproval()

    override fun isPaired(): Boolean = pairingManager.isPaired()

    override fun unpair() {
        pairingManager.unpair()
        connectionManager.disconnect()
    }

    private fun enroll(
        callback: (Result<PairingResultDto>) -> Unit,
        attempt: suspend () -> PairingResult,
    ) {
        scope.launch {
            val dto = attempt().toDto()
            withContext(Dispatchers.Main) { callback(Result.success(dto)) }
        }
    }

    /**
     * Reports the backend's own wire code when it sent one, so a verdict this
     * build doesn't recognise still reaches the UI intact.
     */
    private fun PairingResult.toDto(): PairingResultDto =
        when (this) {
            is PairingResult.Success -> {
                connectionManager.kick()
                PairingResultDto(outcome = PairingOutcomeDto.SUCCESS, deviceId = deviceId)
            }
            is PairingResult.Pending ->
                PairingResultDto(outcome = PairingOutcomeDto.PENDING, retryAfterMs = retryAfterMillis)
            is PairingResult.Failure ->
                PairingResultDto(
                    outcome = PairingOutcomeDto.FAILURE,
                    errorCode = rawCode ?: error.wireCode,
                    message = message,
                )
        }

    override fun getRecentLogs(): List<LogEntryDto> = logBuffer.snapshot().map { it.toDto() }

    companion object {
        const val ECHO_PREFIX = "Luno-Kotlin echo: "
    }
}

private fun LogRecord.toDto() = LogEntryDto(
    timestampMs = timestampMs,
    level = level.name,
    tag = tag,
    message = message,
)

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
