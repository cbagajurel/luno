import type { PageMapItem } from 'nextra'
import { getPageMap } from 'nextra/page-map'
import type { DocPage } from './command-palette'

function titleCase(name: string): string {
  return name
    .split('-')
    .map(part => part.charAt(0).toUpperCase() + part.slice(1))
    .join(' ')
}

function walk(items: PageMapItem[], section: string, out: DocPage[]): void {
  // A folder's own _meta entry carries the titles for its children.
  const meta = items.find(item => 'data' in item)
  const titles: Record<string, unknown> =
    meta && 'data' in meta ? (meta.data as Record<string, unknown>) : {}

  for (const item of items) {
    if ('data' in item) continue

    const named = item as PageMapItem & {
      name?: string
      route?: string
      title?: string
      children?: PageMapItem[]
    }
    if (!named.name) continue

    const metaTitle = titles[named.name]
    const title =
      named.title ??
      (typeof metaTitle === 'string' && metaTitle ? metaTitle : undefined) ??
      titleCase(named.name)

    if (named.children) {
      walk(named.children, named.name === 'docs' ? section : title, out)
    } else if (named.route?.startsWith('/docs')) {
      out.push({
        title: named.name === 'index' ? section || title : title,
        route: named.route,
        section
      })
    }
  }
}

export async function getDocPages(): Promise<DocPage[]> {
  const pageMap = await getPageMap()
  const pages: DocPage[] = []
  walk(pageMap, 'Documentation', pages)

  const seen = new Set<string>()
  return pages.filter(page => {
    if (seen.has(page.route)) return false
    seen.add(page.route)
    return true
  })
}
