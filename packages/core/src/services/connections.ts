import {
  SUPPORTED_PROTOCOL_VERSIONS,
  ackFrame,
  controlFrame,
  decodeFrame,
  negotiateVersion,
  type Control,
  type Event,
  type FrameMeta,
  type ProtocolFrame,
} from '@luno-oss/protocol';
import { auditEvent, type CoreContext } from '../context';
import type { ConnectionPhase } from '../domain/device';
import type { FrameSink } from '../ports/sessions';
import type { DeviceRecord } from '../ports/store';
import { hashDeviceCredential } from './hashing';
import type { MessagingService } from './messaging';

export interface NodeSession {
  readonly id: string;
  readonly deviceId: string;
  /** Feed one raw frame in. Never throws on bad input — malformed frames are quarantined. */
  receive(raw: string): Promise<void>;
  close(): Promise<void>;
}

export function connectionsService(context: CoreContext, messaging: MessagingService) {
  return {
    /**
     * Resolves the credential a node presents on the WSS upgrade. Lookup is by
     * digest rather than by comparing secrets, so there is no string comparison
     * to leak timing and no plaintext credential stored to compare against.
     */
    async authorize(credential: string): Promise<DeviceRecord | null> {
      if (!credential) return null;
      const device = await context.store.devices.findByCredentialHash(
        await hashDeviceCredential(context, credential),
      );
      if (!device || device.status !== 'active') return null;
      return device;
    },

    async open(device: DeviceRecord, sink: FrameSink): Promise<NodeSession> {
      const sessionId = context.ids.newId('con');
      let seq = 0;
      let closed = false;

      // Sequence is per-connection and belongs to whoever owns the socket, so the
      // outbound frame gets its number here rather than at the call site — which
      // lets messaging build a frame without knowing a connection exists.
      const stamped: FrameSink = {
        send: (frame: ProtocolFrame) => sink.send({ ...frame, deviceId: device.id, seq: (seq += 1) }),
        close: (code, reason) => sink.close(code, reason),
      };

      const meta = (): FrameMeta => ({
        id: context.ids.newId('frm'),
        ts: new Date(context.clock.now()).toISOString(),
        deviceId: device.id,
        seq: 0,
      });

      const sendControl = (control: Control) => stamped.send(controlFrame(meta(), control));
      const sendAck = (ackedId: string) => stamped.send(ackFrame(meta(), { ackedId }));

      async function touch(phase?: ConnectionPhase): Promise<void> {
        await context.store.devices.update(device.id, {
          lastSeenAt: context.clock.now(),
          ...(phase ? { phase } : {}),
        });
      }

      async function onControl(frame: ProtocolFrame, control: Control): Promise<void> {
        switch (control.type) {
          case 'version_negotiate': {
            const selected = negotiateVersion(control.supported);
            if (selected === null) {
              await auditEvent(context, {
                deviceId: device.id,
                direction: 'system',
                kind: 'system',
                type: 'version_mismatch',
                payload: { peerSupported: control.supported },
              });
              await stamped.close(1002, 'no mutually supported protocol version');
              return;
            }
            await touch('authenticated');
            await sendControl({
              type: 'version_negotiate',
              supported: [...SUPPORTED_PROTOCOL_VERSIONS],
              selected,
            });
            return;
          }

          case 'resync': {
            await auditEvent(context, {
              deviceId: device.id,
              direction: 'in',
              kind: 'control',
              type: 'resync',
              frameId: frame.id,
              payload: control,
            });
            await touch('ready');
            await context.hooks.emit('device.online', { deviceId: device.id });
            // The node reaches READY on this ack; only then is it listening for
            // the commands it missed.
            await sendAck(frame.id);
            await messaging.redispatchOutstanding(device.id);
            return;
          }

          case 'ping':
            await sendControl({ type: 'pong' });
            return;

          case 'pong':
            return;
        }
      }

      async function onEvent(frame: ProtocolFrame, event: Event): Promise<void> {
        await auditEvent(context, {
          deviceId: device.id,
          direction: 'in',
          kind: 'event',
          type: event.type,
          frameId: frame.id,
          payload: event,
        });

        await messaging.applyEvent(device.id, event);

        switch (event.type) {
          case 'device_status':
            await context.hooks.emit('device.status', { deviceId: device.id, status: event });
            break;
          case 'heartbeat':
            await context.hooks.emit('device.heartbeat', { deviceId: device.id, heartbeat: event });
            break;
          case 'log':
            await context.hooks.emit('node.log', { deviceId: device.id, log: event });
            break;
          case 'error':
            await context.hooks.emit('node.error', { deviceId: device.id, error: event });
            break;
          default:
            break;
        }

        // Acked last: the node holds the event in its durable outbox until this
        // lands, so acking before we have stored it would lose the event on crash.
        await sendAck(frame.id);
      }

      await context.sessions.register(device.id, sessionId, stamped);
      await touch('connected');
      await auditEvent(context, {
        deviceId: device.id,
        direction: 'system',
        kind: 'system',
        type: 'socket_open',
        payload: { sessionId },
      });

      async function process(raw: string): Promise<void> {
        const result = decodeFrame(raw);
        if (result.status !== 'ok') {
          await auditEvent(context, {
            deviceId: device.id,
            direction: 'in',
            kind: 'quarantine',
            type: result.status,
            payload: { reason: result.reason },
          });
          return;
        }

        const { frame } = result;
        await touch();

        switch (frame.body.kind) {
          case 'control':
            return onControl(frame, frame.body.control);
          case 'event':
            return onEvent(frame, frame.body.event);
          case 'ack':
            await auditEvent(context, {
              deviceId: device.id,
              direction: 'in',
              kind: 'ack',
              type: 'ack',
              frameId: frame.body.ack.ackedId,
            });
            return;
          case 'command':
            // Commands only flow backend→node; a node sending one is a peer bug.
            await auditEvent(context, {
              deviceId: device.id,
              direction: 'in',
              kind: 'quarantine',
              type: 'unexpected_command',
              frameId: frame.id,
            });
            return;
        }
      }

      // Frames must apply in arrival order: sms_sent looks up the message that
      // sms_accepted just wrote, so a node that streams events without waiting —
      // and it may — would race if we processed concurrently. Chaining serializes
      // the whole session onto one queue, which every adapter then inherits.
      let tail: Promise<void> = Promise.resolve();

      return {
        id: sessionId,
        deviceId: device.id,

        receive(raw: string): Promise<void> {
          const run = tail.then(() => process(raw));
          tail = run.catch(() => undefined);
          return run;
        },

        async close(): Promise<void> {
          if (closed) return;
          closed = true;
          await context.sessions.unregister(device.id, sessionId);
          await context.store.devices.update(device.id, { phase: 'offline' });
          await auditEvent(context, {
            deviceId: device.id,
            direction: 'system',
            kind: 'system',
            type: 'socket_close',
            payload: { sessionId },
          });
          await context.hooks.emit('device.offline', { deviceId: device.id });
        },
      };
    },
  };
}

export type ConnectionsService = ReturnType<typeof connectionsService>;
