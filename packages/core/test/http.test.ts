import { classifyEnrollFailure, decodeEnrollResponse } from '@luno-oss/protocol';
import { describe, expect, it } from 'vitest';
import type { HttpRequest } from '../src';
import { toFetchHandler } from '../src';
import { enrollRequest, harness } from './helpers';

const request = (method: string, url: string, body?: unknown): HttpRequest => ({
  method,
  url,
  json: async () => {
    if (body === undefined) throw new Error('no body');
    return body;
  },
});

const parse = (body: string) => JSON.parse(body) as unknown;

describe('POST /enroll', () => {
  it('returns a credential the node can read back', async () => {
    const { luno } = harness();
    const { code } = await luno.pairing.createSession();

    const result = await luno.http.handle(
      request('POST', 'https://gw.example.com/enroll', enrollRequest(code)),
    );

    expect(result.status).toBe(200);
    const decoded = decodeEnrollResponse(parse(result.body));
    expect(decoded.status).toBe('approved');
    expect(decoded.credential).toBeTruthy();
    expect(await luno.connections.authorize(decoded.credential ?? '')).not.toBeNull();
  });

  /**
   * The response is only correct if the node's own classifier reaches the verdict
   * we intended, so the assertion runs the body back through `@luno-oss/protocol`
   * rather than checking fields by hand.
   */
  it.each([
    ['an unknown code', 'ZZZZ-9999', 'invalid_code'],
  ])('rejects %s in the shape the node classifies', async (_label, code, expected) => {
    const { luno } = harness();
    await luno.pairing.createSession();

    const result = await luno.http.handle(
      request('POST', 'https://gw.example.com/enroll', enrollRequest(code)),
    );

    expect(result.status).toBeGreaterThanOrEqual(400);
    expect(classifyEnrollFailure(result.status, parse(result.body)).error).toBe(expected);
  });

  it('reports an expired session as such, not as a bad code', async () => {
    const context = harness();
    const { code } = await context.luno.pairing.createSession();
    context.advance(600_001);

    const result = await context.luno.http.handle(
      request('POST', 'https://gw.example.com/enroll', enrollRequest(code)),
    );

    expect(classifyEnrollFailure(result.status, parse(result.body)).error).toBe('session_expired');
  });

  it.each([
    ['a body that is not JSON', undefined],
    ['a body missing required fields', { pairingCode: 'A' }],
  ])('answers 400 for %s', async (_label, body) => {
    const { luno } = harness();
    const result = await luno.http.handle(request('POST', 'https://gw.example.com/enroll', body));

    expect(result.status).toBe(400);
  });
});

describe('POST /enroll/status', () => {
  it('reports a pending enrolment with a retry hint the node honours', async () => {
    const { luno } = harness({ pairing: { requireApproval: true } });
    const { code } = await luno.pairing.createSession();
    const pending = await luno.pairing.enroll(enrollRequest(code));
    if (pending.status !== 'pending') throw new Error('expected pending');

    const result = await luno.http.handle(
      request('POST', 'https://gw.example.com/enroll/status', {
        enrollmentId: pending.enrollmentId,
        nonce: 'n',
        protocolVersion: 1,
      }),
    );

    const decoded = decodeEnrollResponse(parse(result.body));
    expect(result.status).toBe(200);
    expect(decoded.status).toBe('pending');
    expect(decoded.retryAfterMs).toBeGreaterThanOrEqual(1_000);
    expect(decoded.retryAfterMs).toBeLessThanOrEqual(60_000);
  });

  it('answers a denial with 2xx so the node reads the verdict from the body', async () => {
    const { luno } = harness({ pairing: { requireApproval: true } });
    const { code } = await luno.pairing.createSession();
    const pending = await luno.pairing.enroll(enrollRequest(code));
    if (pending.status !== 'pending') throw new Error('expected pending');
    await luno.pairing.denyEnrollment(pending.enrollmentId);

    const result = await luno.http.handle(
      request('POST', 'https://gw.example.com/enroll/status', {
        enrollmentId: pending.enrollmentId,
        nonce: 'n',
        protocolVersion: 1,
      }),
    );

    expect(result.status).toBe(200);
    expect(decodeEnrollResponse(parse(result.body)).status).toBe('denied');
  });
});

describe('routing', () => {
  it('routes regardless of the prefix an adapter mounts it under', async () => {
    const { luno } = harness();
    const { code } = await luno.pairing.createSession();

    const result = await luno.http.handle(
      request('POST', 'https://gw.example.com/api/v1/luno/enroll?trace=1', enrollRequest(code)),
    );
    expect(result.status).toBe(200);
  });

  it.each([
    ['an unknown path', 'GET', 'https://gw.example.com/health', 404],
    ['the wrong method', 'GET', 'https://gw.example.com/enroll', 405],
  ])('answers %s with %i', async (_label, method, url, status) => {
    const { luno } = harness();
    expect((await luno.http.handle(request(method, url))).status).toBe(status);
  });
});

describe('toFetchHandler', () => {
  it('adapts the router onto a platform response type', async () => {
    const { luno } = harness();
    const { code } = await luno.pairing.createSession();

    const handler = toFetchHandler(luno.http, (body, init) => ({ body, ...init }));
    const response = await handler(
      request('POST', 'https://gw.example.com/enroll', enrollRequest(code)),
    );

    expect(response.status).toBe(200);
    expect(response.headers['content-type']).toContain('application/json');
  });
});
