package com.luno.gateway.telephony

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SubscriptionInfo
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import com.luno.gateway.logging.LunoLogger
import com.luno.gateway.model.SimInfo

/**
 * Reads active SIM subscriptions and tracks insert/remove live. This is the
 * first "native manager → domain model → stream to UI" implementation; M5–M7
 * (battery, signal, network) follow the same shape (docs/milestones.md Phase 2).
 * It writes its slice into the shared [DeviceStateStore]; the store owns the
 * coalesced stream.
 *
 * Permission: [SubscriptionManager.getActiveSubscriptionInfoList] and the change
 * listener require the dangerous `READ_PHONE_STATE`. Every read is guarded — a
 * missing permission (or no SIM) yields an empty list, never a crash or a
 * SecurityException escaping.
 *
 * Threading: [start]/[stop] must be called on the main thread. On API < 30 the
 * legacy listener binds to the calling thread's Looper; the main thread always
 * has one. On API 30+ we pass the main executor explicitly.
 *
 * The change listener fires once immediately on registration, so [start]
 * naturally produces the first snapshot.
 */
class SimInfoManager(
    private val context: Context,
    private val store: DeviceStateStore,
    private val logger: LunoLogger,
) {
    private val subscriptionManager: SubscriptionManager =
        context.getSystemService(SubscriptionManager::class.java)
    private val telephonyManager: TelephonyManager =
        context.getSystemService(TelephonyManager::class.java)

    private var listener: SubscriptionManager.OnSubscriptionsChangedListener? = null

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * Begins monitoring subscription changes. Idempotent and safe to call
     * without permission (it no-ops and logs). Call again after the permission
     * is granted to actually register.
     */
    fun start() {
        if (!hasPermission()) {
            logger.w(TAG, "READ_PHONE_STATE not granted; SIM info unavailable")
            refresh() // publishes empty so the UI can render the "no permission" state
            return
        }
        if (listener != null) {
            refresh()
            return
        }
        val newListener = object : SubscriptionManager.OnSubscriptionsChangedListener() {
            override fun onSubscriptionsChanged() = refresh()
        }
        listener = newListener
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            subscriptionManager.addOnSubscriptionsChangedListener(
                ContextCompat.getMainExecutor(context),
                newListener,
            )
        } else {
            @Suppress("DEPRECATION")
            subscriptionManager.addOnSubscriptionsChangedListener(newListener)
        }
        logger.i(TAG, "SIM monitoring started")
    }

    /** Stops monitoring. Idempotent. */
    fun stop() {
        listener?.let { subscriptionManager.removeOnSubscriptionsChangedListener(it) }
        listener = null
    }

    private fun refresh() {
        store.updateSims(readSims())
    }

    private fun readSims(): List<SimInfo> {
        if (!hasPermission()) return emptyList()
        return try {
            val active: List<SubscriptionInfo> =
                subscriptionManager.activeSubscriptionInfoList ?: emptyList()
            active.map { it.toDomain() }
        } catch (e: SecurityException) {
            // Restricted fields on Android 10+ / OEM quirks can still throw.
            logger.w(TAG, "SecurityException reading subscriptions", e)
            emptyList()
        }
    }

    private fun SubscriptionInfo.toDomain(): SimInfo = SimInfo(
        subscriptionId = subscriptionId,
        slotIndex = simSlotIndex,
        carrierName = carrierName?.toString().orEmpty(),
        displayName = displayName?.toString().orEmpty(),
        isEmbedded = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) isEmbedded else false,
        simState = simStateName(subscriptionId),
    )

    private fun simStateName(subscriptionId: Int): String {
        val state = try {
            telephonyManager.createForSubscriptionId(subscriptionId).simState
        } catch (e: SecurityException) {
            TelephonyManager.SIM_STATE_UNKNOWN
        }
        return when (state) {
            TelephonyManager.SIM_STATE_READY -> "READY"
            TelephonyManager.SIM_STATE_ABSENT -> "ABSENT"
            TelephonyManager.SIM_STATE_PIN_REQUIRED -> "PIN_REQUIRED"
            TelephonyManager.SIM_STATE_PUK_REQUIRED -> "PUK_REQUIRED"
            TelephonyManager.SIM_STATE_NETWORK_LOCKED -> "NETWORK_LOCKED"
            TelephonyManager.SIM_STATE_NOT_READY -> "NOT_READY"
            TelephonyManager.SIM_STATE_PERM_DISABLED -> "PERM_DISABLED"
            TelephonyManager.SIM_STATE_CARD_IO_ERROR -> "CARD_IO_ERROR"
            TelephonyManager.SIM_STATE_CARD_RESTRICTED -> "CARD_RESTRICTED"
            else -> "UNKNOWN"
        }
    }

    companion object {
        private const val TAG = "SimInfoManager"
    }
}
