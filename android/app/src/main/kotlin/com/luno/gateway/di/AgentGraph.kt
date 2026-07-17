package com.luno.gateway.di

import android.content.Context
import android.os.Build
import com.luno.gateway.agent.AgentController
import com.luno.gateway.backend.auth.DeviceCredentialStore
import com.luno.gateway.backend.auth.PairingManager
import com.luno.gateway.backend.auth.SharedPrefsStore
import com.luno.gateway.backend.protocol.ProtocolCodec
import com.luno.gateway.backend.rest.DeviceInfo
import com.luno.gateway.backend.rest.RestClient
import com.luno.gateway.backend.ws.ConnectionManager
import com.luno.gateway.backend.ws.ReconnectPolicy
import com.luno.gateway.backend.ws.WebSocketClient
import com.luno.gateway.data.db.LunoDatabase
import com.luno.gateway.data.repository.DeliveryTracker
import com.luno.gateway.data.repository.InboxRepository
import com.luno.gateway.data.repository.OutboxDispatcher
import com.luno.gateway.data.repository.OutboxRepository
import com.luno.gateway.logging.LogcatSink
import com.luno.gateway.logging.LunoLogger
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

    val logger: LunoLogger = LunoLogger(LogcatSink())
    val agentController: AgentController = AgentController(logger)

    val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val deviceStateStore: DeviceStateStore = DeviceStateStore()
    val simInfoManager: SimInfoManager = SimInfoManager(appContext, deviceStateStore, logger)
    val batteryMonitor: BatteryMonitor = BatteryMonitor(appContext, deviceStateStore, logger)
    val signalStrengthMonitor: SignalStrengthMonitor =
        SignalStrengthMonitor(appContext, deviceStateStore, logger)
    val networkMonitor: NetworkMonitor = NetworkMonitor(appContext, deviceStateStore, logger)

    private val database: LunoDatabase = LunoDatabase.build(appContext)
    val outboxRepository: OutboxRepository = OutboxRepository(database.outboxDao(), logger)
    val outboxPartDao = database.outboxPartDao()
    val inboxRepository: InboxRepository = InboxRepository(database.inboxDao(), logger)

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

    val outboxDispatcher: OutboxDispatcher = OutboxDispatcher(
        outboxRepository,
        transportRegistry,
        logger,
        appScope,
        onSent = deliveryTracker::onSent,
    )

    // --- backend connection (M13) ---
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
        PairingManager(RestClient(), credentialStore, deviceInfo, logger)

    private val online: StateFlow<Boolean> =
        deviceStateStore.state
            .map { it.network?.connected == true }
            .stateIn(appScope, SharingStarted.Eagerly, false)

    val connectionManager: ConnectionManager =
        ConnectionManager(
            socket = WebSocketClient(logger = logger),
            codec = ProtocolCodec(),
            reconnectPolicy = ReconnectPolicy(),
            scope = appScope,
            clock = SystemClock,
            online = online,
            credentialProvider = { credentialStore.load() },
            logger = logger,
        )
}
