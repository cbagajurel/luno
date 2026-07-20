import { defineConfig } from 'tsup';

export default defineConfig({
  entry: { index: 'src/index.ts' },
  outExtension: ({ format }) => ({ js: format === 'cjs' ? '.cjs' : '.js' }),
  format: ['esm', 'cjs'],
  target: 'es2022',
  external: ['@luno-oss/core', '@luno-oss/protocol', '@nestjs/common', '@nestjs/core', 'reflect-metadata', 'ws'],
  dts: true,
  clean: true,
  sourcemap: true,
  treeshake: true,
});
