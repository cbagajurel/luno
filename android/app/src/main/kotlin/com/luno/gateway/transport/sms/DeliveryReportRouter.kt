package com.luno.gateway.transport.sms

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.telephony.SmsMessage
import androidx.core.content.ContextCompat
import com.luno.gateway.logging.LunoLogger
import com.luno.gateway.model.DeliveryReport
import com.luno.gateway.util.Clock
import com.luno.gateway.util.SystemClock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import java.util.concurrent.atomic.AtomicInteger

/**
 * Receives `deliveryIntent` broadcasts and turns each into a [DeliveryReport]
 * correlated back to (messageId, partIndex) carried in the intent extras. Only
 * final outcomes (delivered / permanently failed) are emitted; a "still trying"
 * status is dropped so the tracker keeps waiting (or times out).
 */
class DeliveryReportRouter(
    private val context: Context,
    private val logger: LunoLogger,
    private val clock: Clock = SystemClock,
) {
    private val reports = MutableSharedFlow<DeliveryReport>(extraBufferCapacity = 64)
    private val nextRequestCode = AtomicInteger(1)

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            intent ?: return
            val messageId = intent.getStringExtra(EXTRA_MESSAGE_ID) ?: return
            val partIndex = intent.getIntExtra(EXTRA_PART_INDEX, -1)
            if (partIndex < 0) return
            when (deliveryOutcome(intent)) {
                DeliveryOutcome.DELIVERED ->
                    reports.tryEmit(DeliveryReport(messageId, partIndex, delivered = true, at = clock.nowMillis()))
                DeliveryOutcome.FAILED ->
                    reports.tryEmit(DeliveryReport(messageId, partIndex, delivered = false, at = clock.nowMillis()))
                DeliveryOutcome.PENDING -> Unit
            }
        }
    }

    private fun deliveryOutcome(intent: Intent): DeliveryOutcome {
        val pdu = intent.getByteArrayExtra("pdu") ?: return DeliveryOutcome.DELIVERED
        return runCatching {
            @Suppress("DEPRECATION")
            val message = SmsMessage.createFromPdu(pdu, intent.getStringExtra("format"))
            SmsDeliveryStatus.classify(message.status)
        }.getOrElse {
            logger.w(TAG, "delivery PDU parse failed: ${it.message}")
            DeliveryOutcome.DELIVERED
        }
    }

    fun register() {
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(ACTION_SMS_DELIVERED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        logger.i(TAG, "delivery-report receiver registered")
    }

    fun unregister() {
        runCatching { context.unregisterReceiver(receiver) }
    }

    fun deliveryIntentFor(messageId: String, partIndex: Int): PendingIntent {
        val intent = Intent(ACTION_SMS_DELIVERED)
            .setPackage(context.packageName)
            .putExtra(EXTRA_MESSAGE_ID, messageId)
            .putExtra(EXTRA_PART_INDEX, partIndex)
        return PendingIntent.getBroadcast(
            context,
            nextRequestCode.getAndIncrement(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    fun reports(): Flow<DeliveryReport> = reports

    companion object {
        private const val TAG = "DeliveryReportRouter"
        private const val ACTION_SMS_DELIVERED = "com.luno.gateway.action.SMS_DELIVERED"
        private const val EXTRA_MESSAGE_ID = "message_id"
        private const val EXTRA_PART_INDEX = "part_index"
    }
}
