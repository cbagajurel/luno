package com.luno.gateway.di

import android.content.Context
import com.luno.gateway.agent.AgentController
import com.luno.gateway.logging.LogcatSink
import com.luno.gateway.logging.LunoLogger
import com.luno.gateway.telephony.BatteryMonitor
import com.luno.gateway.telephony.DeviceStateStore
import com.luno.gateway.telephony.SignalStrengthMonitor
import com.luno.gateway.telephony.SimInfoManager

class AgentGraph(context: Context) {
    private val appContext: Context = context.applicationContext

    val logger: LunoLogger = LunoLogger(LogcatSink())
    val agentController: AgentController = AgentController(logger)

    val deviceStateStore: DeviceStateStore = DeviceStateStore()
    val simInfoManager: SimInfoManager = SimInfoManager(appContext, deviceStateStore, logger)
    val batteryMonitor: BatteryMonitor = BatteryMonitor(appContext, deviceStateStore, logger)
    val signalStrengthMonitor: SignalStrengthMonitor =
        SignalStrengthMonitor(appContext, deviceStateStore, logger)
}
