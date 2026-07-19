import { describe, expect, it } from 'vitest';
import fixtures from '../fixtures/frames.json';
import { decodeFrame, encodeFrame } from '../src';

/**
 * The cross-language contract. The node's Kotlin ProtocolCodec reads this same
 * file and must reach the same conclusions; a divergence here is a wire-format
 * split between backend and node, which is the one bug this package exists to
 * make impossible.
 */
describe('golden frames', () => {
  for (const fixture of fixtures.frames) {
    it(`decodes and re-encodes ${fixture.name} byte-identically`, () => {
      const wire = JSON.stringify(fixture.envelope);
      const result = decodeFrame(wire);

      expect(result.status).toBe('ok');
      if (result.status !== 'ok') return;
      expect(encodeFrame(result.frame)).toBe(wire);
    });
  }

  for (const fixture of fixtures.unsupported) {
    it(`quarantines ${fixture.name} without failing`, () => {
      const result = decodeFrame(JSON.stringify(fixture.envelope));

      expect(result.status).toBe('unsupported');
      if (result.status !== 'unsupported') return;
      expect(result.envelope.id).toBe(fixture.envelope.id);
    });
  }

  it('covers every frame kind', () => {
    const kinds = new Set(fixtures.frames.map((fixture) => fixture.envelope.kind));
    expect([...kinds].sort()).toEqual(['ack', 'command', 'control', 'event']);
  });
});
