import { defineConfig } from 'vitest/config';

export default defineConfig({
  test: {
    globals: true,
    include: ['test/**/*.test.ts'],
    // pglite spins up a fresh WASM Postgres per suite; give the concurrency test room.
    testTimeout: 30_000,
  },
});
