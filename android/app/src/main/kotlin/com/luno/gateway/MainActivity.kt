package com.luno.gateway

import com.luno.gateway.bridge.FlutterEventBridge
import com.luno.gateway.bridge.LunoHostApiImpl
import com.luno.gateway.bridge.generated.LunoHostApi
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine

/**
 * Flutter host Activity. Installs the Pigeon [LunoHostApi] implementation and
 * the [FlutterEventBridge] onto each attached engine, and tears them down on
 * detach so Activity recreation (rotation, process reuse) doesn't leak handlers
 * or double-register.
 */
class MainActivity : FlutterActivity() {
    private var eventBridge: FlutterEventBridge? = null

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        val messenger = flutterEngine.dartExecutor.binaryMessenger
        LunoHostApi.setUp(messenger, LunoHostApiImpl())
        eventBridge = FlutterEventBridge(messenger).also { it.attach() }
    }

    override fun cleanUpFlutterEngine(flutterEngine: FlutterEngine) {
        eventBridge?.detach()
        eventBridge = null
        LunoHostApi.setUp(flutterEngine.dartExecutor.binaryMessenger, null)
        super.cleanUpFlutterEngine(flutterEngine)
    }
}
