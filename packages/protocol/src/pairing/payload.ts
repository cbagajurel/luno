export const PAIRING_PAYLOAD_VERSION = 1;
export const PAIRING_SCHEME_PREFIX = 'luno://pair';

/**
 * Everything a node needs to attempt an enrolment, however it was delivered.
 * Deliberately thin: it carries the *inputs* to enrolment and none of the policy
 * governing it, so a stale QR code cannot assert its own validity.
 */
export interface PairingPayload {
  backendUrl: string;
  pairingCode: string;
  sessionId?: string;
  label?: string;
  pin?: string;
}

export type PairingPayloadResult =
  | { status: 'ok'; payload: PairingPayload }
  /** A newer payload format. Tell the operator to update rather than guess at the fields. */
  | { status: 'unsupported_version'; version: number }
  | { status: 'malformed'; reason: string };

const malformed = (reason: string): PairingPayloadResult => ({ status: 'malformed', reason });

const INTEGER = /^[+-]?\d+$/;

/** Mirrors Kotlin `toIntOrNull` / `JsonPrimitive.intOrNull`, which both accept a numeric string. */
function coerceInt(value: unknown): number | null {
  if (typeof value === 'number') return Number.isSafeInteger(value) ? value : null;
  if (typeof value !== 'string') return null;
  const trimmed = value.trim();
  return INTEGER.test(trimmed) ? Number(trimmed) : null;
}

const clean = (value: string | undefined): string | undefined => {
  const trimmed = value?.trim();
  return trimmed ? trimmed : undefined;
};

function build(
  version: number,
  url: string | undefined,
  code: string | undefined,
  sessionId: string | undefined,
  label: string | undefined,
  pin: string | undefined,
): PairingPayloadResult {
  if (version !== PAIRING_PAYLOAD_VERSION) return { status: 'unsupported_version', version };

  const backendUrl = (url ?? '').trim().replace(/\/+$/, '');
  if (!backendUrl) return malformed('missing backend URL');
  if (!/^https?:\/\//i.test(backendUrl)) return malformed('backend URL must be http(s)');

  const pairingCode = (code ?? '').trim();
  if (!pairingCode) return malformed('missing pairing code');

  const payload: PairingPayload = { backendUrl, pairingCode };
  const session = clean(sessionId);
  const displayLabel = clean(label);
  const spkiPin = clean(pin);
  if (session) payload.sessionId = session;
  if (displayLabel) payload.label = displayLabel;
  if (spkiPin) payload.pin = spkiPin;
  return { status: 'ok', payload };
}

function parseUri(raw: string): PairingPayloadResult {
  const separator = raw.indexOf('?');
  const query = separator === -1 ? '' : raw.slice(separator + 1);
  if (!query) return malformed('pairing link has no parameters');

  const fields = new Map<string, string>();
  for (const pair of query.split('&')) {
    if (!pair) continue;
    const equals = pair.indexOf('=');
    const key = equals === -1 ? pair : pair.slice(0, equals);
    const rawValue = equals === -1 ? '' : pair.slice(equals + 1);
    let decoded: string;
    try {
      // `URLDecoder`, which the node uses, decodes '+' as a space; decodeURIComponent
      // does not. Matching it here keeps one wire format rather than two dialects.
      decoded = decodeURIComponent(rawValue.replace(/\+/g, ' '));
    } catch {
      return malformed(`undecodable parameter '${key}'`);
    }
    fields.set(key.toLowerCase(), decoded);
  }

  const version = coerceInt(fields.get('v'));
  if (version === null) return malformed('missing version');
  return build(
    version,
    fields.get('u'),
    fields.get('c'),
    fields.get('s'),
    fields.get('l'),
    fields.get('p'),
  );
}

function parseJson(raw: string): PairingPayloadResult {
  let parsed: unknown;
  try {
    parsed = JSON.parse(raw);
  } catch {
    return malformed('not valid pairing JSON');
  }
  if (typeof parsed !== 'object' || parsed === null || Array.isArray(parsed)) {
    return malformed('not valid pairing JSON');
  }

  const object = parsed as Record<string, unknown>;
  const str = (key: string): string | undefined => {
    const value = object[key];
    return typeof value === 'string' ? value : undefined;
  };

  const version = coerceInt(object['v']);
  if (version === null) return malformed('missing version');
  return build(version, str('u'), str('c'), str('s'), str('l'), str('p'));
}

/**
 * Parses the two interchangeable QR forms, both versioned:
 *
 * ```
 * luno://pair?v=1&u=https%3A%2F%2Fgw.example.com&c=ABCD-1234&s=ses_9f3&l=Acme&p=sha256%2FAAAA
 * {"v":1,"u":"https://gw.example.com","c":"ABCD-1234","s":"ses_9f3","l":"Acme","p":"sha256/AAAA"}
 * ```
 */
export function parsePairingPayload(raw: string): PairingPayloadResult {
  const trimmed = raw.trim();
  if (!trimmed) return malformed('empty payload');
  if (trimmed.startsWith('{')) return parseJson(trimmed);
  if (trimmed.toLowerCase().startsWith(PAIRING_SCHEME_PREFIX)) return parseUri(trimmed);
  return malformed('not a Luno pairing code');
}

export function buildPairingUri(payload: PairingPayload): string {
  const params: Array<[string, string | undefined]> = [
    ['v', String(PAIRING_PAYLOAD_VERSION)],
    ['u', payload.backendUrl],
    ['c', payload.pairingCode],
    ['s', payload.sessionId],
    ['l', payload.label],
    ['p', payload.pin],
  ];

  const query = params
    .filter((entry): entry is [string, string] => entry[1] !== undefined)
    .map(([key, value]) => `${key}=${encodeURIComponent(value)}`)
    .join('&');

  return `${PAIRING_SCHEME_PREFIX}?${query}`;
}

export function buildPairingJson(payload: PairingPayload): string {
  const object: Record<string, string | number> = {
    v: PAIRING_PAYLOAD_VERSION,
    u: payload.backendUrl,
    c: payload.pairingCode,
  };
  if (payload.sessionId) object['s'] = payload.sessionId;
  if (payload.label) object['l'] = payload.label;
  if (payload.pin) object['p'] = payload.pin;
  return JSON.stringify(object);
}
