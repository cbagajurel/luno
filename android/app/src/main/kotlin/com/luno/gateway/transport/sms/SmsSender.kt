package com.luno.gateway.transport.sms

import android.app.PendingIntent
import android.content.Context
import android.os.Build
import android.telephony.SmsManager

/**
 * Thin wrapper over [SmsManager]: resolves the right manager (default or a
 * specific subscription) and hands one single-part message to the radio. The
 * outcome arrives asynchronously through [sentIntent]; this call only starts it.
 * A revoked `SEND_SMS` surfaces here as a [SecurityException].
 */
class SmsSender(private val context: Context) {

    fun send(destination: String, body: String, subscriptionId: Int?, sentIntent: PendingIntent) {
        managerFor(subscriptionId).sendTextMessage(destination, null, body, sentIntent, null)
    }

    @Suppress("DEPRECATION")
    private fun managerFor(subscriptionId: Int?): SmsManager {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = context.getSystemService(SmsManager::class.java)
            return if (subscriptionId != null) manager.createForSubscriptionId(subscriptionId) else manager
        }
        return if (subscriptionId != null) {
            SmsManager.getSmsManagerForSubscriptionId(subscriptionId)
        } else {
            SmsManager.getDefault()
        }
    }
}
