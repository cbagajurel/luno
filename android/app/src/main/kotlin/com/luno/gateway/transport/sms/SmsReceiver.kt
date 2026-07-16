package com.luno.gateway.transport.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.luno.gateway.LunoApplication
import com.luno.gateway.model.InboundMessage
import com.luno.gateway.util.Ids
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Manifest-registered receiver for inbound SMS. A concatenated message arrives as
 * its ordered segments in one broadcast, so we reassemble them, persist to the
 * inbox before anything else acts (persist-before-act), and dedupe on
 * sender+timestamp. Heavy work is moved off the broadcast thread via [goAsync]
 * with a timeout so we never ANR. Reporting to the backend happens in M14.
 */
class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        if (messages.isEmpty()) return

        val inbound = buildInbound(
            sender = messages.first().originatingAddress,
            bodies = messages.map { it.messageBody ?: "" },
            receivedAt = messages.first().timestampMillis,
            subscriptionId = subscriptionId(intent),
        )

        val graph = (context.applicationContext as LunoApplication).graph
        val pending = goAsync()
        graph.appScope.launch {
            try {
                withTimeoutOrNull(CAPTURE_TIMEOUT_MILLIS) {
                    graph.inboxRepository.capture(inbound)
                }
                graph.logger.i(TAG, "captured inbound ${inbound.id} (${inbound.parts} part(s))")
            } finally {
                pending.finish()
            }
        }
    }

    private fun subscriptionId(intent: Intent): Int? =
        intent.getIntExtra("subscription", -1).takeIf { it >= 0 }

    companion object {
        private const val TAG = "SmsReceiver"
        private const val CAPTURE_TIMEOUT_MILLIS = 8_000L

        /** Pure inbound assembly + dedupe-key derivation, extracted so it is testable off-device. */
        fun buildInbound(
            sender: String?,
            bodies: List<String>,
            receivedAt: Long,
            subscriptionId: Int?,
        ): InboundMessage {
            val from = sender ?: "unknown"
            val body = MultipartAssembler.reassemble(bodies)
            return InboundMessage(
                id = Ids.inboundKey(from, receivedAt),
                sender = from,
                body = body,
                subscriptionId = subscriptionId,
                receivedAt = receivedAt,
                parts = bodies.size,
            )
        }
    }
}
