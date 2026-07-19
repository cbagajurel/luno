import {
  classifyEnrollFailure,
  decodeEnrollResponse,
  type DeviceInfo,
  type EnrollRequest,
  type EnrollResponse,
  type PairingErrorCode,
} from '@luno/protocol';

/** `fetch` is declared structurally so this package needs no DOM or Node lib. */
declare const fetch: (
  url: string,
  init: { method: string; headers: Record<string, string>; body: string },
) => Promise<{ status: number; text(): Promise<string> }>;

/**
 * How the fake node reaches the backend's REST surface. `fetchTransport` drives a
 * real HTTP server; a test against the core in-process can pass a transport that
 * calls `luno.http.handle` directly, so the same driver covers both.
 */
export interface EnrollTransport {
  post(path: string, body: unknown): Promise<{ status: number; body: unknown }>;
}

export function fetchTransport(baseUrl: string): EnrollTransport {
  const base = baseUrl.replace(/\/+$/, '');
  return {
    async post(path, body) {
      const response = await fetch(base + path, {
        method: 'POST',
        headers: { 'content-type': 'application/json' },
        body: JSON.stringify(body),
      });
      const text = await response.text();
      let parsed: unknown = null;
      try {
        parsed = text ? JSON.parse(text) : null;
      } catch {
        parsed = text;
      }
      return { status: response.status, body: parsed };
    },
  };
}

export class EnrollError extends Error {
  readonly code: PairingErrorCode;
  readonly rawCode: string | undefined;

  constructor(code: PairingErrorCode, message: string, rawCode?: string) {
    super(message);
    this.name = 'EnrollError';
    this.code = code;
    this.rawCode = rawCode;
  }
}

export const defaultDeviceInfo = (installId: string): DeviceInfo => ({
  model: 'Pixel 7 (fake)',
  manufacturer: 'Google',
  androidSdk: 34,
  appVersion: '0.1.0-sim',
  installId,
  platform: 'android',
});

export interface EnrollResult {
  deviceId: string;
  credential: string;
  wsUrl: string | null;
}

async function submit(transport: EnrollTransport, path: string, body: unknown): Promise<EnrollResponse> {
  const { status, body: responseBody } = await transport.post(path, body);
  if (status < 200 || status >= 300) {
    const failure = classifyEnrollFailure(status, responseBody);
    throw new EnrollError(failure.error, failure.message, failure.rawCode);
  }
  return decodeEnrollResponse(responseBody);
}

/**
 * Enrols like the real node: POST /enroll, and if the backend answers `pending`,
 * poll /enroll/status until it resolves. Mirrors the node's own flow so a policy
 * that gates on approval is exercised end to end.
 */
export async function enrollNode(
  transport: EnrollTransport,
  pairingCode: string,
  options: {
    installId?: string;
    deviceInfo?: DeviceInfo;
    sessionId?: string;
    /** Cap on /enroll/status polls before giving up on a pending enrolment. */
    maxPolls?: number;
    sleep?: (ms: number) => Promise<void>;
  } = {},
): Promise<EnrollResult> {
  const deviceInfo = options.deviceInfo ?? defaultDeviceInfo(options.installId ?? 'install-fake');
  const request: EnrollRequest = {
    pairingCode,
    nonce: `nonce-${deviceInfo.installId}`,
    protocolVersion: 1,
    deviceInfo,
    ...(options.sessionId ? { sessionId: options.sessionId } : {}),
  };

  let response = await submit(transport, '/enroll', request);

  const maxPolls = options.maxPolls ?? 20;
  const sleep = options.sleep ?? (() => Promise.resolve());
  let polls = 0;

  while (response.status === 'pending') {
    if (!response.enrollmentId) throw new EnrollError('server', 'pending response had no enrollmentId');
    if (polls >= maxPolls) throw new EnrollError('server', 'gave up waiting for approval');
    polls += 1;
    await sleep(response.retryAfterMs ?? 1_000);
    response = await submit(transport, '/enroll/status', {
      enrollmentId: response.enrollmentId,
      nonce: `nonce-${deviceInfo.installId}-${polls}`,
      protocolVersion: 1,
    });
  }

  if (response.status === 'denied') throw new EnrollError('approval_denied', 'operator denied the device');
  if (!response.deviceId || !response.credential) {
    throw new EnrollError('server', 'approved response missing credential');
  }

  return {
    deviceId: response.deviceId,
    credential: response.credential,
    wsUrl: response.wsUrl ?? null,
  };
}
