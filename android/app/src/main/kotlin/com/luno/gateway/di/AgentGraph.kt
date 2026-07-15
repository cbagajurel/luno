package com.luno.gateway.di

import android.content.Context
import com.luno.gateway.agent.AgentController
import com.luno.gateway.data.db.LunoDatabase
import com.luno.gateway.data.repository.InboxRepository
import com.luno.gateway.data.repository.OutboxRepository
import com.luno.gateway.logging.LogcatSink
import com.luno.gateway.logging.LunoLogger
import com.luno.gateway.telephony.BatteryMonitor
import com.luno.gateway.telephony.DeviceStateStore
import com.luno.gateway.telephony.NetworkMonitor
import com.luno.gateway.telephony.SignalStrengthMonitor
import com.luno.gateway.telephony.SimInfoManager
import com.luno.gateway.transport.TransportRegistry
import com.luno.gateway.transport.fake.FakeTransport

class AgentGraph(context: Context) {
    private val appContext: Context = context.applicationContext

    val logger: LunoLogger = LunoLogger(LogcatSink())
    val agentController: AgentController = AgentController(logger)

    val deviceStateStore: DeviceStateStore = DeviceStateStore()
    val simInfoManager: SimInfoManager = SimInfoManager(appContext, deviceStateStore, logger)
    val batteryMonitor: BatteryMonitor = BatteryMonitor(appContext, deviceStateStore, logger)
    val signalStrengthMonitor: SignalStrengthMonitor =
        SignalStrengthMonitor(appContext, deviceStateStore, logger)
    val networkMonitor: NetworkMonitor = NetworkMonitor(appContext, deviceStateStore, logger)

    private val database: LunoDatabase = LunoDatabase.build(appContext)
    val outboxRepository: OutboxRepository = OutboxRepository(database.outboxDao(), logger)
    val inboxRepository: InboxRepository = InboxRepository(database.inboxDao(), logger)

    // FakeTransport until SmsTransport lands (M9); it lets M8 be exercised end to end.
    val transportRegistry: TransportRegistry = TransportRegistry().apply {
        register(FakeTransport())
    }
}
