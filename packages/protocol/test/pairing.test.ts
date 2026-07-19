import { describe, expect, it } from 'vitest';
import {
  DecodeError,
  ENROLL_STATUS,
  PAIRING_REJECTION_CODES,
  REJECTED_HTTP_STATUSES,
  approvedEnrollResponse,
  clampRetryAfterMs,
  classifyEnrollFailure,
  decodeEnrollRequest,
  decodeEnrollResponse,
  decodeEnrollStatusRequest,
  deniedEnrollResponse,
  enrollRejection,
  parseWireCode,
  pendingEnrollResponse,
  rejectionHttpStatus,
  type PairingRejectionCode,
} from '../src';

describe('parseWireCode', () => {
  it('maps the documented rejection codes', () => {
    expect(parseWireCode('session_expired')).toBe('session_expired');
    expect(parseWireCode('  SESSION_REVOKED  ')).toBe('session_revoked');
  });

  it('preserves an unrecognised token as unknown so backends can add reasons', () => {
    expect(parseWireCode('quota_exceeded')).toBe('unknown');
    expect(parseWireCode('org.policy:blocked')).toBe('unknown');
  });

  it('refuses prose, which is a message rather than a code', () => {
    expect(parseWireCode('pairing code already used')).toBeNull();
    expect(parseWireCode('Invalid Code')).toBeNull();
    expect(parseWireCode('')).toBeNull();
    expect(parseWireCode(null)).toBeNull();
    expect(parseWireCode(undefined)).toBeNull();
  });
});

describe('rejectionHttpStatus', () => {
  /**
   * The property that keeps old nodes correct: whatever status we attach to a
   * rejection, a node that has never heard of the code still classifies the
   * attempt as "code rejected" from the status alone.
   */
  it('always lands on a status the node treats as a rejection', () => {
    for (const code of PAIRING_REJECTION_CODES) {
      expect(REJECTED_HTTP_STATUSES).toContain(rejectionHttpStatus(code));
    }
  });

  it('builds a rejection body carrying the code and message', () => {
    expect(enrollRejection('session_exhausted', 'no enrolments left')).toEqual({
      status: 409,
      body: { error: 'session_exhausted', message: 'no enrolments left' },
    });
  });
});

describe('decodeEnrollRequest', () => {
  const valid = {
    protocolVersion: 1,
    pairingCode: 'ABCD-1234',
    nonce: 'iA9',
    sessionId: 'ses_9f3',
    deviceInfo: {
      model: 'Pixel 8',
      manufacturer: 'Google',
      androidSdk: 34,
      appVersion: '1.0.0',
      installId: 'b2c1',
      platform: 'android',
    },
  };

  it('accepts a well-formed request', () => {
    expect(decodeEnrollRequest(valid)).toEqual(valid);
  });

  it('defaults platform and protocolVersion', () => {
    const { platform: _platform, ...deviceInfo } = valid.deviceInfo;
    const { protocolVersion: _version, ...rest } = valid;
    const decoded = decodeEnrollRequest({ ...rest, deviceInfo });

    expect(decoded.deviceInfo.platform).toBe('android');
    expect(decoded.protocolVersion).toBe(1);
  });

  it.each([
    ['pairingCode', { ...valid, pairingCode: undefined }],
    ['nonce', { ...valid, nonce: undefined }],
    ['deviceInfo', { ...valid, deviceInfo: undefined }],
    ['installId', { ...valid, deviceInfo: { ...valid.deviceInfo, installId: undefined } }],
  ])('rejects a request missing %s', (_field, body) => {
    expect(() => decodeEnrollRequest(body)).toThrow(DecodeError);
  });

  it('reads a status-poll request', () => {
    expect(decodeEnrollStatusRequest({ enrollmentId: 'enr_7', nonce: 'x' })).toEqual({
      enrollmentId: 'enr_7',
      nonce: 'x',
      protocolVersion: 1,
    });
  });
});

describe('enroll responses', () => {
  it('builds an approved response', () => {
    expect(approvedEnrollResponse({ deviceId: 'dev_9', credential: 'tok', wsUrl: 'wss://x/ws' })).toEqual({
      status: 'approved',
      deviceId: 'dev_9',
      credential: 'tok',
      wsUrl: 'wss://x/ws',
    });
  });

  it('omits wsUrl when the node should derive it from the enrolment host', () => {
    expect(approvedEnrollResponse({ deviceId: 'dev_9', credential: 'tok' })).toEqual({
      status: 'approved',
      deviceId: 'dev_9',
      credential: 'tok',
    });
  });

  it('clamps retryAfterMs into the window the node honours', () => {
    expect(clampRetryAfterMs(0)).toBe(1_000);
    expect(clampRetryAfterMs(5_000)).toBe(5_000);
    expect(clampRetryAfterMs(600_000)).toBe(60_000);
    expect(pendingEnrollResponse({ enrollmentId: 'enr_7', retryAfterMs: 1 })['retryAfterMs']).toBe(1_000);
  });

  it('builds a denied response', () => {
    expect(deniedEnrollResponse()).toEqual({ status: 'denied' });
  });

  it('treats a missing status as approved, for servers predating the field', () => {
    expect(decodeEnrollResponse({ deviceId: 'dev_9', credential: 'tok' })).toEqual({
      status: ENROLL_STATUS.APPROVED,
      deviceId: 'dev_9',
      credential: 'tok',
    });
  });
});

describe('classifyEnrollFailure', () => {
  it("lets the backend's own code win over the HTTP status", () => {
    expect(classifyEnrollFailure(500, { error: 'session_expired', message: 'too late' })).toEqual({
      error: 'session_expired',
      message: 'too late',
      rawCode: 'session_expired',
    });
  });

  it('keeps an unrecognised code reaching the UI with its message', () => {
    expect(classifyEnrollFailure(403, { error: 'quota_exceeded', message: 'buy more' })).toEqual({
      error: 'unknown',
      message: 'buy more',
      rawCode: 'quota_exceeded',
    });
  });

  it('falls back to the status when the body carries prose instead of a code', () => {
    expect(classifyEnrollFailure(409, { error: 'pairing code already used' })).toEqual({
      error: 'invalid_code',
      message: 'pairing code already used',
    });
  });

  it('treats an unclassifiable server fault as a server error', () => {
    expect(classifyEnrollFailure(500, 'gateway exploded')).toEqual({
      error: 'server',
      message: 'enroll failed (500)',
    });
  });

  it('round-trips every rejection this SDK can emit', () => {
    for (const code of PAIRING_REJECTION_CODES) {
      const { status, body } = enrollRejection(code as PairingRejectionCode, 'nope');
      expect(classifyEnrollFailure(status, body).error).toBe(code);
    }
  });
});
