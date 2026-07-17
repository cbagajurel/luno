package com.luno.gateway.di

import android.content.Context
import android.os.Build
import com.luno.gateway.BuildConfig
import com.luno.gateway.agent.AgentController
import com.luno.gateway.agent.CommandRouter
import com.luno.gateway.backend.auth.DeviceCredentialStore
import com.luno.gateway.backend.auth.PairingManager
import com.luno.gateway.backend.auth.SharedPrefsStore
import com.luno.gateway.backend.protocol.Event
import com.luno.gateway.backend.protocol.PartSent
import com.luno.gateway.backend.protocol.ProtocolCodec
import com.luno.gateway.backend.rest.DeviceInfo
import com.luno.gateway.backend.rest.RestClient
import com.luno.gateway.backend.ws.ConnectionManager
import com.luno.gateway.backend.ws.EventPublisher
import com.luno.gateway.backend.ws.Heartbeat
import com.luno.gateway.backend.ws.ReconnectPolicy
import com.luno.gateway.backend.ws.WebSocketClient
import com.luno.gateway.data.db.LunoDatabase
import com.luno.gateway.data.repository.DeliveryTracker
import com.luno.gateway.data.repository.InboxRepository
import com.luno.gateway.data.repository.OutboxDispatcher
import com.luno.gateway.data.repository.OutboxRepository
import com.luno.gateway.agent.EventKeys
import com.luno.gateway.logging.LogcatSink
import com.luno.gateway.logging.LunoLogger
import com.luno.gateway.logging.Redaction
import com.luno.gateway.logging.RingBufferLogSink
import com.luno.gateway.security.CryptoBox
import com.luno.gateway.security.Pinning
import com.luno.gateway.security.PolicyStore
import com.luno.gateway.security.RateLimiter
import com.luno.gateway.work.AgentWatchdogWorker
import com.luno.gateway.telephony.BatteryMonitor
import com.luno.gateway.telephony.DeviceStateStore
import com.luno.gateway.telephony.NetworkMonitor
import com.luno.gateway.telephony.SignalStrengthMonitor
import com.luno.gateway.telephony.SimInfoManager
import com.luno.gateway.transport.TransportRegistry
import com.luno.gateway.transport.sms.DeliveryReportRouter
import com.luno.gateway.transport.sms.SentReportRouter
import com.luno.gateway.transport.sms.SmsSender
import com.luno.gateway.security.KeystoreManager
import com.luno.gateway.transport.sms.SmsTransport
import com.luno.gateway.util.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class AgentGraph(context: Context) {
    private val appContext: Context = context.applicationContext

    val logBuffer: RingBufferLogSink = RingBufferLogSink()
    val logger: LunoLogger = LunoLogger(listOf(LogcatSink(), logBuffer), Redaction::redact)

    val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // PII-at-rest key, distinct from the credential key. seal on write / open on read.
    private val dataCrypto: CryptoBox = CryptoBox(KeystoreManager(DATA_KEY_ALIAS))
    private fun openOrBlank(sealed: String): String = dataCrypto.openOrNull(sealed) ?: ""

    val deviceStateStore: DeviceStateStore = DeviceStateStore()
    val simInfoManager: SimInfoManager = SimInfoManager(appContext, deviceStateStore, logger)
    val batteryMonitor: BatteryMonitor = BatteryMonitor(appContext, deviceStateStore, logger)
    val signalStrengthMonitor: SignalStrengthMonitor =
        SignalStrengthMonitor(appContext, deviceStateStore, logger)
    val networkMonitor: NetworkMonitor = NetworkMonitor(appContext, deviceStateStore, logger)

    private val database: LunoDatabase = LunoDatabase.build(appContext)
    val outboxRepository: OutboxRepository =
        OutboxRepository(database.outboxDao(), logger, seal = dataCrypto::seal, open = ::openOrBlank)
    val outboxPartDao = database.outboxPartDao()
    val inboxRepository: InboxRepository =
        InboxRepository(database.inboxDao(), logger, seal = dataCrypto::seal, open = ::openOrBlank)
    private val eventOutboxDao = database.eventOutboxDao()

    private val codec: ProtocolCodec = ProtocolCodec()

    // App-scoped so sent/delivery reports still route if the PendingIntent fires
    // after the debug UI is gone. Unregistered on process death (app-lifetime singleton).
    private val sentReportRouter: SentReportRouter =
        SentReportRouter(appContext, logger).also { it.register() }
    private val deliveryReportRouter: DeliveryReportRouter =
        DeliveryReportRouter(appContext, logger).also { it.register() }
    private val smsTransport: SmsTransport =
        SmsTransport(SmsSender(appContext), sentReportRouter, deliveryReportRouter, logger)

    val transportRegistry: TransportRegistry = TransportRegistry().apply {
        register(smsTransport)
    }

    private val deliveryTracker: DeliveryTracker =
        DeliveryTracker(outboxRepository, outboxPartDao, appScope, logger)
            .also { it.start(smsTransport) }

    // --- backend connection (M13/M14) ---
    val credentialStore: DeviceCredentialStore =
        DeviceCredentialStore(
            SharedPrefsStore(appContext.getSharedPreferences("luno_secure_prefs", Context.MODE_PRIVATE)),
            KeystoreManager(),
            logger,
        )

    private val deviceInfo =
        DeviceInfo(
            model = Build.MODEL ?: "unknown",
            manufacturer = Build.MANUFACTURER ?: "unknown",
            androidSdk = Build.VERSION.SDK_INT,
            appVersion =
                runCatching { appContext.packageManager.getPackageInfo(appContext.packageName, 0).versionName }
                    .getOrNull() ?: "unknown",
        )

    val pairingManager: PairingManager =
        PairingManager(RestClient(allowInsecure = BuildConfig.DEBUG), credentialStore, deviceInfo, logger)

    private val online: StateFlow<Boolean> =
        deviceStateStore.state
            .map { it.network?.connected == true }
            .stateIn(appScope, SharingStarted.Eagerly, false)

    private val outstandingOutboxIds: StateFlow<List<String>> =
        outboxRepository.observeOutstandingCommandIds()
            .stateIn(appScope, SharingStarted.Eagerly, emptyList())

    // Cert pinning seam (off by default — no pins configured yet; delivered via config later).
    val connectionManager: ConnectionManager =
        ConnectionManager(
            socket =
                WebSocketClient(
                    logger = logger,
                    pinner = Pinning.buildPinner(CERT_PIN_HOST, CERT_PINS),
                    allowInsecure = BuildConfig.DEBUG,
                ),
            codec = codec,
            reconnectPolicy = ReconnectPolicy(),
            scope = appScope,
            clock = SystemClock,
            online = online,
            credentialProvider = { credentialStore.load() },
            logger = logger,
            lastAckedInboundSeq = { agentController.lastAckedInboundSeq() },
            outstandingOutboxIds = { outstandingOutboxIds.value },
        )

    private val eventPublisher: EventPublisher =
        EventPublisher(
            sink = connectionManager,
            scope = appScope,
            logger = logger,
            dao = eventOutboxDao,
            codec = codec,
            onEventAcked = { type, correlationId ->
                if (type == Event.SmsReceived.TYPE && correlationId != null) {
                    inboxRepository.markAcked(correlationId)
                }
            },
            seal = dataCrypto::seal,
            open = ::openOrBlank,
        )

    // Backend-authoritative-but-client-enforced send safety (§ threat model).
    private val policyStore: PolicyStore =
        PolicyStore(SharedPrefsStore(appContext.getSharedPreferences("luno_policy", Context.MODE_PRIVATE)))
    private val rateLimiter: RateLimiter =
        RateLimiter().apply { setLimit(policyStore.load().rateLimitPerMinute) }

    // --- M14: protocol wired to SMS + heartbeat ---
    val outboxDispatcher: OutboxDispatcher = OutboxDispatcher(
        outboxRepository,
        transportRegistry,
        logger,
        appScope,
        onSent = { messageId, parts ->
            deliveryTracker.onSent(messageId, parts)
            eventPublisher.reliable(
                Event.SmsSent(messageId, parts.map { PartSent(it.index, STATUS_SENT) }),
                EventKeys.sent(messageId),
            )
        },
        onFailed = { messageId, error ->
            eventPublisher.reliable(
                Event.Error(code = error.code, message = error.message, ref = messageId),
                EventKeys.error(messageId),
            )
        },
    )

    private val queueDepth: StateFlow<Int> =
        outboxRepository.observeQueueDepth().stateIn(appScope, SharingStarted.Eagerly, 0)

    private val heartbeat: Heartbeat =
        Heartbeat(
            events = eventPublisher,
            deviceState = { deviceStateStore.state.value },
            queueDepth = { queueDepth.value },
            transports = { transportRegistry.all().map { it.id.name } },
            scope = appScope,
            logger = logger,
        )

    private val commandRouter: CommandRouter =
        CommandRouter(
            outbox = outboxRepository,
            dispatcher = outboxDispatcher,
            events = eventPublisher,
            ack = connectionManager,
            deviceState = { deviceStateStore.state.value },
            onHeartbeatInterval = heartbeat::setIntervalSeconds,
            logger = logger,
            rateLimiter = rateLimiter,
            allowlist = { policyStore.load().allowlist.toSet() },
            applyPolicy = { rate, allow ->
                val updated = policyStore.update(rate, allow)
                rateLimiter.setLimit(updated.rateLimitPerMinute)
            },
            onWipe = { wipeNode() },
        )

    val agentController: AgentController =
        AgentController(
            logger = logger,
            scope = appScope,
            incoming = connectionManager.incoming,
            connectionState = connectionManager.state,
            events = eventPublisher,
            router = commandRouter,
            heartbeat = heartbeat,
            dispatcher = outboxDispatcher,
            inbox = inboxRepository,
            deliveryReports = smsTransport.deliveryReports(),
        )

    /** Remote revoke/wipe (§8.2): clear credential + all queues + policy, drop the link, return to unpaired. */
    private suspend fun wipeNode() {
        outboxRepository.clearAll()
        inboxRepository.clearAll()
        eventOutboxDao.deleteAll()
        credentialStore.clear()
        policyStore.clear()
        rateLimiter.setLimit(0)
        connectionManager.disconnect()
        AgentWatchdogWorker.cancel(appContext)
        logger.i(TAG, "node wiped; returned to unpaired")
    }

    companion object {
        private const val TAG = "AgentGraph"
        private const val STATUS_SENT = "sent"
        private const val DATA_KEY_ALIAS = "luno_data_key"
        private const val CERT_PIN_HOST = ""
        private val CERT_PINS = emptyList<String>()
    }
}
