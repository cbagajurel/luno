export interface Clock {
  now(): number;
}

export interface IdGenerator {
  /** Ids are prefixed (`dev_`, `ses_`, `msg_`) so they are self-describing in logs. */
  newId(prefix: string): string;
}

/**
 * The two primitives the core needs from the platform's crypto, kept as narrow
 * as possible. Injected rather than imported because `node:crypto` does not
 * exist on Workers, and Web Crypto's `subtle` is async and cannot be shimmed
 * synchronously.
 *
 * `randomBytes` is synchronous because `getRandomValues` is synchronous on every
 * supported runtime; only the digest needs to be awaited.
 */
export interface CryptoPort {
  randomBytes(length: number): Uint8Array;
  /** Deterministic keyed digest — equal inputs must always give equal output. */
  hmacSha256(key: string, message: string): Promise<string>;
}

export type LogLevel = 'debug' | 'info' | 'warn' | 'error';

export interface Logger {
  log(level: LogLevel, message: string, fields?: Record<string, unknown>): void;
}

export const silentLogger: Logger = { log: () => {} };
