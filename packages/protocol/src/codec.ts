import { ACK_TYPE, decodeAck, encodeAck } from './ack';
import { decodeCommand, encodeCommand } from './command';
import { decodeControl, encodeControl } from './control';
import { decodeEvent, encodeEvent } from './event';
import {
  FRAME_KINDS,
  type FrameBody,
  type FrameKind,
  type ProtocolFrame,
  type RawEnvelope,
} from './envelope';
import {
  DecodeError,
  asJsonObject,
  requireInt,
  requireString,
} from './internal/decode';
import type { JsonObject } from './json';

export type DecodeResult =
  | { status: 'ok'; frame: ProtocolFrame }
  /** Envelope parsed, but its (kind, type) is not one we know — quarantine, don't crash. */
  | { status: 'unsupported'; envelope: RawEnvelope; reason: string }
  /** Not even a well-formed envelope — quarantine the raw text. */
  | { status: 'malformed'; raw: string; reason: string };

export function frameType(body: FrameBody): string {
  switch (body.kind) {
    case 'command':
      return body.command.type;
    case 'event':
      return body.event.type;
    case 'ack':
      return ACK_TYPE;
    case 'control':
      return body.control.type;
  }
}

function encodePayload(body: FrameBody): JsonObject {
  switch (body.kind) {
    case 'command':
      return encodeCommand(body.command);
    case 'event':
      return encodeEvent(body.event);
    case 'ack':
      return encodeAck(body.ack);
    case 'control':
      return encodeControl(body.control);
  }
}

export function encodeFrame(frame: ProtocolFrame): string {
  const envelope: RawEnvelope = {
    v: frame.v,
    kind: frame.body.kind,
    id: frame.id,
    ts: frame.ts,
    deviceId: frame.deviceId,
    type: frameType(frame.body),
    seq: frame.seq,
    payload: encodePayload(frame.body),
  };
  return JSON.stringify(envelope);
}

function isFrameKind(value: string): value is FrameKind {
  return (FRAME_KINDS as readonly string[]).includes(value);
}

function readEnvelope(text: string): RawEnvelope {
  const root = asJsonObject(JSON.parse(text), 'frame');
  const kind = requireString(root, 'kind');
  if (!isFrameKind(kind)) throw new DecodeError(`unknown kind '${kind}'`);
  return {
    v: requireInt(root, 'v'),
    kind,
    id: requireString(root, 'id'),
    ts: requireString(root, 'ts'),
    deviceId: requireString(root, 'deviceId'),
    type: requireString(root, 'type'),
    seq: requireInt(root, 'seq'),
    payload: asJsonObject(root['payload'], "'payload'"),
  };
}

function readBody(envelope: RawEnvelope): FrameBody | null {
  const { type, payload } = envelope;
  switch (envelope.kind) {
    case 'command': {
      const command = decodeCommand(type, payload);
      return command && { kind: 'command', command };
    }
    case 'event': {
      const event = decodeEvent(type, payload);
      return event && { kind: 'event', event };
    }
    case 'ack':
      return { kind: 'ack', ack: decodeAck(payload) };
    case 'control': {
      const control = decodeControl(type, payload);
      return control && { kind: 'control', control };
    }
  }
}

const reasonOf = (error: unknown): string =>
  error instanceof Error ? error.message : String(error);

/**
 * Decodes a frame without ever throwing. Unknown (kind, type) pairs are reported
 * as `unsupported` rather than rejected so a newer node and an older backend
 * interoperate (§8.5); anything structurally broken is `malformed`. Both are for
 * the caller to quarantine and carry on.
 */
export function decodeFrame(text: string): DecodeResult {
  let envelope: RawEnvelope;
  try {
    envelope = readEnvelope(text);
  } catch (error) {
    return { status: 'malformed', raw: text, reason: `envelope: ${reasonOf(error)}` };
  }

  let body: FrameBody | null;
  try {
    body = readBody(envelope);
  } catch (error) {
    return { status: 'malformed', raw: text, reason: `payload: ${reasonOf(error)}` };
  }

  if (body === null) {
    return {
      status: 'unsupported',
      envelope,
      reason: `unknown ${envelope.kind}/${envelope.type}`,
    };
  }

  return {
    status: 'ok',
    frame: {
      v: envelope.v,
      id: envelope.id,
      ts: envelope.ts,
      deviceId: envelope.deviceId,
      seq: envelope.seq,
      body,
    },
  };
}
