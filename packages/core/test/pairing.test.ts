import { parsePairingPayload } from '@luno-oss/protocol';
import { beforeEach, describe, expect, it } from 'vitest';
import { deviceInfo, enrollRequest, harness } from './helpers';

describe('createSession', () => {
  it('returns the plaintext code once and stores only its digest', async () => {
    const { luno } = harness();
    const { session, code } = await luno.pairing.createSession();

    expect(code).toMatch(/^[A-Z2-9]{4}-[A-Z2-9]{4}$/);
    expect(session.codeHash).not.toContain(code);
    expect(JSON.stringify(session)).not.toContain(code.replace('-', ''));
  });

  it('builds a scannable payload the node can parse', async () => {
    const { luno } = harness();
    const { code, qrUri, session } = await luno.pairing.createSession({
      backendUrl: 'https://gw.example.com',
      label: 'Acme',
    });

    const parsed = parsePairingPayload(qrUri ?? '');
    expect(parsed.status).toBe('ok');
    if (parsed.status !== 'ok') return;
    expect(parsed.payload).toEqual({
      backendUrl: 'https://gw.example.com',
      pairingCode: code,
      sessionId: session.id,
      label: 'Acme',
    });
  });

  it('applies the documented secure defaults', async () => {
    const { luno, now } = harness();
    const { session } = await luno.pairing.createSession();

    expect(session.maxEnrollments).toBe(1);
    expect(session.requireApproval).toBe(false);
    expect(session.allowReplacement).toBe(false);
    expect(session.expiresAt).toBe(now() + 600_000);
  });

  it('honours a per-session policy override', async () => {
    const { luno } = harness();
    const { session } = await luno.pairing.createSession({
      policy: { expiresInMs: null, maxEnrollments: null },
    });

    expect(session.expiresAt).toBeNull();
    expect(session.maxEnrollments).toBeNull();
  });
});

describe('enroll', () => {
  it('approves a valid code and issues a working credential', async () => {
    const { luno } = harness();
    const { code } = await luno.pairing.createSession();

    const outcome = await luno.pairing.enroll(enrollRequest(code));
    expect(outcome.status).toBe('approved');
    if (outcome.status !== 'approved') return;

    expect(await luno.connections.authorize(outcome.credential)).not.toBeNull();
  });

  it('accepts the code however the operator typed it', async () => {
    const { luno } = harness();
    const { code } = await luno.pairing.createSession();
    const mangled = code.toLowerCase().replace('-', ' ');

    expect((await luno.pairing.enroll(enrollRequest(mangled))).status).toBe('approved');
  });

  it.each([
    ['an unknown code', 'ZZZZ-9999', 'invalid_code'],
    ['a blank code', '', 'invalid_code'],
  ])('rejects %s', async (_label, code, expected) => {
    const { luno } = harness();
    await luno.pairing.createSession();

    const outcome = await luno.pairing.enroll(enrollRequest(code));
    expect(outcome.status).toBe('rejected');
    if (outcome.status !== 'rejected') return;
    expect(outcome.code).toBe(expected);
  });

  it('rejects a code that has expired', async () => {
    const context = harness();
    const { code } = await context.luno.pairing.createSession();
    context.advance(600_001);

    const outcome = await context.luno.pairing.enroll(enrollRequest(code));
    expect(outcome.status === 'rejected' && outcome.code).toBe('session_expired');
  });

  it('rejects a code whose session was revoked', async () => {
    const { luno } = harness();
    const { code, session } = await luno.pairing.createSession();
    await luno.pairing.revokeSession(session.id);

    const outcome = await luno.pairing.enroll(enrollRequest(code));
    expect(outcome.status === 'rejected' && outcome.code).toBe('session_revoked');
  });

  it('rejects once the session is spent', async () => {
    const { luno } = harness();
    const { code } = await luno.pairing.createSession();
    await luno.pairing.enroll(enrollRequest(code));

    const outcome = await luno.pairing.enroll(
      enrollRequest(code, { deviceInfo: deviceInfo('install-2') }),
    );
    expect(outcome.status === 'rejected' && outcome.code).toBe('session_exhausted');
  });

  it('rejects a payload whose session id disagrees with its code', async () => {
    const { luno } = harness();
    const { code } = await luno.pairing.createSession();

    const outcome = await luno.pairing.enroll(enrollRequest(code, { sessionId: 'ses_other' }));
    expect(outcome.status === 'rejected' && outcome.code).toBe('invalid_code');
  });

  it('turns away a device that is already enrolled', async () => {
    const { luno } = harness();
    const first = await luno.pairing.createSession();
    await luno.pairing.enroll(enrollRequest(first.code));

    const second = await luno.pairing.createSession();
    const outcome = await luno.pairing.enroll(enrollRequest(second.code));
    expect(outcome.status === 'rejected' && outcome.code).toBe('already_enrolled');
  });

  it('gives the slot back when a device is turned away', async () => {
    const { luno } = harness();
    const first = await luno.pairing.createSession();
    await luno.pairing.enroll(enrollRequest(first.code));

    const second = await luno.pairing.createSession();
    await luno.pairing.enroll(enrollRequest(second.code));

    // The refused attempt must not have burned the session's only enrolment.
    const outcome = await luno.pairing.enroll(
      enrollRequest(second.code, { deviceInfo: deviceInfo('install-fresh') }),
    );
    expect(outcome.status).toBe('approved');
  });

  it('re-enrols the same device in place when replacement is allowed', async () => {
    const { luno } = harness();
    const first = await luno.pairing.createSession();
    const initial = await luno.pairing.enroll(enrollRequest(first.code));

    const second = await luno.pairing.createSession({ policy: { allowReplacement: true } });
    const replaced = await luno.pairing.enroll(enrollRequest(second.code));

    expect(initial.status).toBe('approved');
    expect(replaced.status).toBe('approved');
    if (initial.status !== 'approved' || replaced.status !== 'approved') return;

    expect(replaced.deviceId).toBe(initial.deviceId);
    expect(replaced.credential).not.toBe(initial.credential);
    // The superseded credential must stop working, or replacement would widen access.
    expect(await luno.connections.authorize(initial.credential)).toBeNull();
    expect(await luno.connections.authorize(replaced.credential)).not.toBeNull();
  });

  /**
   * The end-to-end version of the store's linearizability requirement: many nodes
   * submitting one single-use code must yield exactly one enrolled device.
   */
  it('admits exactly one device when enrolments race', async () => {
    const { luno } = harness();
    const { code } = await luno.pairing.createSession();

    const outcomes = await Promise.all(
      Array.from({ length: 25 }, (_, index) =>
        luno.pairing.enroll(enrollRequest(code, { deviceInfo: deviceInfo(`install-${index}`) })),
      ),
    );

    expect(outcomes.filter((outcome) => outcome.status === 'approved')).toHaveLength(1);
    expect(await luno.devices.list()).toHaveLength(1);
  });
});

describe('approval gate', () => {
  let context: ReturnType<typeof harness>;

  beforeEach(() => {
    context = harness({ pairing: { requireApproval: true } });
  });

  it('parks the device until an operator decides', async () => {
    const { code } = await context.luno.pairing.createSession();
    const outcome = await context.luno.pairing.enroll(enrollRequest(code));

    expect(outcome.status).toBe('pending');
    if (outcome.status !== 'pending') return;
    expect(outcome.retryAfterMs).toBeGreaterThanOrEqual(1_000);
    expect(await context.luno.devices.list()).toHaveLength(0);
  });

  it('uses an unguessable enrolment handle', async () => {
    const { code } = await context.luno.pairing.createSession();
    const outcome = await context.luno.pairing.enroll(enrollRequest(code));

    // /enroll/status hands a credential to whoever presents this, so it must not
    // be a guessable counter even though the spec calls it non-secret.
    if (outcome.status !== 'pending') throw new Error('expected pending');
    expect(outcome.enrollmentId.length).toBeGreaterThan(24);
  });

  it('issues a credential once approved', async () => {
    const { code } = await context.luno.pairing.createSession();
    const pending = await context.luno.pairing.enroll(enrollRequest(code));
    if (pending.status !== 'pending') throw new Error('expected pending');

    expect(
      (await context.luno.pairing.enrollStatus({
        enrollmentId: pending.enrollmentId,
        nonce: 'n',
        protocolVersion: 1,
      })).status,
    ).toBe('pending');

    await context.luno.pairing.approveEnrollment(pending.enrollmentId);
    const approved = await context.luno.pairing.enrollStatus({
      enrollmentId: pending.enrollmentId,
      nonce: 'n',
      protocolVersion: 1,
    });

    expect(approved.status).toBe('approved');
    if (approved.status !== 'approved') return;
    expect(await context.luno.connections.authorize(approved.credential)).not.toBeNull();
  });

  it('re-issues a credential when the node polls again after a lost reply', async () => {
    const { code } = await context.luno.pairing.createSession();
    const pending = await context.luno.pairing.enroll(enrollRequest(code));
    if (pending.status !== 'pending') throw new Error('expected pending');
    await context.luno.pairing.approveEnrollment(pending.enrollmentId);

    const poll = () =>
      context.luno.pairing.enrollStatus({
        enrollmentId: pending.enrollmentId,
        nonce: 'n',
        protocolVersion: 1,
      });

    const first = await poll();
    const second = await poll();
    expect(first.status).toBe('approved');
    expect(second.status).toBe('approved');
    if (first.status !== 'approved' || second.status !== 'approved') return;

    expect(second.deviceId).toBe(first.deviceId);
    expect(await context.luno.devices.list()).toHaveLength(1);
    expect(await context.luno.connections.authorize(second.credential)).not.toBeNull();
  });

  it('reports a denial and returns the slot to the session', async () => {
    const { code, session } = await context.luno.pairing.createSession();
    const pending = await context.luno.pairing.enroll(enrollRequest(code));
    if (pending.status !== 'pending') throw new Error('expected pending');

    await context.luno.pairing.denyEnrollment(pending.enrollmentId);
    expect(
      (await context.luno.pairing.enrollStatus({
        enrollmentId: pending.enrollmentId,
        nonce: 'n',
        protocolVersion: 1,
      })).status,
    ).toBe('denied');

    const reused = await context.store.pairingSessions.findById(session.id);
    expect(reused?.enrollmentsUsed).toBe(0);
  });
});
