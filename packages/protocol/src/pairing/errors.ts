/**
 * Rejection codes a backend may send on `/enroll` (docs/pairing.md). A backend
 * is free to invent others — the node preserves an unrecognised token as
 * `unknown` and shows the accompanying message — but these are the ones with
 * agreed meaning.
 */
export const PAIRING_REJECTION_CODES = [
  'invalid_code',
  'session_expired',
  'session_exhausted',
  'session_revoked',
  'already_enrolled',
  'approval_denied',
  'policy_rejected',
] as const;

export type PairingRejectionCode = (typeof PAIRING_REJECTION_CODES)[number];

/**
 * The node's full classification, which additionally covers failures it decides
 * locally rather than receiving: a non-HTTPS endpoint, a dead network, its own
 * bugs. A backend never sends those — they are here so client SDKs and the
 * conformance suite can speak the same taxonomy as the node.
 */
export const PAIRING_ERROR_CODES = [
  ...PAIRING_REJECTION_CODES,
  'not_secure',
  'network',
  'server',
  'internal',
  'unknown',
] as const;

export type PairingErrorCode = (typeof PAIRING_ERROR_CODES)[number];

/**
 * HTTP statuses the node treats as "pairing code rejected" when it cannot
 * classify on the body. Every status in `rejectionHttpStatus` is drawn from this
 * set on purpose: a backend that sends a brand-new `error` token still gets the
 * right *behaviour* from an older node, which falls back to the status.
 */
export const REJECTED_HTTP_STATUSES: readonly number[] = [400, 401, 403, 409, 410];

const HTTP_STATUS_BY_CODE: Record<PairingRejectionCode, number> = {
  invalid_code: 401,
  session_expired: 410,
  session_exhausted: 409,
  session_revoked: 403,
  already_enrolled: 409,
  approval_denied: 403,
  policy_rejected: 403,
};

export const rejectionHttpStatus = (code: PairingRejectionCode): number =>
  HTTP_STATUS_BY_CODE[code];

const KNOWN = new Set<string>(PAIRING_ERROR_CODES);

/**
 * `error` must be a lowercase token. Plenty of servers put a whole sentence in
 * that field; treating prose as a code would flatten a plainly-rejected pairing
 * attempt into `unknown`, so anything that isn't token-shaped returns null and
 * the caller falls back to the HTTP status.
 */
const TOKEN = /^[a-z0-9][a-z0-9_.:-]*$/;

export function parseWireCode(code: string | null | undefined): PairingErrorCode | null {
  if (typeof code !== 'string') return null;
  const normalized = code.trim().toLowerCase();
  if (!TOKEN.test(normalized)) return null;
  return KNOWN.has(normalized) ? (normalized as PairingErrorCode) : 'unknown';
}

export const isRejectionCode = (code: string): code is PairingRejectionCode =>
  (PAIRING_REJECTION_CODES as readonly string[]).includes(code);
