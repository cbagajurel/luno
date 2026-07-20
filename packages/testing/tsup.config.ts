import { defineConfig } from 'tsup';

export default defineConfig({
  entry: { index: 'src/index.ts', store: 'src/store.ts' },
  outExtension: ({ format }) => ({ js: format === 'cjs' ? '.cjs' : '.js' }),
  format: ['esm', 'cjs'],
  target: 'es2022',
  external: ['vitest', '@luno-oss/core', 'ws'],
  dts: true,
  clean: true,
  sourcemap: true,
  treeshake: true,
});
