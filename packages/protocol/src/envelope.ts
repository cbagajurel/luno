import type { JsonObject } from './json';
import type { Ack } from './ack';
import type { Command } from './command';
import type { Control } from './control';
import type { Event } from './event';
import { PROTOCOL_VERSION } from './version';

export const FRAME_KINDS = ['command', 'event', 'ack', 'control'] as const;

export type FrameKind = (typeof FRAME_KINDS)[number];

/**
 * The envelope every frame shares (§8.1), with its payload still raw. Field
 * order here is the wire order the node's kotlinx serializer emits.
 */
export interface RawEnvelope {
  v: number;
  kind: FrameKind;
  id: string;
  ts: string;
  deviceId: string;
  type: string;
  seq: number;
  payload: JsonObject;
}

export type FrameBody =
  | { kind: 'command'; command: Command }
  | { kind: 'event'; event: Event }
  | { kind: 'ack'; ack: Ack }
  | { kind: 'control'; control: Control };

/** A fully-typed frame: envelope metadata plus its decoded body. */
export interface ProtocolFrame {
  v: number;
  id: string;
  ts: string;
  deviceId: string;
  seq: number;
  body: FrameBody;
}

export interface FrameMeta {
  v?: number;
  id: string;
  ts: string;
  deviceId: string;
  seq: number;
}

const withMeta = (meta: FrameMeta, body: FrameBody): ProtocolFrame => ({
  v: meta.v ?? PROTOCOL_VERSION,
  id: meta.id,
  ts: meta.ts,
  deviceId: meta.deviceId,
  seq: meta.seq,
  body,
});

export const commandFrame = (meta: FrameMeta, command: Command): ProtocolFrame =>
  withMeta(meta, { kind: 'command', command });

export const eventFrame = (meta: FrameMeta, event: Event): ProtocolFrame =>
  withMeta(meta, { kind: 'event', event });

export const ackFrame = (meta: FrameMeta, ack: Ack): ProtocolFrame =>
  withMeta(meta, { kind: 'ack', ack });

export const controlFrame = (meta: FrameMeta, control: Control): ProtocolFrame =>
  withMeta(meta, { kind: 'control', control });
