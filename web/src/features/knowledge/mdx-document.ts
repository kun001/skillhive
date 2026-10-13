import { unified } from 'unified'
import remarkParse from 'remark-parse'
import remarkGfm from 'remark-gfm'
import remarkMdx from 'remark-mdx'
import type { Root } from 'mdast'
import { stripMarkdownFrontmatter } from '@/features/skill/markdown-frontmatter'

export const MAX_MDX_PREVIEW_CHARS = 128 * 1024
const MAX_NODES = 5000
const MAX_DEPTH = 40

interface Node {
  type: string
  name?: string | null
  value?: string
  url?: string
  identifier?: string
  children?: Node[]
  attributes?: { type: string; name?: string; value?: unknown }[]
  position?: { start: { offset?: number }; end: { offset?: number } }
  data?: { hName?: string; hProperties?: Record<string, string> }
}

const components: Record<string, { kind: string; attrs: string[] }> = {
  Callout: { kind: 'callout', attrs: ['type', 'title'] },
  Admonition: { kind: 'callout', attrs: ['type', 'title'] },
  Note: { kind: 'callout', attrs: ['title'] },
  Info: { kind: 'callout', attrs: ['title'] },
  Tip: { kind: 'callout', attrs: ['title'] },
  Warning: { kind: 'callout', attrs: ['title'] },
  Caution: { kind: 'callout', attrs: ['title'] },
  Danger: { kind: 'callout', attrs: ['title'] },
  Accordion: { kind: 'accordion', attrs: ['title'] },
  Details: { kind: 'accordion', attrs: ['title'] },
  AccordionGroup: { kind: 'group', attrs: [] },
  Tabs: { kind: 'tabs', attrs: [] },
  Tab: { kind: 'tab', attrs: ['title', 'label', 'value'] },
  TabItem: { kind: 'tab', attrs: ['title', 'label', 'value'] },
  Steps: { kind: 'steps', attrs: [] },
  Step: { kind: 'step', attrs: ['title'] },
  Cards: { kind: 'cards', attrs: [] },
  CardGroup: { kind: 'cards', attrs: [] },
  Card: { kind: 'card', attrs: ['title', 'description', 'href'] },
  Figure: { kind: 'figure', attrs: ['caption'] },
  Image: { kind: 'image', attrs: ['src', 'alt', 'title', 'width', 'height'] },
  img: { kind: 'image', attrs: ['src', 'alt', 'title', 'width', 'height'] },
}

/** Document URLs never become executable protocols or authenticated navigation to arbitrary endpoints. */
export function safeMdxLink(url: string | undefined): string | undefined {
  if (!url || url.includes('\\') || Array.from(url).some((char) => char.charCodeAt(0) <= 32 || char.charCodeAt(0) === 127)) return undefined
  if (url.startsWith('#')) return url
  try {
    const parsed = new URL(url)
    return ['https:', 'http:', 'mailto:'].includes(parsed.protocol) && !parsed.username && !parsed.password ? url : undefined
  } catch { return undefined }
}

export interface MdxDocument {
  tree?: Root
  unsupported: number
  imageUrls: string[]
  error?: 'size' | 'syntax' | 'complexity'
}

/** Parse syntax only. MDX is never compiled, evaluated, imported, or passed through as HTML. */
export function prepareMdxDocument(source: string): MdxDocument {
  if (source.length > MAX_MDX_PREVIEW_CHARS) return { unsupported: 0, imageUrls: [], error: 'size' }
  const content = stripMarkdownFrontmatter(source)
  let parsed: Node
  try {
    parsed = unified().use(remarkParse).use(remarkGfm).use(remarkMdx).parse(content) as unknown as Node
  } catch { return { unsupported: 0, imageUrls: [], error: 'syntax' } }
  let count = 0
  let unsupported = 0
  const urls = new Set<string>()
  const definitions = new Map<string, string>()
  function collect(node: Node, depth: number) {
    if (++count > MAX_NODES || depth > MAX_DEPTH) throw new Error('complexity')
    if (node.type === 'definition' && node.identifier && node.url && !definitions.has(node.identifier)) definitions.set(node.identifier, node.url)
    node.children?.forEach((child) => collect(child, depth + 1))
  }
  function placeholder(node: Node, inline: boolean): Node {
    unsupported++
    const start = node.position?.start.offset
    const end = node.position?.end.offset
    return { type: inline ? 'inlineCode' : 'code', value: start === undefined || end === undefined ? node.value ?? node.name ?? '' : content.slice(start, end) }
  }
  function transform(node: Node, inline = false): Node {
    if (['mdxjsEsm', 'mdxFlowExpression', 'mdxTextExpression', 'html'].includes(node.type)) {
      return placeholder(node, inline || node.type === 'mdxTextExpression')
    }
    if (node.type === 'mdxJsxFlowElement' || node.type === 'mdxJsxTextElement') {
      const spec = Object.prototype.hasOwnProperty.call(components, node.name ?? '') ? components[node.name ?? ''] : undefined
      if (!spec || node.type === 'mdxJsxTextElement') return placeholder(node, node.type === 'mdxJsxTextElement')
      const attrs: Record<string, string> = {}
      for (const attr of node.attributes ?? []) {
        if (attr.type !== 'mdxJsxAttribute' || !attr.name || !spec.attrs.includes(attr.name) || typeof attr.value !== 'string') return placeholder(node, false)
        attrs[attr.name] = attr.value.slice(0, 2000)
      }
      if (spec.kind === 'image') {
        if (!attrs.src || node.children?.length) return placeholder(node, false)
        urls.add(attrs.src)
        return { type: 'paragraph', children: [{ type: 'image', url: attrs.src, ...{ alt: attrs.alt ?? '', title: attrs.title ?? null } }] } as Node
      }
      let tone = attrs.type ?? (node.name ?? '').toLowerCase()
      if (tone === 'caution') tone = 'warning'
      if (!['note', 'info', 'tip', 'warning', 'danger'].includes(tone)) tone = 'note'
      const properties: Record<string, string> = { dataMdxKind: spec.kind, dataMdxTone: tone }
      for (const [key, value] of Object.entries(attrs)) properties[`dataMdx${key[0].toUpperCase()}${key.slice(1)}`] = value
      // Link validation is repeated by the renderer; uploaded props are never spread into DOM nodes.
      if (attrs.href && !safeMdxLink(attrs.href)) delete properties.dataMdxHref
      properties.dataMdxTone = tone
      return { type: 'mdxSafeComponent', data: { hName: 'div', hProperties: properties }, children: node.children?.map((child) => transform(child)) ?? [] }
    }
    if (node.type === 'image' && node.url) urls.add(node.url)
    if (node.type === 'imageReference' && node.identifier) {
      const url = definitions.get(node.identifier)
      if (url) urls.add(url)
    }
    return { ...node, children: node.children?.map((child) => transform(child, node.type === 'paragraph' || node.type === 'heading' || inline)) }
  }
  try {
    collect(parsed, 0)
    return { tree: transform(parsed) as unknown as Root, unsupported, imageUrls: [...urls] }
  } catch { return { unsupported: 0, imageUrls: [], error: 'complexity' } }
}
