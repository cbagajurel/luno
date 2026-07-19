export const PROTOCOL_VERSION = 1;

export const SUPPORTED_PROTOCOL_VERSIONS: readonly number[] = [PROTOCOL_VERSION];

/** Highest version both peers speak, or null if they share none (§8.5). */
export function negotiateVersion(peerSupported: Iterable<number>): number | null {
  let best: number | null = null;
  for (const version of peerSupported) {
    if (!SUPPORTED_PROTOCOL_VERSIONS.includes(version)) continue;
    if (best === null || version > best) best = version;
  }
  return best;
}
