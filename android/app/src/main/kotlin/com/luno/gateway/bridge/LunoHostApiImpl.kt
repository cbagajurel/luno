package com.luno.gateway.bridge

import com.luno.gateway.bridge.generated.LunoHostApi

/**
 * Native implementation of the Pigeon [LunoHostApi] — the Dart->Kotlin command
 * surface. M2 exposes only [ping]; real telephony/agent commands land here in
 * later milestones.
 *
 * Methods run on the platform thread. Keep them non-blocking; anything that
 * could take time must hand off to the agent/service rather than stall the
 * caller (see the no-jank/ANR requirement in the M2 checklist).
 */
class LunoHostApiImpl : LunoHostApi {
    override fun ping(message: String): String = "$ECHO_PREFIX$message"

    companion object {
        const val ECHO_PREFIX = "Luno-Kotlin echo: "
    }
}
