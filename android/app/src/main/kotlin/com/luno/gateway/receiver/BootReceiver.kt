package com.luno.gateway.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.luno.gateway.LunoApplication
import com.luno.gateway.agent.GatewayForegroundService
import com.luno.gateway.work.AgentWatchdogWorker

/**
 * Auto-starts the gateway after a reboot (§4). `BOOT_COMPLETED` is one of the few
 * broadcasts allowed to start a foreground service from the background, so a paired
 * node comes back unattended. Unpaired nodes stay silent — there's nothing to run.
 *
 * Best-effort by nature: `BOOT_COMPLETED` is *not* delivered to force-stopped or
 * never-launched apps and some OEMs withhold it, which is exactly why the
 * [AgentWatchdogWorker] backstop exists (pitfalls: "design for recovery").
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in BOOT_ACTIONS) return
        val graph = (context.applicationContext as LunoApplication).graph
        val paired = graph.credentialStore.load() != null
        graph.logger.i(TAG, "boot received (action=${intent.action}, paired=$paired)")
        if (!shouldAutoStart(paired)) return
        GatewayForegroundService.start(context)
        AgentWatchdogWorker.schedule(context)
    }

    companion object {
        private const val TAG = "BootReceiver"

        private val BOOT_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            // Some OEMs (HTC and others) fire this instead of/alongside BOOT_COMPLETED.
            "android.intent.action.QUICKBOOT_POWERON",
        )

        fun shouldAutoStart(paired: Boolean): Boolean = paired
    }
}
