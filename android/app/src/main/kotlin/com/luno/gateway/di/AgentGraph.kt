package com.luno.gateway.di

import android.content.Context
import com.luno.gateway.agent.AgentController
import com.luno.gateway.logging.LogcatSink
import com.luno.gateway.logging.LunoLogger
import com.luno.gateway.telephony.BatteryMonitor
import com.luno.gateway.telephony.DeviceStateStore
import com.luno.gateway.telephony.SimInfoManager

/**
 * The single composition root for the native agent — manual DI by decision
 * (plan.md §Decisions #6). Everything long-lived is constructed here once and
 * shared, so dependencies are explicit and the graph is trivially testable.
 *
 * Built by [com.luno.gateway.LunoApplication] and reachable from any component
 * (service, Activity) via the application instance. Kept intentionally small;
 * it grows one wire per milestone.
 *
 * Takes the application [Context] so telephony/telemetry managers can reach
 * system services without leaking an Activity.
 */
class AgentGraph(context: Context) {
    private val appContext: Context = context.applicationContext

    val logger: LunoLogger = LunoLogger(LogcatSink())
    val agentController: AgentController = AgentController(logger)

    /** Coalesced telemetry state written by the device managers below. */
    val deviceStateStore: DeviceStateStore = DeviceStateStore()
    val simInfoManager: SimInfoManager = SimInfoManager(appContext, deviceStateStore, logger)
    val batteryMonitor: BatteryMonitor = BatteryMonitor(appContext, deviceStateStore, logger)
}
