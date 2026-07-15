package com.luno.gateway.bridge

import com.luno.gateway.bridge.generated.LunoHostApi

/**
 * Native implementation of the Pigeon [LunoHostApi] — the Dart->Kotlin command
 * surface. M2 added [ping]; M3 adds the agent lifecycle commands, which it
 * delegates to an [AgentHost] (the Activity) so this class holds no Android
 * state of its own.
 *
 * Methods run on the platform thread. Keep them non-blocking; anything that
 * could take time must hand off to the agent/service rather than stall the
 * caller (see the no-jank/ANR requirement in the M2 checklist).
 */
class LunoHostApiImpl(private val host: AgentHost) : LunoHostApi {
    override fun ping(message: String): String = "$ECHO_PREFIX$message"

    override fun startAgent() = host.startAgent()

    override fun stopAgent() = host.stopAgent()

    override fun isAgentRunning(): Boolean = host.isAgentRunning()

    override fun requestNotificationPermission() = host.requestNotificationPermission()

    companion object {
        const val ECHO_PREFIX = "Luno-Kotlin echo: "
    }
}
