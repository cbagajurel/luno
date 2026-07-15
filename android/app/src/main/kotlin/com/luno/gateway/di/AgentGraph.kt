package com.luno.gateway.di

import com.luno.gateway.agent.AgentController
import com.luno.gateway.logging.LogcatSink
import com.luno.gateway.logging.LunoLogger

/**
 * The single composition root for the native agent — manual DI by decision
 * (plan.md §Decisions #6). Everything long-lived is constructed here once and
 * shared, so dependencies are explicit and the graph is trivially testable.
 *
 * Built by [com.luno.gateway.LunoApplication] and reachable from any component
 * (service, Activity) via the application instance. Kept intentionally small;
 * it grows one wire per milestone.
 */
class AgentGraph {
    val logger: LunoLogger = LunoLogger(LogcatSink())
    val agentController: AgentController = AgentController(logger)
}
