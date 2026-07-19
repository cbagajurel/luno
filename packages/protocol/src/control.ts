import { compact, type JsonObject } from './json';
import { optionalInt, requireInt, requireIntArray, stringArrayWithDefault } from './internal/decode';

export const CONTROL_TYPES = {
  RESYNC: 'resync',
  VERSION_NEGOTIATE: 'version_negotiate',
  PING: 'ping',
  PONG: 'pong',
} as const;

/** The reconnect handshake (§7.4), version negotiation (§8.5), app-layer keepalive (§8.4). */
export interface ResyncControl {
  type: typeof CONTROL_TYPES.RESYNC;
  lastAckedInboundSeq: number;
  outstandingOutboxIds: string[];
}

export interface VersionNegotiateControl {
  type: typeof CONTROL_TYPES.VERSION_NEGOTIATE;
  supported: number[];
  selected?: number | null;
}

export interface PingControl {
  type: typeof CONTROL_TYPES.PING;
}

export interface PongControl {
  type: typeof CONTROL_TYPES.PONG;
}

export type Control = ResyncControl | VersionNegotiateControl | PingControl | PongControl;

export function encodeControl(control: Control): JsonObject {
  switch (control.type) {
    case CONTROL_TYPES.RESYNC:
      return compact({
        lastAckedInboundSeq: control.lastAckedInboundSeq,
        outstandingOutboxIds: control.outstandingOutboxIds ?? [],
      });
    case CONTROL_TYPES.VERSION_NEGOTIATE:
      return compact({ supported: control.supported, selected: control.selected });
    case CONTROL_TYPES.PING:
    case CONTROL_TYPES.PONG:
      return {};
  }
}

export function decodeControl(type: string, payload: JsonObject): Control | null {
  switch (type) {
    case CONTROL_TYPES.RESYNC:
      return {
        type: CONTROL_TYPES.RESYNC,
        lastAckedInboundSeq: requireInt(payload, 'lastAckedInboundSeq'),
        outstandingOutboxIds: stringArrayWithDefault(payload, 'outstandingOutboxIds', []),
      };
    case CONTROL_TYPES.VERSION_NEGOTIATE:
      return {
        type: CONTROL_TYPES.VERSION_NEGOTIATE,
        supported: requireIntArray(payload, 'supported'),
        selected: optionalInt(payload, 'selected'),
      };
    case CONTROL_TYPES.PING:
      return { type: CONTROL_TYPES.PING };
    case CONTROL_TYPES.PONG:
      return { type: CONTROL_TYPES.PONG };
    default:
      return null;
  }
}
