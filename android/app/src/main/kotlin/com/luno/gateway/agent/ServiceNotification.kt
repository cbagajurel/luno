package com.luno.gateway.agent

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.luno.gateway.MainActivity
import com.luno.gateway.R

/**
 * Owns the persistent, low-importance notification that keeps the gateway a
 * foreground service. Low importance (no sound, no peek, badge suppressed) is
 * deliberate: this is an always-on utility, not an alert.
 *
 * The channel exists from minSdk 26 upward, so no version guard is needed.
 * Tapping the notification reopens the dashboard.
 */
class ServiceNotification(private val context: Context) {

    /** Creates the notification channel if it does not already exist. */
    fun ensureChannel() {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.luno_notification_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = context.getString(R.string.luno_notification_channel_desc)
                setShowBadge(false)
            }
            manager.createNotificationChannel(channel)
        }
    }

    /** Builds the ongoing notification shown while the agent runs. */
    fun build(): Notification {
        val open = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
        }
        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            open,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(context.getString(R.string.luno_notification_title))
            .setContentText(context.getString(R.string.luno_notification_text))
            .setSmallIcon(R.drawable.ic_stat_luno)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    companion object {
        const val CHANNEL_ID = "luno_gateway_status"
        const val NOTIFICATION_ID = 1001
    }
}
