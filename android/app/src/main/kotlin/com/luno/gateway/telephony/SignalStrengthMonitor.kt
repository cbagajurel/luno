// PhoneStateListener is deprecated in favour of TelephonyCallback (API 31+), but this
// file deliberately keeps it as the pre-31 fallback, so the deprecation is suppressed
// file-wide rather than at each of its use sites.
@file:Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")

package com.luno.gateway.telephony

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.PhoneStateListener
import android.telephony.SignalStrength
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import com.luno.gateway.logging.LunoLogger
import com.luno.gateway.model.SignalInfo
import com.luno.gateway.model.SimInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class SignalStrengthMonitor(
    private val context: Context,
    private val store: DeviceStateStore,
    private val logger: LunoLogger,
) {
    private val telephonyManager: TelephonyManager =
        context.getSystemService(TelephonyManager::class.java)

    private var scope: CoroutineScope? = null
    private val registrations = mutableMapOf<Int, Registration>()

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) ==
            PackageManager.PERMISSION_GRANTED

    // Tracks the active SIM set from the store and (un)registers one signal
    // callback per subscription. Runs the collector regardless of permission;
    // registration itself is permission-gated, so a later grant re-syncs once
    // SimInfoManager repopulates the SIM set.
    fun start() {
        if (scope != null) return
        val s = CoroutineScope(Dispatchers.Main.immediate)
        scope = s
        s.launch {
            store.state
                .map { state -> state.sims.map(SimInfo::subscriptionId).toSet() }
                .distinctUntilChanged()
                .collect { subIds -> sync(subIds) }
        }
    }

    fun stop() {
        scope?.cancel()
        scope = null
        registrations.keys.toList().forEach { unregister(it) }
        registrations.clear()
    }

    private fun sync(subIds: Set<Int>) {
        (registrations.keys - subIds).toList().forEach { unregister(it) }
        if (hasPermission()) {
            (subIds - registrations.keys).forEach { register(it) }
        }
        store.retainSignals(subIds)
    }

    private fun register(subId: Int) {
        val tm = telephonyManager.createForSubscriptionId(subId)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val callback = ModernCallback(subId)
                tm.registerTelephonyCallback(ContextCompat.getMainExecutor(context), callback)
                registrations[subId] = Registration.Modern(tm, callback)
            } else {
                val listener = LegacyListener(subId)
                @Suppress("DEPRECATION")
                tm.listen(listener, PhoneStateListener.LISTEN_SIGNAL_STRENGTHS)
                registrations[subId] = Registration.Legacy(tm, listener)
            }
        } catch (e: SecurityException) {
            logger.w(TAG, "cannot register signal callback for sub $subId", e)
        }
    }

    private fun unregister(subId: Int) {
        when (val reg = registrations.remove(subId)) {
            is Registration.Modern ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    reg.tm.unregisterTelephonyCallback(reg.callback)
                }
            is Registration.Legacy -> {
                @Suppress("DEPRECATION")
                reg.tm.listen(reg.listener, PhoneStateListener.LISTEN_NONE)
            }
            null -> {}
        }
    }

    private fun publish(subId: Int, signalStrength: SignalStrength) {
        store.updateSignal(signalStrength.toDomain(subId))
    }

    private fun SignalStrength.toDomain(subId: Int): SignalInfo {
        val dbm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            cellSignalStrengths.firstOrNull()?.dbm?.takeIf { it != Int.MAX_VALUE }
        } else {
            null
        }
        return SignalInfo(subscriptionId = subId, dbm = dbm, level = level)
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private inner class ModernCallback(private val subId: Int) :
        TelephonyCallback(), TelephonyCallback.SignalStrengthsListener {
        override fun onSignalStrengthsChanged(signalStrength: SignalStrength) =
            publish(subId, signalStrength)
    }

    private inner class LegacyListener(private val subId: Int) : PhoneStateListener() {
        override fun onSignalStrengthsChanged(signalStrength: SignalStrength) =
            publish(subId, signalStrength)
    }

    private sealed interface Registration {
        @RequiresApi(Build.VERSION_CODES.S)
        class Modern(val tm: TelephonyManager, val callback: TelephonyCallback) : Registration

        class Legacy(val tm: TelephonyManager, val listener: PhoneStateListener) : Registration
    }

    companion object {
        private const val TAG = "SignalStrengthMonitor"
    }
}
