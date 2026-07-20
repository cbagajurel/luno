/**
 * The store contract every `LunoStore` must satisfy, re-exported from
 * `@luno-oss/core` so conformance tooling has one home. Imported only inside a test
 * (it depends on vitest), which is why it is a separate entry from the runtime
 * fake-node tools.
 *
 * ```ts
 * import { describeStoreConformance } from '@luno-oss/testing/store';
 * describeStoreConformance('my-store', () => myStore());
 * ```
 */
export { describeStoreConformance } from '@luno-oss/core/testing';
