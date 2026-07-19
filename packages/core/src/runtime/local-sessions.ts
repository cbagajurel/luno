import type { ProtocolFrame } from '@luno/protocol';
import type { DeliveryOutcome, FrameSink, SessionRegistry } from '../ports/sessions';

/**
 * Sessions held in this process. Correct for a single long-running server and
 * for a Durable Object that owns its device; a multi-instance deployment needs a
 * broker-backed registry instead, which is why `deliver` can report `not_local`
 * even though this implementation never does.
 */
export function localSessionRegistry(): SessionRegistry {
  const sinks = new Map<string, { sessionId: string; sink: FrameSink }>();

  return {
    async register(deviceId, sessionId, sink) {
      const existing = sinks.get(deviceId);
      sinks.set(deviceId, { sessionId, sink });
      // A node that reconnects before we noticed the old socket drop would
      // otherwise leave a stale sink that silently swallows commands.
      if (existing && existing.sessionId !== sessionId) {
        await existing.sink.close(1012, 'superseded by a newer session').catch(() => {});
      }
    },

    async unregister(deviceId, sessionId) {
      const existing = sinks.get(deviceId);
      if (existing?.sessionId === sessionId) sinks.delete(deviceId);
    },

    async deliver(deviceId, frame: ProtocolFrame): Promise<DeliveryOutcome> {
      const existing = sinks.get(deviceId);
      if (!existing) return { delivered: false, reason: 'offline' };
      await existing.sink.send(frame);
      return { delivered: true };
    },

    async isConnected(deviceId) {
      return sinks.has(deviceId);
    },

    async connectedDeviceIds() {
      return [...sinks.keys()];
    },
  };
}
