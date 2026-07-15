package com.luno.gateway.agent

import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import com.luno.gateway.LunoApplication
import com.luno.gateway.di.AgentGraph
import com.luno.gateway.logging.LunoLogger

/**
 * The 24/7 process the whole agent lives inside. For M3 it does nothing but
 * enter the foreground and stay alive — that is the milestone. Telephony,
 * backend, and queue draining hang off [AgentController] in later milestones.
 *
 * Declared as a `specialUse` foreground service (locked decision, plan.md): a
 * persistent SMS gateway fits none of the narrow standard FGS types. On Android
 * 14+ an FGS started without a matching declared type crashes, so the type
 * passed here must equal the manifest `foregroundServiceType`.
 *
 * Restart semantics: [onStartCommand] returns START_STICKY, so after a system
 * kill the OS recreates the service with a null intent; the `else` branch treats
 * that as "start", re-entering the foreground.
 */
class GatewayForegroundService : LifecycleService() {

    private lateinit var graph: AgentGraph
    private lateinit var logger: LunoLogger
    private lateinit var notification: ServiceNotification

    override fun onCreate() {
        super.onCreate()
        graph = (application as LunoApplication).graph
        logger = graph.logger
        notification = ServiceNotification(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_STOP -> {
                logger.i(TAG, "stop requested")
                shutdown()
                return START_NOT_STICKY
            }
            else -> {
                // Explicit ACTION_START, or a null intent from a START_STICKY restart.
                enterForeground()
            }
        }
        return START_STICKY
    }

    private fun enterForeground() {
        notification.ensureChannel()
        ServiceCompat.startForeground(
            this,
            ServiceNotification.NOTIFICATION_ID,
            notification.build(),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            } else {
                0
            },
        )
        graph.agentController.onServiceStarted()
        logger.i(TAG, "foreground service started")
    }

    private fun shutdown() {
        graph.agentController.onServiceStopped()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        // Covers OS-initiated teardown too, so the UI never shows a stale RUNNING.
        graph.agentController.onServiceStopped()
        logger.i(TAG, "foreground service destroyed")
        super.onDestroy()
    }

    companion object {
        private const val TAG = "GatewayFgs"
        const val ACTION_START = "com.luno.gateway.action.START_AGENT"
        const val ACTION_STOP = "com.luno.gateway.action.STOP_AGENT"

        /** Starts the agent from a user-initiated context (allowed FGS start). */
        fun start(context: Context) {
            val intent = Intent(context, GatewayForegroundService::class.java)
                .setAction(ACTION_START)
            ContextCompat.startForegroundService(context, intent)
        }

        /** Requests the agent stop and leave the foreground. */
        fun stop(context: Context) {
            val intent = Intent(context, GatewayForegroundService::class.java)
                .setAction(ACTION_STOP)
            context.startService(intent)
        }
    }
}
