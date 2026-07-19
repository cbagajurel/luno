import type { DeviceStatusEvent, ErrorEvent, HeartbeatEvent, LogEvent } from '@luno/protocol';
import type { DeviceRecord, EnrollmentRecord, MessageRecord } from './ports/store';

export interface LunoEventMap {
  'device.enrolled': { device: DeviceRecord };
  'device.online': { deviceId: string };
  'device.offline': { deviceId: string };
  'device.revoked': { deviceId: string };
  'device.status': { deviceId: string; status: DeviceStatusEvent };
  'device.heartbeat': { deviceId: string; heartbeat: HeartbeatEvent };
  'enrollment.pending': { enrollment: EnrollmentRecord };
  'sms.received': {
    deviceId: string;
    from: string;
    body: string;
    subscriptionId: number | null;
    receivedAt: number;
    parts: number;
  };
  'sms.status': { message: MessageRecord };
  'node.log': { deviceId: string; log: LogEvent };
  'node.error': { deviceId: string; error: ErrorEvent };
}

export type LunoEventName = keyof LunoEventMap;

export type LunoEventHandler<K extends LunoEventName> = (
  payload: LunoEventMap[K],
) => void | Promise<void>;

export interface HookBus {
  on<K extends LunoEventName>(event: K, handler: LunoEventHandler<K>): () => void;
  emit<K extends LunoEventName>(event: K, payload: LunoEventMap[K]): Promise<void>;
}

/**
 * A handler that throws must not take down the frame loop that emitted it — a
 * failing webhook is the application's problem, not a reason to drop a node's
 * connection — so failures are reported and swallowed.
 */
export function hookBus(onError: (event: string, error: unknown) => void): HookBus {
  const handlers = new Map<string, Set<(payload: unknown) => void | Promise<void>>>();

  return {
    on(event, handler) {
      const set = handlers.get(event) ?? new Set();
      set.add(handler as (payload: unknown) => void | Promise<void>);
      handlers.set(event, set);
      return () => set.delete(handler as (payload: unknown) => void | Promise<void>);
    },

    async emit(event, payload) {
      const set = handlers.get(event);
      if (!set) return;
      for (const handler of set) {
        try {
          await handler(payload);
        } catch (error) {
          onError(event, error);
        }
      }
    },
  };
}
