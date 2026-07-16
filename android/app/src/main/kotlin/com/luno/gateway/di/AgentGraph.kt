package com.luno.gateway.di

import android.content.Context
import com.luno.gateway.agent.AgentController
import com.luno.gateway.data.db.LunoDatabase
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
import com.luno.gateway.transport.sms.SentReportRouter
import com.luno.gateway.transport.sms.SmsSender
import com.luno.gateway.transport.sms.SmsTransport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class AgentGraph(context: Context) {
    private val appContext: Context = context.applicationContext

    val logger: LunoLogger = LunoLogger(LogcatSink())
    val agentController: AgentController = AgentController(logger)

    private val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val deviceStateStore: DeviceStateStore = DeviceStateStore()
    val simInfoManager: SimInfoManager = SimInfoManager(appContext, deviceStateStore, logger)
    val batteryMonitor: BatteryMonitor = BatteryMonitor(appContext, deviceStateStore, logger)
    val signalStrengthMonitor: SignalStrengthMonitor =
        SignalStrengthMonitor(appContext, deviceStateStore, logger)
    val networkMonitor: NetworkMonitor = NetworkMonitor(appContext, deviceStateStore, logger)

    private val database: LunoDatabase = LunoDatabase.build(appContext)
    val outboxRepository: OutboxRepository = OutboxRepository(database.outboxDao(), logger)
    val inboxRepository: InboxRepository = InboxRepository(database.inboxDao(), logger)

    // App-scoped so sent reports still route if the sender's PendingIntent fires
    // after the debug UI is gone. Unregistered on process death (app-lifetime singleton).
    private val sentReportRouter: SentReportRouter =
        SentReportRouter(appContext, logger).also { it.register() }
    private val smsTransport: SmsTransport =
        SmsTransport(SmsSender(appContext), sentReportRouter, logger)

    val transportRegistry: TransportRegistry = TransportRegistry().apply {
        register(smsTransport)
    }

    val outboxDispatcher: OutboxDispatcher =
        OutboxDispatcher(outboxRepository, transportRegistry, logger, appScope)
}
