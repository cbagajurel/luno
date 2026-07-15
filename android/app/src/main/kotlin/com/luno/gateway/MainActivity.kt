package com.luno.gateway

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.luno.gateway.agent.GatewayForegroundService
import com.luno.gateway.bridge.AgentHost
import com.luno.gateway.bridge.AgentStateChannel
import com.luno.gateway.bridge.FlutterEventBridge
import com.luno.gateway.bridge.LunoHostApiImpl
import com.luno.gateway.bridge.generated.LunoHostApi
import com.luno.gateway.di.AgentGraph
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine

/**
 * Flutter host Activity. Installs the Pigeon [LunoHostApi] implementation, the
 * M2 tick [FlutterEventBridge], and the M3 [AgentStateChannel] onto each attached
 * engine, tearing them down on detach so Activity recreation doesn't leak
 * handlers or double-register.
 *
 * It also implements [AgentHost]: the HostApi delegates service start/stop and
 * the notification-permission request here because those need a Context/Activity.
 */
class MainActivity : FlutterActivity(), AgentHost {
    private var eventBridge: FlutterEventBridge? = null
    private var agentStateChannel: AgentStateChannel? = null

    private val graph: AgentGraph
        get() = (application as LunoApplication).graph

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        val messenger = flutterEngine.dartExecutor.binaryMessenger
        LunoHostApi.setUp(messenger, LunoHostApiImpl(this))
        eventBridge = FlutterEventBridge(messenger).also { it.attach() }
        agentStateChannel = AgentStateChannel(messenger, graph.agentController).also { it.attach() }
    }

    override fun cleanUpFlutterEngine(flutterEngine: FlutterEngine) {
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
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                REQ_POST_NOTIFICATIONS,
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_POST_NOTIFICATIONS) {
            val granted = grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED
            graph.logger.i(TAG, "POST_NOTIFICATIONS granted=$granted")
        }
    }

    companion object {
        private const val TAG = "MainActivity"
        private const val REQ_POST_NOTIFICATIONS = 1001
    }
}
