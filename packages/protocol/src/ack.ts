import { compact, type JsonObject } from './json';
import { requireString } from './internal/decode';

export const ACK_TYPE = 'ack';

/** Confirms receipt of a specific frame, in either direction (§8.4). */
export interface Ack {
  ackedId: string;
}

export function encodeAck(ack: Ack): JsonObject {
  return compact({ ackedId: ack.ackedId });
}

export function decodeAck(payload: JsonObject): Ack {
  return { ackedId: requireString(payload, 'ackedId') };
}
