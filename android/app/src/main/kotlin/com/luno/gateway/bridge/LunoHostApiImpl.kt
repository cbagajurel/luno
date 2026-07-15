package com.luno.gateway.bridge

import com.luno.gateway.bridge.generated.LunoHostApi
import com.luno.gateway.telephony.SimInfoManager
import com.luno.gateway.bridge.generated.SimInfo as SimInfoDto

/**
 * Native implementation of the Pigeon [LunoHostApi] — the Dart->Kotlin command
 * surface. M2 added [ping]; M3 added the agent lifecycle commands; M4 adds the
 * SIM query. Permission-gated / Activity-bound work is delegated to [AgentHost]
 * (the Activity); telemetry reads come from the [SimInfoManager]. This class
 * holds no Android state of its own.
 *
 * Methods run on the platform thread. Keep them non-blocking; anything that
 * could take time must hand off to the agent/service rather than stall the
 * caller (see the no-jank/ANR requirement in the M2 checklist).
 */
class LunoHostApiImpl(
    private val host: AgentHost,
    private val simInfoManager: SimInfoManager,
) : LunoHostApi {
    override fun ping(message: String): String = "$ECHO_PREFIX$message"

    override fun startAgent() = host.startAgent()

    override fun stopAgent() = host.stopAgent()

    override fun isAgentRunning(): Boolean = host.isAgentRunning()

    override fun requestNotificationPermission() = host.requestNotificationPermission()

    override fun getSimInfo(): List<SimInfoDto> =
        simInfoManager.snapshot().sims.map { it.toDto() }

    override fun hasPhonePermission(): Boolean = host.hasPhonePermission()

    override fun requestPhonePermission() = host.requestPhonePermission()

    companion object {
        const val ECHO_PREFIX = "Luno-Kotlin echo: "
    }
}
