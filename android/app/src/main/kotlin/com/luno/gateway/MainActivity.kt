package com.luno.gateway

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import com.luno.gateway.agent.GatewayForegroundService
import com.luno.gateway.bridge.AgentHost
import com.luno.gateway.bridge.AgentStateChannel
import com.luno.gateway.bridge.ConnectionStateChannel
import com.luno.gateway.bridge.DeviceStateChannel
import com.luno.gateway.bridge.FlutterEventBridge
import com.luno.gateway.bridge.InboxChannel
import com.luno.gateway.bridge.LogChannel
import com.luno.gateway.bridge.LunoHostApiImpl
import com.luno.gateway.bridge.OutboxChannel
import com.luno.gateway.bridge.generated.LunoHostApi
import com.luno.gateway.bridge.generated.PermissionStatus
import com.luno.gateway.di.AgentGraph
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine

class MainActivity : FlutterActivity(), AgentHost {
    private var eventBridge: FlutterEventBridge? = null
    private var agentStateChannel: AgentStateChannel? = null
    private var deviceStateChannel: DeviceStateChannel? = null
    private var outboxChannel: OutboxChannel? = null
    private var inboxChannel: InboxChannel? = null
    private var connectionStateChannel: ConnectionStateChannel? = null
    private var logChannel: LogChannel? = null

    private val pendingRequests = mutableMapOf<Int, (PermissionStatus) -> Unit>()

    private val permissionPrefs by lazy {
        getSharedPreferences(PERMISSION_PREFS, Context.MODE_PRIVATE)
    }

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
                graph.pairingManager,
                graph.connectionManager,
                graph.logBuffer,
                graph.appScope,
            ),
        )
        eventBridge = FlutterEventBridge(messenger).also { it.attach() }
        outboxChannel = OutboxChannel(messenger, graph.outboxRepository).also { it.attach() }
        inboxChannel = InboxChannel(messenger, graph.inboxRepository).also { it.attach() }
        agentStateChannel = AgentStateChannel(messenger, graph.agentController).also { it.attach() }
        connectionStateChannel =
            ConnectionStateChannel(messenger, graph.connectionManager).also { it.attach() }
        logChannel = LogChannel(messenger, graph.logBuffer).also { it.attach() }
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
        logChannel?.detach()
        logChannel = null
        connectionStateChannel?.detach()
        connectionStateChannel = null
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

    override fun phonePermissionStatus(): PermissionStatus =
        statusOf(Manifest.permission.READ_PHONE_STATE)

    override fun requestPhonePermission(onResult: (PermissionStatus) -> Unit) {
        request(Manifest.permission.READ_PHONE_STATE, REQ_READ_PHONE_STATE, onResult)
    }

    override fun smsPermissionStatus(): PermissionStatus = statusOf(Manifest.permission.SEND_SMS)

    override fun requestSmsPermission(onResult: (PermissionStatus) -> Unit) {
        request(Manifest.permission.SEND_SMS, REQ_SEND_SMS, onResult)
    }

    override fun isReceiveSmsSupported(): Boolean = BuildConfig.RECEIVE_SMS_ENABLED

    override fun receiveSmsPermissionStatus(): PermissionStatus =
        if (!BuildConfig.RECEIVE_SMS_ENABLED) {
            PermissionStatus.BLOCKED
        } else {
            statusOf(Manifest.permission.RECEIVE_SMS)
        }

    override fun requestReceiveSmsPermission(onResult: (PermissionStatus) -> Unit) {
        // Undeclared in sendOnly builds, so the system would deny it outright.
        if (!BuildConfig.RECEIVE_SMS_ENABLED) {
            onResult(PermissionStatus.BLOCKED)
            return
        }
        request(Manifest.permission.RECEIVE_SMS, REQ_RECEIVE_SMS, onResult)
    }

    override fun openAppSettings() {
        val intent =
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", packageName, null),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { startActivity(intent) }
            .onFailure { graph.logger.w(TAG, "cannot open app settings", it) }
    }

    private fun isGranted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    /**
     * BLOCKED is a *hint*, never a verdict: Android reports "denied without rationale"
     * both for a restricted-settings block and for "Don't ask again", and the flag does
     * not clear once the user allows restricted settings. So treat it as "the last
     * attempt could not prompt" and always let [request] re-check with the real system
     * call — the persisted [hasRequested] flag only separates this from the untouched
     * state before the very first request.
     */
    private fun statusOf(permission: String): PermissionStatus =
        when {
            isGranted(permission) -> PermissionStatus.GRANTED
            !hasRequested(permission) -> PermissionStatus.DENIED
            ActivityCompat.shouldShowRequestPermissionRationale(this, permission) ->
                PermissionStatus.DENIED
            else -> PermissionStatus.BLOCKED
        }

    private fun request(
        permission: String,
        requestCode: Int,
        onResult: (PermissionStatus) -> Unit,
    ) {
        if (isGranted(permission)) {
            onResult(PermissionStatus.GRANTED)
            return
        }
        // Always ask the system, even when the cached status says BLOCKED. Allowing
        // restricted settings makes the permission grantable again without changing
        // anything we can observe, so short-circuiting here would strand the user on
        // "Fix in Settings" forever. A genuinely blocked request just returns denied
        // immediately, which onRequestPermissionsResult re-derives as BLOCKED.
        pendingRequests[requestCode] = onResult
        markRequested(permission)
        ActivityCompat.requestPermissions(this, arrayOf(permission), requestCode)
    }

    private fun hasRequested(permission: String): Boolean =
        permissionPrefs.getBoolean(permission, false)

    private fun markRequested(permission: String) {
        permissionPrefs.edit { putBoolean(permission, true) }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        val granted = grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED
        val permission = permissions.firstOrNull()
        val status =
            when {
                granted -> PermissionStatus.GRANTED
                permission != null -> statusOf(permission)
                else -> PermissionStatus.DENIED
            }

        when (requestCode) {
            REQ_POST_NOTIFICATIONS -> graph.logger.i(TAG, "POST_NOTIFICATIONS granted=$granted")
            REQ_READ_PHONE_STATE -> {
                graph.logger.i(TAG, "READ_PHONE_STATE granted=$granted")
                if (granted) graph.simInfoManager.start()
            }
            REQ_SEND_SMS -> graph.logger.i(TAG, "SEND_SMS status=$status")
            REQ_RECEIVE_SMS -> graph.logger.i(TAG, "RECEIVE_SMS status=$status")
        }
        pendingRequests.remove(requestCode)?.invoke(status)
    }

    companion object {
        private const val TAG = "MainActivity"
        private const val REQ_POST_NOTIFICATIONS = 1001
        private const val REQ_READ_PHONE_STATE = 1002
        private const val REQ_SEND_SMS = 1003
        private const val REQ_RECEIVE_SMS = 1004
        private const val PERMISSION_PREFS = "luno_permission_requests"
    }
}
