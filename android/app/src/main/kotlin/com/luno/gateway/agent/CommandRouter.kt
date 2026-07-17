package com.luno.gateway.agent

import com.luno.gateway.backend.protocol.Command
import com.luno.gateway.backend.protocol.Event
import com.luno.gateway.backend.protocol.FrameBody
import com.luno.gateway.backend.protocol.ProtocolFrame
import com.luno.gateway.backend.ws.EventPublisher
import com.luno.gateway.backend.ws.EventSink
import com.luno.gateway.data.repository.EnqueueResult
import com.luno.gateway.data.repository.OutboxDispatcher
import com.luno.gateway.data.repository.OutboxRepository
import com.luno.gateway.logging.LunoLogger
import com.luno.gateway.model.DeviceState
import com.luno.gateway.model.OutboundMessage
import com.luno.gateway.security.RateLimiter
import com.luno.gateway.util.Clock
import com.luno.gateway.util.Ids
import com.luno.gateway.util.SystemClock

/**
 * Turns a decoded backend command frame into durable actions and the events that
 * report them (§2 outbound flow). The command frame's own [ProtocolFrame.id] is
 * the idempotency key, so a redelivered `send_sms` re-acks and re-reports the same
 * message without enqueuing or sending it twice.
 *
 * `send_sms` is gated by client-enforced safety controls (§ threat model): an optional
 * recipient allowlist and a [RateLimiter], both backend-authoritative but enforced here
 * so a compromised/buggy backend can't turn the SIM into a spam cannon. `revoke`/`wipe`
 * run [onWipe] to return the node to unpaired.
 */
class CommandRouter(
    private val outbox: OutboxRepository,
    private val dispatcher: OutboxDispatcher,
    private val events: EventPublisher,
    private val ack: EventSink,
    private val deviceState: () -> DeviceState,
    private val onHeartbeatInterval: (seconds: Int) -> Unit,
    private val logger: LunoLogger,
    private val rateLimiter: RateLimiter = RateLimiter(),
    private val allowlist: () -> Set<String> = { emptySet() },
    private val applyPolicy: (ratePerMinute: Int?, allowlist: List<String>?) -> Unit = { _, _ -> },
    private val onWipe: suspend () -> Unit = {},
    private val clock: Clock = SystemClock,
) {
    /** @return the command frame's seq if it was a real command, so the agent can track the resync cursor. */
    suspend fun handle(frame: ProtocolFrame): Long? {
        val command = (frame.body as? FrameBody.CommandBody)?.command ?: return null
        when (command) {
            is Command.SendSms -> onSendSms(frame.id, command)
            is Command.CancelSms -> onCancel(command)
            is Command.GetStatus -> events.ephemeral(deviceState().toDeviceStatusEvent())
            is Command.ConfigUpdate -> onConfigUpdate(command)
            is Command.Revoke -> resetNode("revoke")
            is Command.Wipe -> resetNode("wipe")
        }
        ack.sendAck(frame.id)
        return frame.seq
    }

    private suspend fun resetNode(reason: String) {
        logger.w(TAG, "$reason received: clearing credential + queues, returning to unpaired")
        onWipe()
    }

    private suspend fun onSendSms(commandId: String, cmd: Command.SendSms) {
        val allowed = allowlist()
        if (allowed.isNotEmpty() && cmd.to !in allowed) {
            reject(commandId, "policy_reject", "recipient not allowlisted")
            return
        }
        if (!rateLimiter.tryAcquire(clock.nowMillis())) {
            reject(commandId, "rate_limited", "send rate limit exceeded")
            return
        }
        val message = OutboundMessage(
            id = Ids.newId(),
            recipient = cmd.to,
            body = cmd.body,
            subscriptionId = cmd.subscriptionId,
            requestDeliveryReport = cmd.deliveryReport,
            commandId = commandId,
            ref = cmd.ref,
        )
        when (val result = outbox.enqueue(message)) {
            is EnqueueResult.Enqueued -> {
                accept(commandId, result.id)
                dispatcher.dispatch(result.id)
            }
            is EnqueueResult.Duplicate -> {
                logger.i(TAG, "send_sms $commandId already applied as ${result.id}; re-acking only")
                accept(commandId, result.id)
            }
        }
    }

    private suspend fun accept(commandId: String, messageId: String) {
        events.reliable(Event.SmsAccepted(commandId, messageId), EventKeys.accepted(commandId))
    }

    private suspend fun reject(commandId: String, code: String, message: String) {
        logger.w(TAG, "send_sms $commandId rejected ($code): $message")
        events.reliable(Event.Error(code = code, message = message, ref = commandId), EventKeys.error(commandId))
    }

    private suspend fun onCancel(cmd: Command.CancelSms) {
        val cancelled = outbox.cancelByCommandId(cmd.commandId)
        logger.i(TAG, "cancel_sms ${cmd.commandId}: ${if (cancelled) "cancelled" else "not cancellable"}")
    }

    private fun onConfigUpdate(cmd: Command.ConfigUpdate) {
        cmd.heartbeatSec?.let { onHeartbeatInterval(it) }
        if (cmd.rateLimitPerMinute != null || cmd.allowlist != null) {
            applyPolicy(cmd.rateLimitPerMinute, cmd.allowlist)
            logger.i(TAG, "config_update: send policy updated (rate/allowlist)")
        }
        if (cmd.credential != null) {
            logger.i(TAG, "config_update: credential rotation not yet supported")
        }
    }

    companion object {
        private const val TAG = "CommandRouter"
    }
}
