# Luno documentation

The official documentation site for [Luno](../README.md), built with
[Next.js](https://nextjs.org) and [Nextra](https://nextra.site).

```sh
npm install
npm run dev     # http://localhost:3000
npm run build   # production build + Pagefind search index
```

## Layout

```
app/
├── page.tsx            Landing page
├── layout.tsx          Root layout — navbar, footer, theme
├── _meta.global.tsx    Navigation structure for the whole site
├── _components/        Landing-page-only components
├── docs/               All documentation, as page.mdx files
└── og/                 Dynamic Open Graph image route
components/
├── command-palette.tsx  ⌘K search (cmdk + Pagefind)
├── get-doc-pages.ts     Flattens the Nextra page map for the palette
├── ui/command.tsx       shadcn-style cmdk primitives
├── play-store-button.tsx
├── luno-logo.tsx
└── site.ts              GitHub and Play Store URLs
```

Navigation is defined in `app/_meta.global.tsx`. Adding a page means creating
`app/docs/<section>/<page>/page.mdx` and adding its slug to the matching group
in that file.

Each page exports `metadata` with a `title` and `description`; the title is also
used to generate the page's social image at build time.

## Search

Search is a cmdk command palette, opened with **⌘K / Ctrl+K** or the navbar
button. It combines two sources: instant client-side matching over the page list
(built from the Nextra page map) and full-text results from Pagefind.

Pagefind indexes the built HTML, so **content search only works after
`npm run build`** — in `next dev` the palette still navigates pages but shows a
notice instead of content hits.

## Store links

`components/site.ts` holds the GitHub and Google Play URLs. The Play link is
built from the app's `applicationId` (`com.luno.gateway`) — change it there if
the published listing differs.

## Content source

These pages are written from the design documents in [`../docs`](../docs), which
remain the source of truth for the project. When behaviour changes, update the
design document and the corresponding page together.

## Dependency notes

`nextra` and `nextra-theme-docs` are pinned to **exactly 4.6.0**. Version 4.6.1
throws `Invalid input: expected nonoptional, received undefined → at children`
at render time on every page, so do not widen that range without verifying a
build first.

Next.js is held at 15.x for the same reason — Nextra 4.6.0 predates Next 16, and
on Next 16 every MDX page that imports from `nextra/components` fails to compile
with "You are attempting to export `metadata` from a component marked with
`use client`".

If a build fails with `Cannot find module for page` or a missing
`required-server-files.json`, the webpack cache is stale. Run
`rm -rf .next node_modules/.cache` and rebuild.
