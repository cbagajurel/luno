import { describe, expect, it } from 'vitest';
import type { LunoStore, PairingSessionRecord } from '../src';

const NOW = 1_700_000_000_000;

const session = (overrides: Partial<PairingSessionRecord> = {}): PairingSessionRecord => ({
  id: 'ses_1',
  codeHash: 'hash-1',
  label: null,
  createdAt: NOW,
  createdBy: null,
  expiresAt: NOW + 600_000,
  maxEnrollments: 1,
  enrollmentsUsed: 0,
  requireApproval: false,
  allowReplacement: false,
  revokedAt: null,
  metadata: null,
  ...overrides,
});

/**
 * The contract every `LunoStore` implementation must satisfy. Exported so a
 * future `@luno/store-postgres` or `@luno/store-firestore` can run the identical
 * suite against real infrastructure — the claim that business logic survives a
 * database swap is only worth as much as this suite.
 */
export function describeStoreConformance(name: string, makeStore: () => LunoStore): void {
  describe(`${name}: pairing session claims`, () => {
    it('claims a fresh session', async () => {
      const store = makeStore();
      await store.pairingSessions.create(session());

      const outcome = await store.pairingSessions.claim({ sessionId: 'ses_1', now: NOW });
      expect(outcome.status).toBe('claimed');
      if (outcome.status !== 'claimed') return;
      expect(outcome.session.enrollmentsUsed).toBe(1);
    });

    it('reports an unknown session rather than throwing', async () => {
      const store = makeStore();
      expect((await store.pairingSessions.claim({ sessionId: 'nope', now: NOW })).status).toBe(
        'not_found',
      );
    });

    it('refuses a session past its expiry', async () => {
      const store = makeStore();
      await store.pairingSessions.create(session({ expiresAt: NOW - 1 }));
      expect((await store.pairingSessions.claim({ sessionId: 'ses_1', now: NOW })).status).toBe(
        'expired',
      );
    });

    it('refuses a revoked session', async () => {
      const store = makeStore();
      await store.pairingSessions.create(session({ revokedAt: NOW - 1 }));
      expect((await store.pairingSessions.claim({ sessionId: 'ses_1', now: NOW })).status).toBe(
        'revoked',
      );
    });

    it('refuses a session with no slots left', async () => {
      const store = makeStore();
      await store.pairingSessions.create(session({ maxEnrollments: 2, enrollmentsUsed: 2 }));
      expect((await store.pairingSessions.claim({ sessionId: 'ses_1', now: NOW })).status).toBe(
        'exhausted',
      );
    });

    it('never exhausts a session with no enrolment limit', async () => {
      const store = makeStore();
      await store.pairingSessions.create(session({ maxEnrollments: null }));

      for (let attempt = 0; attempt < 25; attempt += 1) {
        expect((await store.pairingSessions.claim({ sessionId: 'ses_1', now: NOW })).status).toBe(
          'claimed',
        );
      }
    });

    it('returns a slot to the session on release', async () => {
      const store = makeStore();
      await store.pairingSessions.create(session());

      await store.pairingSessions.claim({ sessionId: 'ses_1', now: NOW });
      expect((await store.pairingSessions.claim({ sessionId: 'ses_1', now: NOW })).status).toBe(
        'exhausted',
      );

      await store.pairingSessions.release('ses_1');
      expect((await store.pairingSessions.claim({ sessionId: 'ses_1', now: NOW })).status).toBe(
        'claimed',
      );
    });

    /**
     * The reason `claim` exists at all. A read-then-write implementation passes
     * every sequential test above and fails this one — which is exactly the bug
     * that would otherwise surface in production as two devices sharing a
     * single-use pairing session.
     */
    it.each([1, 3, 10])(
      'admits at most %i device(s) when claims race',
      async (maxEnrollments) => {
        const store = makeStore();
        await store.pairingSessions.create(session({ maxEnrollments }));

        const outcomes = await Promise.all(
          Array.from({ length: 60 }, () =>
            store.pairingSessions.claim({ sessionId: 'ses_1', now: NOW }),
          ),
        );

        const claimed = outcomes.filter((outcome) => outcome.status === 'claimed');
        expect(claimed).toHaveLength(maxEnrollments);
        expect(outcomes.filter((outcome) => outcome.status === 'exhausted')).toHaveLength(
          60 - maxEnrollments,
        );

        const after = await store.pairingSessions.findById('ses_1');
        expect(after?.enrollmentsUsed).toBe(maxEnrollments);
      },
    );

    it('finds a session by its code digest', async () => {
      const store = makeStore();
      await store.pairingSessions.create(session({ codeHash: 'digest-xyz' }));

      expect((await store.pairingSessions.findByCodeHash('digest-xyz'))?.id).toBe('ses_1');
      expect(await store.pairingSessions.findByCodeHash('other')).toBeNull();
    });

    it('does not let a caller mutate stored state through a returned record', async () => {
      const store = makeStore();
      await store.pairingSessions.create(session());

      const first = await store.pairingSessions.findById('ses_1');
      if (first) first.enrollmentsUsed = 99;

      expect((await store.pairingSessions.findById('ses_1'))?.enrollmentsUsed).toBe(0);
    });
  });

  describe(`${name}: messages`, () => {
    const message = (id: string, status: 'pending' | 'delivered') => ({
      id,
      deviceId: 'dev_1',
      to: '+1',
      body: 'x',
      subscriptionId: null,
      ref: null,
      status,
      commandId: `cmd_${id}`,
      nodeMessageId: null,
      parts: [],
      error: null,
      createdAt: NOW,
      updatedAt: NOW,
    });

    it('lists only non-terminal messages as outstanding', async () => {
      const store = makeStore();
      await store.messages.create(message('msg_1', 'pending'));
      await store.messages.create(message('msg_2', 'delivered'));

      const outstanding = await store.messages.listOutstanding('dev_1');
      expect(outstanding.map((record) => record.id)).toEqual(['msg_1']);
    });

    it('finds a message by the command id the node acks on', async () => {
      const store = makeStore();
      await store.messages.create(message('msg_1', 'pending'));

      expect((await store.messages.findByCommandId('cmd_msg_1'))?.id).toBe('msg_1');
    });
  });
}
