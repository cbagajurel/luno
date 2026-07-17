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
import com.luno.gateway.util.Ids

/**
 * Turns a decoded backend command frame into durable actions and the events that
 * report them (§2 outbound flow). The command frame's own [ProtocolFrame.id] is
 * the idempotency key, so a redelivered `send_sms` re-acks and re-reports the same
 * message without enqueuing or sending it twice.
 */
class CommandRouter(
    private val outbox: OutboxRepository,
    private val dispatcher: OutboxDispatcher,
    private val events: EventPublisher,
    private val ack: EventSink,
    private val deviceState: () -> DeviceState,
    private val onHeartbeatInterval: (seconds: Int) -> Unit,
    private val logger: LunoLogger,
) {
    /** @return the command frame's seq if it was a real command, so the agent can track the resync cursor. */
    suspend fun handle(frame: ProtocolFrame): Long? {
        val command = (frame.body as? FrameBody.CommandBody)?.command ?: return null
        when (command) {
            is Command.SendSms -> onSendSms(frame.id, command)
            is Command.CancelSms -> onCancel(command)
            is Command.GetStatus -> events.ephemeral(deviceState().toDeviceStatusEvent())
            is Command.ConfigUpdate -> onConfigUpdate(command)
            is Command.Revoke -> logger.w(TAG, "revoke received (applied in M16)")
            is Command.Wipe -> logger.w(TAG, "wipe received (applied in M16)")
        }
        ack.sendAck(frame.id)
        return frame.seq
    }

    private suspend fun onSendSms(commandId: String, cmd: Command.SendSms) {
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

    private suspend fun onCancel(cmd: Command.CancelSms) {
        val cancelled = outbox.cancelByCommandId(cmd.commandId)
        logger.i(TAG, "cancel_sms ${cmd.commandId}: ${if (cancelled) "cancelled" else "not cancellable"}")
    }

    private fun onConfigUpdate(cmd: Command.ConfigUpdate) {
        cmd.heartbeatSec?.let { onHeartbeatInterval(it) }
        if (cmd.rateLimitPerMinute != null || cmd.allowlist != null || cmd.credential != null) {
            logger.i(TAG, "config_update: rate-limit/allowlist/credential rotation applied in M16")
        }
    }

    companion object {
        private const val TAG = "CommandRouter"
    }
}
