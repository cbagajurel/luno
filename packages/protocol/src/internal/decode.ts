import type { JsonObject, JsonValue } from '../json';

/**
 * Thrown by the readers below and caught at the codec boundary, where it becomes
 * a `malformed` DecodeResult rather than propagating. Callers of the public API
 * never see it.
 */
export class DecodeError extends Error {
  constructor(message: string) {
    super(message);
    this.name = 'DecodeError';
  }
}

function fail(message: string): never {
  throw new DecodeError(message);
}

export function asJsonObject(value: unknown, what: string): JsonObject {
  if (typeof value !== 'object' || value === null || Array.isArray(value)) {
    fail(`${what} must be an object`);
  }
  return value as JsonObject;
}

/**
 * The readers mirror kotlinx.serialization's decoding rules, which the node
 * relies on: a missing field with no default is an error, an explicit `null` for
 * a non-nullable field is an error, and a missing field with a default silently
 * takes that default. Being lenient here would let frames decode on the server
 * that the node itself would reject.
 */

export function requireString(o: JsonObject, key: string): string {
  const value = o[key];
  if (typeof value !== 'string') fail(`'${key}' must be a string`);
  return value;
}

export function optionalString(o: JsonObject, key: string): string | undefined {
  const value = o[key];
  if (value === undefined || value === null) return undefined;
  if (typeof value !== 'string') fail(`'${key}' must be a string`);
  return value;
}

export function requireInt(o: JsonObject, key: string): number {
  const value = o[key];
  if (typeof value !== 'number' || !Number.isSafeInteger(value)) {
    fail(`'${key}' must be an integer`);
  }
  return value;
}

export function optionalInt(o: JsonObject, key: string): number | undefined {
  const value = o[key];
  if (value === undefined || value === null) return undefined;
  if (typeof value !== 'number' || !Number.isSafeInteger(value)) {
    fail(`'${key}' must be an integer`);
  }
  return value;
}

export function intWithDefault(o: JsonObject, key: string, fallback: number): number {
  if (o[key] === undefined) return fallback;
  return requireInt(o, key);
}

export function requireBoolean(o: JsonObject, key: string): boolean {
  const value = o[key];
  if (typeof value !== 'boolean') fail(`'${key}' must be a boolean`);
  return value;
}

export function booleanWithDefault(o: JsonObject, key: string, fallback: boolean): boolean {
  if (o[key] === undefined) return fallback;
  return requireBoolean(o, key);
}

function requireArray(o: JsonObject, key: string): JsonValue[] {
  const value = o[key];
  if (!Array.isArray(value)) fail(`'${key}' must be an array`);
  return value;
}

export function requireStringArray(o: JsonObject, key: string): string[] {
  return requireArray(o, key).map((item, index) => {
    if (typeof item !== 'string') fail(`'${key}[${index}]' must be a string`);
    return item;
  });
}

export function stringArrayWithDefault(o: JsonObject, key: string, fallback: string[]): string[] {
  if (o[key] === undefined) return fallback;
  return requireStringArray(o, key);
}

export function requireIntArray(o: JsonObject, key: string): number[] {
  return requireArray(o, key).map((item, index) => {
    if (typeof item !== 'number' || !Number.isSafeInteger(item)) {
      fail(`'${key}[${index}]' must be an integer`);
    }
    return item;
  });
}

export function requireObjectArray<T>(
  o: JsonObject,
  key: string,
  map: (item: JsonObject, index: number) => T,
): T[] {
  return requireArray(o, key).map((item, index) =>
    map(asJsonObject(item, `'${key}[${index}]'`), index),
  );
}

export function objectArrayWithDefault<T>(
  o: JsonObject,
  key: string,
  fallback: T[],
  map: (item: JsonObject, index: number) => T,
): T[] {
  if (o[key] === undefined) return fallback;
  return requireObjectArray(o, key, map);
}

export function optionalObject<T>(
  o: JsonObject,
  key: string,
  map: (value: JsonObject) => T,
): T | undefined {
  const value = o[key];
  if (value === undefined || value === null) return undefined;
  return map(asJsonObject(value, `'${key}'`));
}
