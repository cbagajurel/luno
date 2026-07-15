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
            // Explicit ACTION_START, or a null intent from a START_STICKY restart.
            else -> enterForeground()
        }
        return START_STICKY
    }

    private fun enterForeground() {
        notification.ensureChannel()
        // The type must match the manifest foregroundServiceType or Android 14+ crashes.
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
        graph.agentController.onServiceStopped()
        logger.i(TAG, "foreground service destroyed")
        super.onDestroy()
    }

    companion object {
        private const val TAG = "GatewayFgs"
        const val ACTION_START = "com.luno.gateway.action.START_AGENT"
        const val ACTION_STOP = "com.luno.gateway.action.STOP_AGENT"

        fun start(context: Context) {
            val intent = Intent(context, GatewayForegroundService::class.java)
                .setAction(ACTION_START)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, GatewayForegroundService::class.java)
                .setAction(ACTION_STOP)
            context.startService(intent)
        }
    }
}
