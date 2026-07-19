export type JsonPrimitive = string | number | boolean | null;
export type JsonValue = JsonPrimitive | JsonValue[] | { [key: string]: JsonValue };
export type JsonObject = { [key: string]: JsonValue };

/**
 * Build a payload object dropping absent fields, mirroring the node's
 * `Json { explicitNulls = false }`: a null/undefined field is omitted from the
 * wire entirely rather than serialized as `null`. Insertion order is preserved,
 * which is what lets our output match kotlinx's declaration-order output byte
 * for byte.
 */
export function compact(fields: Record<string, JsonValue | undefined>): JsonObject {
  const out: JsonObject = {};
  for (const [key, value] of Object.entries(fields)) {
    if (value === undefined || value === null) continue;
    out[key] = value;
  }
  return out;
}
