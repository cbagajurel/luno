import type { ProtocolFrame } from '@luno-oss/protocol';

/**
 * Where a frame goes. The entire transport abstraction is this one interface: a
 * `ws` socket, a Cloudflare Durable Object, an SSE stream and a buffered
 * long-poll queue all satisfy it identically, so the §6/§7 logic is written once
 * and never learns which it is talking to.
 */
export interface FrameSink {
  send(frame: ProtocolFrame): Promise<void>;
  close(code?: number, reason?: string): Promise<void>;
}

export type DeliveryOutcome =
  | { delivered: true }
  /** No session anywhere — the node is offline. */
  | { delivered: false; reason: 'offline' }
  /**
   * A session exists but not in this process. A single-process registry never
   * returns this; a broker-backed one returns it when it cannot route, which is
   * what keeps multi-instance and socketless deployments expressible without
   * changing the core.
   */
  | { delivered: false; reason: 'not_local' };

export interface SessionRegistry {
  register(deviceId: string, sessionId: string, sink: FrameSink): Promise<void>;
  unregister(deviceId: string, sessionId: string): Promise<void>;
  deliver(deviceId: string, frame: ProtocolFrame): Promise<DeliveryOutcome>;
  isConnected(deviceId: string): Promise<boolean>;
  connectedDeviceIds(): Promise<string[]>;
}
