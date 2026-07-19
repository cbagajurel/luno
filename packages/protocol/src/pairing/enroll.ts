import { compact, type JsonObject } from './../json';
import {
  asJsonObject,
  intWithDefault,
  optionalInt,
  optionalString,
  requireInt,
  requireString,
} from './../internal/decode';
import {
  REJECTED_HTTP_STATUSES,
  parseWireCode,
  type PairingErrorCode,
  type PairingRejectionCode,
  rejectionHttpStatus,
} from './errors';

export const ENROLL_PROTOCOL_VERSION = 1;

export const ENROLL_STATUS = {
  APPROVED: 'approved',
  PENDING: 'pending',
  DENIED: 'denied',
} as const;

export type EnrollStatus = (typeof ENROLL_STATUS)[keyof typeof ENROLL_STATUS];

/** The node clamps `retryAfterMs` into this window; emitting outside it is pointless. */
export const RETRY_AFTER_MIN_MS = 1_000;
export const RETRY_AFTER_MAX_MS = 60_000;

export const clampRetryAfterMs = (ms: number): number =>
  Math.min(RETRY_AFTER_MAX_MS, Math.max(RETRY_AFTER_MIN_MS, Math.round(ms)));

export interface DeviceInfo {
  model: string;
  manufacturer: string;
  androidSdk: number;
  appVersion: string;
  installId: string;
  platform: string;
}

export interface EnrollRequest {
  pairingCode: string;
  nonce: string;
  deviceInfo: DeviceInfo;
  protocolVersion: number;
  sessionId?: string;
  publicKey?: string;
}

export interface EnrollStatusRequest {
  enrollmentId: string;
  nonce: string;
  protocolVersion: number;
}

export interface EnrollResponse {
  status: EnrollStatus | string;
  deviceId?: string;
  credential?: string;
  wsUrl?: string;
  enrollmentId?: string;
  retryAfterMs?: number;
}

export interface EnrollErrorBody {
  error?: string;
  message?: string;
}

const decodeDeviceInfo = (o: JsonObject): DeviceInfo => ({
  model: requireString(o, 'model'),
  manufacturer: requireString(o, 'manufacturer'),
  androidSdk: requireInt(o, 'androidSdk'),
  appVersion: requireString(o, 'appVersion'),
  installId: requireString(o, 'installId'),
  platform: optionalString(o, 'platform') ?? 'android',
});

/**
 * Validates an incoming `/enroll` body. Throws `DecodeError` on anything the
 * node would not have produced, which an adapter should surface as 400 — the
 * required fields here are exactly those the node always sends.
 */
export function decodeEnrollRequest(value: unknown): EnrollRequest {
  const o = asJsonObject(value, 'enroll request');
  const request: EnrollRequest = {
    pairingCode: requireString(o, 'pairingCode'),
    nonce: requireString(o, 'nonce'),
    deviceInfo: decodeDeviceInfo(asJsonObject(o['deviceInfo'], "'deviceInfo'")),
    protocolVersion: intWithDefault(o, 'protocolVersion', ENROLL_PROTOCOL_VERSION),
  };
  const sessionId = optionalString(o, 'sessionId');
  const publicKey = optionalString(o, 'publicKey');
  if (sessionId !== undefined) request.sessionId = sessionId;
  if (publicKey !== undefined) request.publicKey = publicKey;
  return request;
}

export function decodeEnrollStatusRequest(value: unknown): EnrollStatusRequest {
  const o = asJsonObject(value, 'enroll status request');
  return {
    enrollmentId: requireString(o, 'enrollmentId'),
    nonce: requireString(o, 'nonce'),
    protocolVersion: intWithDefault(o, 'protocolVersion', ENROLL_PROTOCOL_VERSION),
  };
}

export const approvedEnrollResponse = (params: {
  deviceId: string;
  credential: string;
  wsUrl?: string;
}): JsonObject =>
  compact({
    status: ENROLL_STATUS.APPROVED,
    deviceId: params.deviceId,
    credential: params.credential,
    wsUrl: params.wsUrl,
  });

export const pendingEnrollResponse = (params: {
  enrollmentId: string;
  retryAfterMs: number;
}): JsonObject =>
  compact({
    status: ENROLL_STATUS.PENDING,
    enrollmentId: params.enrollmentId,
    retryAfterMs: clampRetryAfterMs(params.retryAfterMs),
  });

export const deniedEnrollResponse = (): JsonObject => compact({ status: ENROLL_STATUS.DENIED });

/**
 * A rejection body plus the status to send it with. The status always lands in
 * `REJECTED_HTTP_STATUSES`, so a node that has never heard of `code` still
 * classifies the attempt as rejected rather than as a server fault.
 */
export function enrollRejection(
  code: PairingRejectionCode,
  message: string,
): { status: number; body: JsonObject } {
  return { status: rejectionHttpStatus(code), body: compact({ error: code, message }) };
}

/** Missing `status` means approved, so a two-field response from an older server still works. */
export function decodeEnrollResponse(value: unknown): EnrollResponse {
  const o = asJsonObject(value, 'enroll response');
  const response: EnrollResponse = {
    status: optionalString(o, 'status') ?? ENROLL_STATUS.APPROVED,
  };
  const deviceId = optionalString(o, 'deviceId');
  const credential = optionalString(o, 'credential');
  const wsUrl = optionalString(o, 'wsUrl');
  const enrollmentId = optionalString(o, 'enrollmentId');
  const retryAfterMs = optionalInt(o, 'retryAfterMs');
  if (deviceId !== undefined) response.deviceId = deviceId;
  if (credential !== undefined) response.credential = credential;
  if (wsUrl !== undefined) response.wsUrl = wsUrl;
  if (enrollmentId !== undefined) response.enrollmentId = enrollmentId;
  if (retryAfterMs !== undefined) response.retryAfterMs = retryAfterMs;
  return response;
}

export interface EnrollFailure {
  error: PairingErrorCode;
  message: string;
  rawCode?: string;
}

/**
 * Classifies a failed enrolment exactly as the node does: the backend's own
 * `error` token wins, the HTTP status is only a fallback for servers that do not
 * send one, and an unrecognised token survives as `unknown` plus its raw string
 * so a new verdict reaches the UI without a node release.
 */
export function classifyEnrollFailure(status: number, body: unknown): EnrollFailure {
  let parsed: EnrollErrorBody | null = null;
  try {
    const o = asJsonObject(body, 'error body');
    parsed = {
      error: optionalString(o, 'error') ?? undefined,
      message: optionalString(o, 'message') ?? undefined,
    };
  } catch {
    parsed = null;
  }

  const wireCode = parsed?.error;
  const mapped = parseWireCode(wireCode);
  const failure: EnrollFailure = {
    error: mapped ?? (REJECTED_HTTP_STATUSES.includes(status) ? 'invalid_code' : 'server'),
    message: parsed?.message ?? wireCode ?? `enroll failed (${status})`,
  };
  // Only a real token is worth passing on; prose is already the message.
  if (mapped !== null && wireCode !== undefined) failure.rawCode = wireCode;
  return failure;
}
