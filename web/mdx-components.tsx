import { useMDXComponents as getDocsMDXComponents } from 'nextra-theme-docs'
import type { UseMDXComponents } from 'nextra/mdx-components'

const docsComponents = getDocsMDXComponents()

export const useMDXComponents: UseMDXComponents<typeof docsComponents> = <T,>(
  components?: T
) => ({
  ...docsComponents,
  ...components
})
