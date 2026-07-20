import {
  buildPairingJson,
  buildPairingUri,
  clampRetryAfterMs,
  type DeviceInfo,
  type EnrollRequest,
  type EnrollStatusRequest,
  type PairingRejectionCode,
} from '@luno-oss/protocol';
import { auditEvent, type CoreContext } from '../context';
import { LunoError } from '../domain/errors';
import {
  formatPairingCode,
  pairingCodeAlphabet,
  pairingCodeLength,
  type PairingPolicy,
} from '../domain/pairing-session';
import { hashDeviceCredential, hashPairingCode } from './hashing';
import { randomCode, randomToken } from '../runtime/tokens';
import type { DeviceRecord, EnrollmentRecord, PairingSessionRecord } from '../ports/store';

export interface CreateSessionInput {
  label?: string;
  createdBy?: string;
  metadata?: Record<string, string>;
  policy?: Partial<PairingPolicy>;
  /** Base URL the node should enrol against; needed to build a scannable payload. */
  backendUrl?: string;
}

export interface CreatedSession {
  session: PairingSessionRecord;
  /** Plaintext, returned exactly once — only its hash is stored. */
  code: string;
  qrUri: string | null;
  qrJson: string | null;
}

export type EnrollOutcome =
  | { status: 'approved'; deviceId: string; credential: string; wsUrl: string | null }
  | { status: 'pending'; enrollmentId: string; retryAfterMs: number }
  | { status: 'denied' }
  | { status: 'rejected'; code: PairingRejectionCode; message: string };

const reject = (code: PairingRejectionCode, message: string): EnrollOutcome => ({
  status: 'rejected',
  code,
  message,
});

export function pairingService(context: CoreContext) {
  const hashCode = (code: string) => hashPairingCode(context, code);
  const hashCredential = (credential: string) => hashDeviceCredential(context, credential);

  async function mintDevice(input: {
    sessionId: string;
    installId: string;
    info: DeviceInfo;
  }): Promise<{ device: DeviceRecord; credential: string }> {
    const credential = randomToken(context.crypto, 32);
    const now = context.clock.now();
    const device: DeviceRecord = {
      id: context.ids.newId('dev'),
      sessionId: input.sessionId,
      credentialHash: await hashCredential(credential),
      installId: input.installId,
      info: input.info,
      status: 'active',
      phase: 'offline',
      pairedAt: now,
      lastSeenAt: null,
      revokedAt: null,
    };
    await context.store.devices.create(device);
    await auditEvent(context, {
      deviceId: device.id,
      direction: 'system',
      kind: 'system',
      type: 'device.enrolled',
      payload: { sessionId: input.sessionId, installId: input.installId },
    });
    await context.hooks.emit('device.enrolled', { device });
    return { device, credential };
  }

  /** Re-issues a credential for a device that already exists, without a new identity. */
  async function reissueCredential(device: DeviceRecord): Promise<string> {
    const credential = randomToken(context.crypto, 32);
    await context.store.devices.update(device.id, {
      credentialHash: await hashCredential(credential),
      status: 'active',
      revokedAt: null,
    });
    return credential;
  }

  async function resolveSession(
    request: EnrollRequest,
  ): Promise<{ session: PairingSessionRecord } | { rejection: EnrollOutcome }> {
    const codeHash = await hashCode(request.pairingCode);
    const session = await context.store.pairingSessions.findByCodeHash(codeHash);
    if (!session) return { rejection: reject('invalid_code', 'no such pairing session') };

    // A payload whose session id disagrees with the code it carries is not a
    // session we should trust either half of.
    if (request.sessionId && request.sessionId !== session.id) {
      return { rejection: reject('invalid_code', 'pairing code does not match the session') };
    }
    return { session };
  }

  return {
    async createSession(input: CreateSessionInput = {}): Promise<CreatedSession> {
      const policy: PairingPolicy = { ...context.policy, ...input.policy };
      const now = context.clock.now();
      const code = formatPairingCode(
        randomCode(context.crypto, pairingCodeAlphabet(), pairingCodeLength()),
      );

      const session: PairingSessionRecord = {
        id: context.ids.newId('ses'),
        codeHash: await hashCode(code),
        label: input.label ?? null,
        createdAt: now,
        createdBy: input.createdBy ?? null,
        expiresAt: policy.expiresInMs === null ? null : now + policy.expiresInMs,
        maxEnrollments: policy.maxEnrollments,
        enrollmentsUsed: 0,
        requireApproval: policy.requireApproval,
        allowReplacement: policy.allowReplacement,
        revokedAt: null,
        metadata: input.metadata ?? null,
      };
      await context.store.pairingSessions.create(session);
      await auditEvent(context, {
        deviceId: null,
        direction: 'system',
        kind: 'system',
        type: 'session.created',
        payload: { sessionId: session.id, createdBy: session.createdBy },
      });

      const payload = input.backendUrl
        ? { backendUrl: input.backendUrl, pairingCode: code, sessionId: session.id, ...(input.label ? { label: input.label } : {}) }
        : null;

      return {
        session,
        code,
        qrUri: payload ? buildPairingUri(payload) : null,
        qrJson: payload ? buildPairingJson(payload) : null,
      };
    },

    async enroll(request: EnrollRequest): Promise<EnrollOutcome> {
      const resolved = await resolveSession(request);
      if ('rejection' in resolved) return resolved.rejection;
      const { session } = resolved;

      const claim = await context.store.pairingSessions.claim({
        sessionId: session.id,
        now: context.clock.now(),
      });

      switch (claim.status) {
        case 'not_found':
          return reject('invalid_code', 'no such pairing session');
        case 'revoked':
          return reject('session_revoked', 'this pairing session was revoked');
        case 'expired':
          return reject('session_expired', 'this pairing session has expired');
        case 'exhausted':
          return reject('session_exhausted', 'this pairing session has no enrolments left');
        case 'claimed':
          break;
      }

      const existing = await context.store.devices.findByInstallId(request.deviceInfo.installId);
      if (existing && !claim.session.allowReplacement) {
        // The slot was taken before we could know this, so hand it back rather
        // than burning an enrolment on a device we are about to turn away.
        await context.store.pairingSessions.release(session.id);
        return reject('already_enrolled', 'this device is already enrolled');
      }

      if (claim.session.requireApproval) {
        const enrollment: EnrollmentRecord = {
          // High entropy on purpose: docs/pairing.md calls enrollmentId a
          // non-secret handle, but /enroll/status hands a credential to whoever
          // presents it, so a guessable value would be an enrolment bypass.
          id: `enr_${randomToken(context.crypto, 24)}`,
          sessionId: session.id,
          installId: request.deviceInfo.installId,
          info: request.deviceInfo,
          status: 'pending',
          createdAt: context.clock.now(),
          decidedAt: null,
          deviceId: null,
        };
        await context.store.enrollments.create(enrollment);
        await context.hooks.emit('enrollment.pending', { enrollment });
        return {
          status: 'pending',
          enrollmentId: enrollment.id,
          retryAfterMs: clampRetryAfterMs(context.pendingRetryAfterMs),
        };
      }

      if (existing) {
        const credential = await reissueCredential(existing);
        return {
          status: 'approved',
          deviceId: existing.id,
          credential,
          wsUrl: context.wsUrl,
        };
      }

      const { device, credential } = await mintDevice({
        sessionId: session.id,
        installId: request.deviceInfo.installId,
        info: request.deviceInfo,
      });
      return { status: 'approved', deviceId: device.id, credential, wsUrl: context.wsUrl };
    },

    async enrollStatus(request: EnrollStatusRequest): Promise<EnrollOutcome> {
      const enrollment = await context.store.enrollments.findById(request.enrollmentId);
      if (!enrollment) return reject('invalid_code', 'no such enrolment');

      switch (enrollment.status) {
        case 'pending':
          return {
            status: 'pending',
            enrollmentId: enrollment.id,
            retryAfterMs: clampRetryAfterMs(context.pendingRetryAfterMs),
          };

        case 'denied':
          return { status: 'denied' };

        case 'approved': {
          const { device, credential } = await mintDevice({
            sessionId: enrollment.sessionId,
            installId: enrollment.installId,
            info: enrollment.info,
          });
          await context.store.enrollments.update(enrollment.id, {
            status: 'completed',
            deviceId: device.id,
          });
          return { status: 'approved', deviceId: device.id, credential, wsUrl: context.wsUrl };
        }

        case 'completed': {
          // The node polled again, which means it never received the credential
          // we issued. Re-issuing is what makes a lost response recoverable; the
          // old one stops working, so a dropped reply cannot strand the device.
          const device = enrollment.deviceId
            ? await context.store.devices.findById(enrollment.deviceId)
            : null;
          if (!device) return reject('policy_rejected', 'enrolled device no longer exists');
          const credential = await reissueCredential(device);
          return { status: 'approved', deviceId: device.id, credential, wsUrl: context.wsUrl };
        }
      }
    },

    listSessions: (): Promise<PairingSessionRecord[]> => context.store.pairingSessions.list(),

    async revokeSession(sessionId: string): Promise<void> {
      const session = await context.store.pairingSessions.findById(sessionId);
      if (!session) throw LunoError.notFound('pairing session');
      await context.store.pairingSessions.revoke(sessionId, context.clock.now());
      await auditEvent(context, {
        deviceId: null,
        direction: 'system',
        kind: 'system',
        type: 'session.revoked',
        payload: { sessionId },
      });
    },

    listEnrollments: (): Promise<EnrollmentRecord[]> => context.store.enrollments.list(),

    async approveEnrollment(enrollmentId: string): Promise<void> {
      const enrollment = await context.store.enrollments.findById(enrollmentId);
      if (!enrollment) throw LunoError.notFound('enrolment');
      if (enrollment.status !== 'pending') {
        throw new LunoError('conflict', `enrolment is already ${enrollment.status}`);
      }
      await context.store.enrollments.update(enrollmentId, {
        status: 'approved',
        decidedAt: context.clock.now(),
      });
    },

    async denyEnrollment(enrollmentId: string): Promise<void> {
      const enrollment = await context.store.enrollments.findById(enrollmentId);
      if (!enrollment) throw LunoError.notFound('enrolment');
      if (enrollment.status !== 'pending') {
        throw new LunoError('conflict', `enrolment is already ${enrollment.status}`);
      }
      await context.store.enrollments.update(enrollmentId, {
        status: 'denied',
        decidedAt: context.clock.now(),
      });
      // A denied device never became one, so give its slot back to the session.
      await context.store.pairingSessions.release(enrollment.sessionId);
    },

    verifyCredential: (credential: string): Promise<string> => hashCredential(credential),
  };
}

export type PairingService = ReturnType<typeof pairingService>;
