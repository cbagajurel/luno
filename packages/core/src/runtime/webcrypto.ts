import type { Clock, CryptoPort, IdGenerator } from '../ports/runtime';
import { toBase64Url } from './tokens';

/**
 * Web Crypto and TextEncoder are declared structurally rather than pulled in
 * from `@types/node` or the DOM lib, which would drag a whole platform's globals
 * into a package that must compile against the intersection of Node, Workers,
 * Deno and Bun. These two are present on all four.
 */
interface SubtleCryptoLike {
  importKey(
    format: 'raw',
    keyData: Uint8Array,
    algorithm: { name: 'HMAC'; hash: 'SHA-256' },
    extractable: boolean,
    usages: readonly string[],
  ): Promise<unknown>;
  sign(algorithm: 'HMAC', key: unknown, data: Uint8Array): Promise<ArrayBuffer>;
}

declare const crypto: {
  getRandomValues(array: Uint8Array): Uint8Array;
  subtle: SubtleCryptoLike;
};

declare const TextEncoder: { new (): { encode(input: string): Uint8Array } };

const utf8 = new TextEncoder();

export function webCrypto(): CryptoPort {
  return {
    randomBytes: (length) => crypto.getRandomValues(new Uint8Array(length)),

    async hmacSha256(key, message) {
      const secret = await crypto.subtle.importKey(
        'raw',
        utf8.encode(key),
        { name: 'HMAC', hash: 'SHA-256' },
        false,
        ['sign'],
      );
      const signature = await crypto.subtle.sign('HMAC', secret, utf8.encode(message));
      return toBase64Url(new Uint8Array(signature));
    },
  };
}

export const systemClock: Clock = { now: () => Date.now() };

export function tokenIdGenerator(source: CryptoPort): IdGenerator {
  return { newId: (prefix) => `${prefix}_${toBase64Url(source.randomBytes(12))}` };
}
