import type { CoreContext } from '../context';
import { normalizePairingCode } from '../domain/pairing-session';

/**
 * Codes and credentials are stored as a keyed digest of their normalised form.
 * HMAC rather than a per-record salted hash because a typed pairing code arrives
 * with no session id (docs/pairing.md makes `s` optional), so the server must be
 * able to find the session *by* the digest — which a random salt would make
 * impossible. The distinct prefixes keep a code digest from ever colliding with
 * a credential digest.
 */
export const hashPairingCode = (context: CoreContext, code: string): Promise<string> =>
  context.crypto.hmacSha256(context.secret, `pairing-code:${normalizePairingCode(code)}`);

export const hashDeviceCredential = (context: CoreContext, credential: string): Promise<string> =>
  context.crypto.hmacSha256(context.secret, `device-credential:${credential}`);
