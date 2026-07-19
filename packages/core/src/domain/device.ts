/**
 * The node's connection state as the backend observes it, mirroring §6. The
 * backend only ever learns about the phases that produce a frame, so there is no
 * server-side equivalent of the node's CONNECTING or BACKING_OFF.
 */
export const CONNECTION_PHASES = ['offline', 'connected', 'authenticated', 'ready'] as const;

export type ConnectionPhase = (typeof CONNECTION_PHASES)[number];

export type DeviceStatus = 'active' | 'revoked';

/**
 * How long after its last frame a node is presumed gone. The node heartbeats
 * every 30–60s (§7.2), so this allows a couple of misses before we call it.
 */
export const DEFAULT_PRESENCE_TIMEOUT_MS = 150_000;

/**
 * Presence is derived from `lastSeenAt` rather than tracked with a timer. A timer
 * would need a long-lived process, which the platforms this SDK targets do not
 * all provide; computing it on read works identically on a serverless function
 * and a long-running server.
 */
export function isPresent(
  lastSeenAt: number | null,
  now: number,
  timeoutMs: number = DEFAULT_PRESENCE_TIMEOUT_MS,
): boolean {
  if (lastSeenAt === null) return false;
  return now - lastSeenAt <= timeoutMs;
}
