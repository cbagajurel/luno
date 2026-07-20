export type LunoErrorCode =
  | 'invalid_request'
  | 'not_found'
  | 'conflict'
  | 'forbidden'
  | 'device_offline'
  | 'internal';

const HTTP_STATUS: Record<LunoErrorCode, number> = {
  invalid_request: 400,
  not_found: 404,
  conflict: 409,
  forbidden: 403,
  device_offline: 409,
  internal: 500,
};

/**
 * Operator-facing failures — the API surface a dashboard or service calls. This
 * is deliberately separate from the node-facing pairing taxonomy in
 * `@luno-oss/protocol`, which is a wire contract rather than an SDK error type.
 */
export class LunoError extends Error {
  readonly code: LunoErrorCode;

  constructor(code: LunoErrorCode, message: string) {
    super(message);
    this.name = 'LunoError';
    this.code = code;
  }

  get status(): number {
    return HTTP_STATUS[this.code];
  }

  static notFound = (what: string): LunoError => new LunoError('not_found', `${what} not found`);
}
