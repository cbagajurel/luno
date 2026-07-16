package com.luno.gateway

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.luno.gateway.agent.GatewayForegroundService
import com.luno.gateway.bridge.AgentHost
import com.luno.gateway.bridge.AgentStateChannel
import com.luno.gateway.bridge.DeviceStateChannel
import com.luno.gateway.bridge.FlutterEventBridge
import com.luno.gateway.bridge.InboxChannel
import com.luno.gateway.bridge.LunoHostApiImpl
import com.luno.gateway.bridge.OutboxChannel
import com.luno.gateway.bridge.generated.LunoHostApi
import com.luno.gateway.di.AgentGraph
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine

class MainActivity : FlutterActivity(), AgentHost {
    private var eventBridge: FlutterEventBridge? = null
    private var agentStateChannel: AgentStateChannel? = null
    private var deviceStateChannel: DeviceStateChannel? = null
    private var outboxChannel: OutboxChannel? = null
    private var inboxChannel: InboxChannel? = null

    private val graph: AgentGraph
        get() = (application as LunoApplication).graph

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        val messenger = flutterEngine.dartExecutor.binaryMessenger
        LunoHostApi.setUp(
            messenger,
            LunoHostApiImpl(
                this,
                graph.deviceStateStore,
                graph.outboxRepository,
                graph.outboxDispatcher,
                graph.outboxPartDao,
                graph.inboxRepository,
            ),
        )
        eventBridge = FlutterEventBridge(messenger).also { it.attach() }
        outboxChannel = OutboxChannel(messenger, graph.outboxRepository).also { it.attach() }
        inboxChannel = InboxChannel(messenger, graph.inboxRepository).also { it.attach() }
        agentStateChannel = AgentStateChannel(messenger, graph.agentController).also { it.attach() }
        deviceStateChannel = DeviceStateChannel(
            messenger,
            graph.deviceStateStore,
            onStart = {
                graph.batteryMonitor.start()
                graph.signalStrengthMonitor.start()
                graph.networkMonitor.start()
                graph.simInfoManager.start()
            },
            onStop = {
                graph.batteryMonitor.stop()
                graph.signalStrengthMonitor.stop()
                graph.networkMonitor.stop()
                graph.simInfoManager.stop()
            },
        ).also { it.attach() }
    }

    override fun cleanUpFlutterEngine(flutterEngine: FlutterEngine) {
        inboxChannel?.detach()
        inboxChannel = null
        outboxChannel?.detach()
        outboxChannel = null
        deviceStateChannel?.detach()
        deviceStateChannel = null
        agentStateChannel?.detach()
        agentStateChannel = null
        eventBridge?.detach()
        eventBridge = null
        LunoHostApi.setUp(flutterEngine.dartExecutor.binaryMessenger, null)
        super.cleanUpFlutterEngine(flutterEngine)
    }

    override fun startAgent() = GatewayForegroundService.start(this)

    override fun stopAgent() = GatewayForegroundService.stop(this)

    override fun isAgentRunning(): Boolean = graph.agentController.isRunning

    override fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (!isGranted(Manifest.permission.POST_NOTIFICATIONS)) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                REQ_POST_NOTIFICATIONS,
            )
        }
    }

    override fun hasPhonePermission(): Boolean = isGranted(Manifest.permission.READ_PHONE_STATE)

    override fun requestPhonePermission() {
        if (!hasPhonePermission()) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.READ_PHONE_STATE),
                REQ_READ_PHONE_STATE,
            )
        }
    }

    override fun hasSmsPermission(): Boolean = isGranted(Manifest.permission.SEND_SMS)

    override fun requestSmsPermission() {
        if (!hasSmsPermission()) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.SEND_SMS),
                REQ_SEND_SMS,
            )
        }
    }

    override fun hasReceiveSmsPermission(): Boolean = isGranted(Manifest.permission.RECEIVE_SMS)

    override fun requestReceiveSmsPermission() {
        if (!hasReceiveSmsPermission()) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECEIVE_SMS),
                REQ_RECEIVE_SMS,
            )
        }
    }

    private fun isGranted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        val granted = grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED
        when (requestCode) {
            REQ_POST_NOTIFICATIONS -> graph.logger.i(TAG, "POST_NOTIFICATIONS granted=$granted")
            REQ_READ_PHONE_STATE -> {
                graph.logger.i(TAG, "READ_PHONE_STATE granted=$granted")
                if (granted) graph.simInfoManager.start()
            }
            REQ_SEND_SMS -> graph.logger.i(TAG, "SEND_SMS granted=$granted")
            REQ_RECEIVE_SMS -> graph.logger.i(TAG, "RECEIVE_SMS granted=$granted")
        }
    }

    companion object {
        private const val TAG = "MainActivity"
        private const val REQ_POST_NOTIFICATIONS = 1001
        private const val REQ_READ_PHONE_STATE = 1002
        private const val REQ_SEND_SMS = 1003
        private const val REQ_RECEIVE_SMS = 1004
    }
}
