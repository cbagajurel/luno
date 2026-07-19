import { describe, expect, it } from 'vitest';
import { buildPairingJson, buildPairingUri, parsePairingPayload } from '../src';

const ok = (raw: string) => {
  const result = parsePairingPayload(raw);
  if (result.status !== 'ok') throw new Error(`expected ok, got ${result.status}`);
  return result.payload;
};

describe('parsePairingPayload', () => {
  it('parses the URI form', () => {
    expect(
      ok('luno://pair?v=1&u=https%3A%2F%2Fgw.example.com&c=ABCD-1234&s=ses_9f3&l=Acme&p=sha256%2FAAAA'),
    ).toEqual({
      backendUrl: 'https://gw.example.com',
      pairingCode: 'ABCD-1234',
      sessionId: 'ses_9f3',
      label: 'Acme',
      pin: 'sha256/AAAA',
    });
  });

  it('parses the JSON form identically', () => {
    expect(
      ok('{"v":1,"u":"https://gw.example.com","c":"ABCD-1234","s":"ses_9f3","l":"Acme","p":"sha256/AAAA"}'),
    ).toEqual(ok('luno://pair?v=1&u=https%3A%2F%2Fgw.example.com&c=ABCD-1234&s=ses_9f3&l=Acme&p=sha256%2FAAAA'));
  });

  it("decodes '+' as a space, matching the node's URLDecoder", () => {
    expect(ok('luno://pair?v=1&u=https%3A%2F%2Fgw.example.com&c=A1&l=Acme+Corp').label).toBe('Acme Corp');
    expect(ok('luno://pair?v=1&u=https%3A%2F%2Fgw.example.com&c=A1&l=Acme%20Corp').label).toBe('Acme Corp');
  });

  it('accepts a numeric-string version, as the node does', () => {
    expect(ok('{"v":"1","u":"https://gw.example.com","c":"A1"}').pairingCode).toBe('A1');
  });

  it('is case-insensitive about the scheme', () => {
    expect(ok('LUNO://PAIR?v=1&u=https%3A%2F%2Fgw.example.com&c=A1').pairingCode).toBe('A1');
  });

  it('trims trailing slashes from the backend URL', () => {
    expect(ok('luno://pair?v=1&u=https%3A%2F%2Fgw.example.com%2F%2F&c=A1').backendUrl).toBe(
      'https://gw.example.com',
    );
  });

  it('omits blank optional fields instead of returning empty strings', () => {
    const payload = ok('luno://pair?v=1&u=https%3A%2F%2Fgw.example.com&c=A1&s=&l=&p=');
    expect(payload.sessionId).toBeUndefined();
    expect(payload.label).toBeUndefined();
    expect(payload.pin).toBeUndefined();
  });

  it('reports a newer payload version rather than guessing at its fields', () => {
    const result = parsePairingPayload('luno://pair?v=2&u=https%3A%2F%2Fgw.example.com&c=A1');
    expect(result).toEqual({ status: 'unsupported_version', version: 2 });
  });

  it.each([
    ['empty input', '   ', 'empty payload'],
    ['a foreign scheme', 'https://example.com/pair', 'not a Luno pairing code'],
    ['a link with no parameters', 'luno://pair', 'pairing link has no parameters'],
    ['a missing version', 'luno://pair?u=https%3A%2F%2Fgw.example.com&c=A1', 'missing version'],
    ['a missing URL', 'luno://pair?v=1&c=A1', 'missing backend URL'],
    ['a non-http URL', 'luno://pair?v=1&u=ftp%3A%2F%2Fgw.example.com&c=A1', 'backend URL must be http(s)'],
    ['a missing code', 'luno://pair?v=1&u=https%3A%2F%2Fgw.example.com', 'missing pairing code'],
    ['broken JSON', '{"v":1,', 'not valid pairing JSON'],
    ['an undecodable escape', 'luno://pair?v=1&u=%zz&c=A1', "undecodable parameter 'u'"],
  ])('rejects %s', (_label, raw, reason) => {
    expect(parsePairingPayload(raw)).toEqual({ status: 'malformed', reason });
  });
});

describe('building payloads', () => {
  const payload = {
    backendUrl: 'https://gw.example.com',
    pairingCode: 'ABCD-1234',
    sessionId: 'ses_9f3',
    label: 'Acme Corp',
    pin: 'sha256/AAAA',
  };

  it('produces a URI the parser reads back unchanged', () => {
    expect(ok(buildPairingUri(payload))).toEqual(payload);
  });

  it('produces JSON the parser reads back unchanged', () => {
    expect(ok(buildPairingJson(payload))).toEqual(payload);
  });

  it('percent-encodes reserved characters', () => {
    expect(buildPairingUri(payload)).toBe(
      'luno://pair?v=1&u=https%3A%2F%2Fgw.example.com&c=ABCD-1234&s=ses_9f3&l=Acme%20Corp&p=sha256%2FAAAA',
    );
  });

  it('omits optional fields that are absent', () => {
    const minimal = { backendUrl: 'https://gw.example.com', pairingCode: 'A1' };
    expect(buildPairingUri(minimal)).toBe('luno://pair?v=1&u=https%3A%2F%2Fgw.example.com&c=A1');
    expect(buildPairingJson(minimal)).toBe('{"v":1,"u":"https://gw.example.com","c":"A1"}');
  });
});
