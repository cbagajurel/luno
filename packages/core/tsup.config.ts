import { defineConfig } from 'tsup';

export default defineConfig({
  entry: { index: 'src/index.ts', testing: 'src/testing/index.ts' },
  outExtension: ({ format }) => ({ js: format === 'cjs' ? '.cjs' : '.js' }),
  format: ['esm', 'cjs'],
  target: 'es2022',
  // vitest is a peer, resolved in the consumer's test process, never bundled.
  external: ['vitest'],
  dts: true,
  clean: true,
  sourcemap: true,
  treeshake: true,
});
