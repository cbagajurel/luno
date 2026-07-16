package com.luno.gateway.transport.sms

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import com.luno.gateway.logging.LunoLogger
import com.luno.gateway.util.Ids
import kotlinx.coroutines.CompletableDeferred
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Correlates each outgoing send with its `sentIntent` broadcast. Every send is
 * armed with a unique request id (carried in the PendingIntent's extras) and a
 * [CompletableDeferred] the caller awaits; the receiver reads the result code
 * and completes the matching deferred. A distinct request code per send keeps
 * the PendingIntents distinct (extras are not part of PendingIntent identity).
 */
class SentReportRouter(
    private val context: Context,
    private val logger: LunoLogger,
) {
    class Armed(val requestId: String, val sentIntent: PendingIntent)

    private val pending = ConcurrentHashMap<String, CompletableDeferred<SmsSendResult>>()
    private val nextRequestCode = AtomicInteger(1)

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val requestId = intent?.getStringExtra(EXTRA_REQUEST_ID) ?: return
            val result = SmsResultCodes.fromSentResultCode(resultCode)
            logger.i(TAG, "sent report: code=$resultCode (${SmsResultCodes.name(resultCode)})")
            pending.remove(requestId)?.complete(result)
        }
    }

    fun register() {
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(ACTION_SMS_SENT),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        logger.i(TAG, "sent-report receiver registered")
    }

    fun unregister() {
        runCatching { context.unregisterReceiver(receiver) }
        pending.values.forEach { it.cancel() }
        pending.clear()
    }

    fun arm(deferred: CompletableDeferred<SmsSendResult>): Armed {
        val requestId = Ids.newId()
        pending[requestId] = deferred
        val intent = Intent(ACTION_SMS_SENT)
            .setPackage(context.packageName)
            .putExtra(EXTRA_REQUEST_ID, requestId)
        val sentIntent = PendingIntent.getBroadcast(
            context,
            nextRequestCode.getAndIncrement(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return Armed(requestId, sentIntent)
    }

    fun disarm(requestId: String) {
        pending.remove(requestId)
    }

    companion object {
        private const val TAG = "SentReportRouter"
        private const val ACTION_SMS_SENT = "com.luno.gateway.action.SMS_SENT"
        private const val EXTRA_REQUEST_ID = "request_id"
    }
}
