import type { CryptoPort } from '../ports/runtime';

const BASE64URL = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_';

/** Hand-rolled so the core needs neither `Buffer` nor `btoa`, which no runtime shares. */
export function toBase64Url(bytes: Uint8Array): string {
  let out = '';
  for (let index = 0; index < bytes.length; index += 3) {
    const a = bytes[index] ?? 0;
    const b = bytes[index + 1] ?? 0;
    const c = bytes[index + 2] ?? 0;
    const remaining = bytes.length - index;

    out += BASE64URL[a >> 2];
    out += BASE64URL[((a & 0x03) << 4) | (b >> 4)];
    if (remaining > 1) out += BASE64URL[((b & 0x0f) << 2) | (c >> 6)];
    if (remaining > 2) out += BASE64URL[c & 0x3f];
  }
  return out;
}

export const randomToken = (crypto: CryptoPort, bytes = 32): string =>
  toBase64Url(crypto.randomBytes(bytes));

/**
 * Draws `length` characters uniformly from `alphabet`. Bytes at or above the
 * largest whole multiple of the alphabet size are discarded rather than reduced
 * with a bare modulo, which would make the first few characters of the alphabet
 * measurably more likely and quietly cost the code some of its entropy.
 */
export function randomCode(crypto: CryptoPort, alphabet: string, length: number): string {
  const limit = 256 - (256 % alphabet.length);
  let out = '';
  while (out.length < length) {
    for (const byte of crypto.randomBytes(length * 2)) {
      if (byte >= limit) continue;
      out += alphabet[byte % alphabet.length];
      if (out.length === length) break;
    }
  }
  return out;
}
