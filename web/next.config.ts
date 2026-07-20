import nextra from 'nextra'

const withNextra = nextra({
  defaultShowCopyCode: true
})

const nextConfig = withNextra({
  reactStrictMode: true,
  // Served from oss.nexneotech.com/luno, behind a rewrite from the OSS hub
  // project. The origin must own the prefix too, or its own asset and route
  // URLs come back without it.
  basePath: '/luno',
  // The repo root carries its own pnpm-lock.yaml, so Turbopack would otherwise
  // infer the monorepo as the project root and fail to resolve the MDX
  // import source.
  turbopack: {
    root: import.meta.dirname,
    // Next 16 dropped the `@vercel/turbopack-next/mdx-import-source` magic
    // module that Nextra still aliases to, so point MDX at our provider.
    resolveAlias: { 'next-mdx-import-source-file': './mdx-components.tsx' }
  },
  // lucide-react ships one module per icon; without this the barrel import
  // pulls the whole set into dev builds.
  experimental: { optimizePackageImports: ['lucide-react'] }
})

export default nextConfig
