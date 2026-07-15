package com.luno.gateway.telephony

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import com.luno.gateway.logging.LunoLogger
import com.luno.gateway.model.BatteryStatus

/**
 * Tracks battery level, charging state, power source, and health (M5). Requires
 * no permission. Reads are event-driven off the sticky `ACTION_BATTERY_CHANGED`
 * broadcast — never polled — and results are written into the shared
 * [DeviceStateStore], where equality-based change detection debounces the
 * naturally chatty broadcast.
 *
 * The receiver runs on the main thread by default, matching the other telemetry
 * writers into the store.
 */
class BatteryMonitor(
    private val context: Context,
    private val store: DeviceStateStore,
    private val logger: LunoLogger,
) {
    private val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = publish(intent)
    }

    private var registered = false

    /** Registers for battery changes and publishes the current sticky value. */
    fun start() {
        if (registered) {
            publish(context.registerReceiver(null, filter))
            return
        }
        // Registering returns the current sticky Intent, giving an immediate snapshot.
        val sticky = context.registerReceiver(receiver, filter)
        registered = true
        publish(sticky)
        logger.i(TAG, "battery monitoring started")
    }

    /** Unregisters the receiver. Idempotent. */
    fun stop() {
        if (!registered) return
        context.unregisterReceiver(receiver)
        registered = false
    }

    private fun publish(intent: Intent?) {
        store.updateBattery(intent?.let { parse(it) })
    }

    private fun parse(intent: Intent): BatteryStatus {
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val percent = if (level >= 0 && scale > 0) level * 100 / scale else -1

        val status = intent.getIntExtra(
            BatteryManager.EXTRA_STATUS,
            BatteryManager.BATTERY_STATUS_UNKNOWN,
        )
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL

        return BatteryStatus(
            levelPercent = percent,
            isCharging = isCharging,
            plugged = pluggedName(intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)),
            health = healthName(
                intent.getIntExtra(
                    BatteryManager.EXTRA_HEALTH,
                    BatteryManager.BATTERY_HEALTH_UNKNOWN,
                ),
            ),
        )
    }

    private fun pluggedName(plugged: Int): String = when (plugged) {
        0 -> "NONE"
        BatteryManager.BATTERY_PLUGGED_AC -> "AC"
        BatteryManager.BATTERY_PLUGGED_USB -> "USB"
        BatteryManager.BATTERY_PLUGGED_WIRELESS -> "WIRELESS"
        BatteryManager.BATTERY_PLUGGED_DOCK -> "DOCK"
        else -> "UNKNOWN"
    }

    private fun healthName(health: Int): String = when (health) {
        BatteryManager.BATTERY_HEALTH_GOOD -> "GOOD"
        BatteryManager.BATTERY_HEALTH_OVERHEAT -> "OVERHEAT"
        BatteryManager.BATTERY_HEALTH_DEAD -> "DEAD"
        BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "OVER_VOLTAGE"
        BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "FAILURE"
        BatteryManager.BATTERY_HEALTH_COLD -> "COLD"
        else -> "UNKNOWN"
    }

    companion object {
        private const val TAG = "BatteryMonitor"
    }
}
