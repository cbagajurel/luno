/**
 * Enrolment policy. Every field is deployment configuration rather than
 * protocol: the node enforces none of it and cannot see it, so changing any of
 * these is a server config change and never an app release.
 */
export interface PairingPolicy {
  /** null disables expiry entirely. */
  expiresInMs: number | null;
  /** null admits unlimited devices; 1 makes the session single-use. */
  maxEnrollments: number | null;
  requireApproval: boolean;
  /** Whether a device with a known installId may re-enrol and replace itself. */
  allowReplacement: boolean;
}

/**
 * The secure defaults recommended by docs/pairing.md: one device, short expiry,
 * session spent once used, no replacement unless asked for.
 */
export const DEFAULT_PAIRING_POLICY: PairingPolicy = {
  expiresInMs: 10 * 60 * 1000,
  maxEnrollments: 1,
  requireApproval: false,
  allowReplacement: false,
};

/** Excludes characters that are easy to misread when a code is typed by hand. */
const CODE_ALPHABET = 'ABCDEFGHJKMNPQRSTUVWXYZ23456789';
const CODE_LENGTH = 8;
const CODE_GROUP = 4;

export const pairingCodeAlphabet = (): string => CODE_ALPHABET;
export const pairingCodeLength = (): number => CODE_LENGTH;

/** Renders raw code characters in the grouped form operators see: `ABCD-1234`. */
export function formatPairingCode(characters: string): string {
  const groups: string[] = [];
  for (let index = 0; index < characters.length; index += CODE_GROUP) {
    groups.push(characters.slice(index, index + CODE_GROUP));
  }
  return groups.join('-');
}

/**
 * Codes are compared after normalisation, so the grouping dashes and the case an
 * operator happens to type are irrelevant — `abcd-1234` and `ABCD1234` are the
 * same code. Only the normalised form is ever hashed.
 */
export function normalizePairingCode(code: string): string {
  return code.replace(/[\s-]/g, '').toUpperCase();
}
