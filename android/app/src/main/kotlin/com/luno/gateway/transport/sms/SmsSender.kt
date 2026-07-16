package com.luno.gateway.transport.sms

import android.app.PendingIntent
import android.content.Context
import android.os.Build
import android.telephony.SmsManager

/**
 * Thin wrapper over [SmsManager]: resolves the right manager (default or a
 * specific subscription) and hands a message to the radio. Single-part goes
 * through [sendSinglePart] (`sendTextMessage`) and only genuinely long bodies
 * through [sendMultipart] (`sendMultipartTextMessage`) — some RILs choke on a
 * single-segment multipart send. Outcomes arrive asynchronously through the
 * sent/delivery intents; a revoked `SEND_SMS` surfaces here as a [SecurityException].
 */
class SmsSender(private val context: Context) {

    @Suppress("DEPRECATION")
    fun managerFor(subscriptionId: Int?): SmsManager {
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

    fun sendSinglePart(
        manager: SmsManager,
        destination: String,
        body: String,
        sentIntent: PendingIntent,
        deliveryIntent: PendingIntent?,
    ) {
        manager.sendTextMessage(destination, null, body, sentIntent, deliveryIntent)
    }

    /** @param deliveryIntents one per part, or null when no delivery report was requested. */
    fun sendMultipart(
        manager: SmsManager,
        destination: String,
        parts: List<String>,
        sentIntents: List<PendingIntent>,
        deliveryIntents: List<PendingIntent>?,
    ) {
        manager.sendMultipartTextMessage(
            destination,
            null,
            ArrayList(parts),
            ArrayList(sentIntents),
            deliveryIntents?.let { ArrayList(it) },
        )
    }
}
