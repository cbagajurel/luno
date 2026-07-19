import { describe, expect, it } from 'vitest';
import {
  ackFrame,
  commandFrame,
  controlFrame,
  decodeFrame,
  encodeFrame,
  eventFrame,
  frameType,
  negotiateVersion,
  type FrameMeta,
} from '../src';

const meta: FrameMeta = {
  id: 'cmd_0001',
  ts: '2026-07-19T09:00:00Z',
  deviceId: 'dev_abc123',
  seq: 128,
};

describe('encodeFrame', () => {
  it('writes the envelope in the order the node emits', () => {
    const wire = encodeFrame(
      commandFrame(meta, { type: 'send_sms', to: '+9779800000000', body: 'hello', ref: 'order-42' }),
    );

    expect(wire).toBe(
      '{"v":1,"kind":"command","id":"cmd_0001","ts":"2026-07-19T09:00:00Z",' +
        '"deviceId":"dev_abc123","type":"send_sms","seq":128,' +
        '"payload":{"to":"+9779800000000","body":"hello","deliveryReport":true,"ref":"order-42"}}',
    );
  });

  it('omits absent fields rather than writing null, and keeps defaults', () => {
    const wire = encodeFrame(
      commandFrame(meta, { type: 'send_sms', to: '+15551234567', body: 'hi', subscriptionId: null }),
    );

    expect(wire).toContain('"payload":{"to":"+15551234567","body":"hi","deliveryReport":true}');
    expect(wire).not.toContain('subscriptionId');
    expect(wire).not.toContain('null');
  });

  it('writes an empty object for payload-less commands', () => {
    expect(encodeFrame(commandFrame(meta, { type: 'get_status' }))).toContain('"payload":{}');
  });

  it('keeps empty collection defaults, which the node expects to be present', () => {
    const wire = encodeFrame(
      eventFrame(meta, { type: 'heartbeat', queueDepth: 0 }),
    );

    expect(wire).toContain('"payload":{"queueDepth":0,"signals":[],"transports":[]}');
  });
});

describe('decodeFrame', () => {
  it('round-trips a command', () => {
    const frame = commandFrame(meta, {
      type: 'send_sms',
      to: '+9779800000000',
      body: 'hello',
      subscriptionId: 2,
      deliveryReport: false,
      ref: 'order-42',
    });
    const result = decodeFrame(encodeFrame(frame));

    expect(result.status).toBe('ok');
    if (result.status !== 'ok') return;
    expect(result.frame).toEqual(frame);
  });

  it('applies the deliveryReport default when the field is absent', () => {
    const result = decodeFrame(
      '{"v":1,"kind":"command","id":"c","ts":"t","deviceId":"d","type":"send_sms","seq":1,' +
        '"payload":{"to":"+1","body":"x"}}',
    );

    expect(result.status).toBe('ok');
    if (result.status !== 'ok') return;
    expect(result.frame.body).toEqual({
      kind: 'command',
      command: { type: 'send_sms', to: '+1', body: 'x', subscriptionId: undefined, deliveryReport: true, ref: undefined },
    });
  });

  it('ignores unknown payload fields so a newer peer stays readable', () => {
    const result = decodeFrame(
      '{"v":1,"kind":"command","id":"c","ts":"t","deviceId":"d","type":"send_sms","seq":1,' +
        '"payload":{"to":"+1","body":"x","priority":"high"},"futureField":9}',
    );

    expect(result.status).toBe('ok');
  });

  it('reports an unknown (kind, type) as unsupported and preserves the envelope', () => {
    const result = decodeFrame(
      '{"v":1,"kind":"command","id":"c","ts":"t","deviceId":"d","type":"send_mms","seq":1,"payload":{}}',
    );

    expect(result.status).toBe('unsupported');
    if (result.status !== 'unsupported') return;
    expect(result.envelope.type).toBe('send_mms');
    expect(result.reason).toContain('command/send_mms');
  });

  it.each([
    ['not json at all', 'nonsense'],
    ['an unknown kind', '{"v":1,"kind":"telepathy","id":"c","ts":"t","deviceId":"d","type":"x","seq":1,"payload":{}}'],
    ['a missing envelope field', '{"v":1,"kind":"ack","id":"c","ts":"t","type":"ack","seq":1,"payload":{}}'],
    ['a missing payload', '{"v":1,"kind":"ack","id":"c","ts":"t","deviceId":"d","type":"ack","seq":1}'],
    [
      'a missing required payload field',
      '{"v":1,"kind":"command","id":"c","ts":"t","deviceId":"d","type":"send_sms","seq":1,"payload":{"to":"+1"}}',
    ],
    [
      'an explicit null for a non-nullable field',
      '{"v":1,"kind":"command","id":"c","ts":"t","deviceId":"d","type":"send_sms","seq":1,' +
        '"payload":{"to":"+1","body":"x","deliveryReport":null}}',
    ],
    [
      'a non-integer sequence',
      '{"v":1,"kind":"ack","id":"c","ts":"t","deviceId":"d","type":"ack","seq":1.5,"payload":{"ackedId":"e"}}',
    ],
  ])('quarantines %s as malformed', (_label, wire) => {
    expect(decodeFrame(wire).status).toBe('malformed');
  });

  it('never throws on hostile input', () => {
    for (const wire of ['', '[]', 'null', '{}', '{"v":1}', '"a string"']) {
      expect(() => decodeFrame(wire)).not.toThrow();
      expect(decodeFrame(wire).status).not.toBe('ok');
    }
  });
});

describe('frameType', () => {
  it('derives the envelope type from each body kind', () => {
    expect(frameType(commandFrame(meta, { type: 'wipe' }).body)).toBe('wipe');
    expect(frameType(eventFrame(meta, { type: 'log', level: 'info', tag: 't', msg: 'm', at: 1 }).body)).toBe('log');
    expect(frameType(ackFrame(meta, { ackedId: 'x' }).body)).toBe('ack');
    expect(frameType(controlFrame(meta, { type: 'ping' }).body)).toBe('ping');
  });
});

describe('negotiateVersion', () => {
  it('picks the highest mutually supported version', () => {
    expect(negotiateVersion([1])).toBe(1);
    expect(negotiateVersion([1, 2, 3])).toBe(1);
  });

  it('returns null when no version is shared', () => {
    expect(negotiateVersion([2, 3])).toBeNull();
    expect(negotiateVersion([])).toBeNull();
  });
});
