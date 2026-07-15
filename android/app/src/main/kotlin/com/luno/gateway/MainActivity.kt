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
import com.luno.gateway.bridge.LunoHostApiImpl
import com.luno.gateway.bridge.generated.LunoHostApi
import com.luno.gateway.di.AgentGraph
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine

/**
 * Flutter host Activity. Installs the Pigeon [LunoHostApi] implementation, the
 * M2 tick [FlutterEventBridge], the M3 [AgentStateChannel], and the M4/M5
 * coalesced [DeviceStateChannel] onto each attached engine, tearing them down on
 * detach so Activity recreation doesn't leak handlers or double-register.
 *
 * It also implements [AgentHost]: the HostApi delegates service start/stop and
 * the runtime-permission requests here because those need a Context/Activity.
 */
class MainActivity : FlutterActivity(), AgentHost {
    private var eventBridge: FlutterEventBridge? = null
    private var agentStateChannel: AgentStateChannel? = null
    private var deviceStateChannel: DeviceStateChannel? = null

    private val graph: AgentGraph
        get() = (application as LunoApplication).graph

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        val messenger = flutterEngine.dartExecutor.binaryMessenger
        LunoHostApi.setUp(messenger, LunoHostApiImpl(this, graph.deviceStateStore))
        eventBridge = FlutterEventBridge(messenger).also { it.attach() }
        agentStateChannel = AgentStateChannel(messenger, graph.agentController).also { it.attach() }
        deviceStateChannel = DeviceStateChannel(
            messenger,
            graph.deviceStateStore,
            onStart = {
                // Battery needs no permission; SIM monitoring no-ops until granted.
                graph.batteryMonitor.start()
                graph.simInfoManager.start()
            },
            onStop = {
                graph.batteryMonitor.stop()
                graph.simInfoManager.stop()
            },
        ).also { it.attach() }
    }

    override fun cleanUpFlutterEngine(flutterEngine: FlutterEngine) {
        deviceStateChannel?.detach()
        deviceStateChannel = null
        agentStateChannel?.detach()
        agentStateChannel = null
        eventBridge?.detach()
        eventBridge = null
        LunoHostApi.setUp(flutterEngine.dartExecutor.binaryMessenger, null)
        super.cleanUpFlutterEngine(flutterEngine)
    }

    // --- AgentHost ---------------------------------------------------------

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
                // Registering the listener now emits the first SIM snapshot,
                // which pushes the sim-change signal so the UI re-queries.
                if (granted) graph.simInfoManager.start()
            }
        }
    }

    companion object {
        private const val TAG = "MainActivity"
        private const val REQ_POST_NOTIFICATIONS = 1001
        private const val REQ_READ_PHONE_STATE = 1002
    }
}
