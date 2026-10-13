import { Children, isValidElement, useId, useMemo, useState, type ReactElement, type ReactNode } from 'react'
import ReactMarkdown, { type ExtraProps } from 'react-markdown'
import rehypeSanitize, { defaultSchema } from 'rehype-sanitize'
import rehypeHighlight from 'rehype-highlight'
import { useTranslation } from 'react-i18next'
import type { Root } from 'mdast'
import { cn } from '@/shared/lib/utils'
import { prepareMdxDocument, safeMdxLink } from './mdx-document'

const schema = {
  ...defaultSchema,
  attributes: {
    ...defaultSchema.attributes,
    div: [...(defaultSchema.attributes?.div ?? []), 'dataMdxKind', 'dataMdxTone', 'dataMdxTitle', 'dataMdxLabel', 'dataMdxValue', 'dataMdxDescription', 'dataMdxHref', 'dataMdxCaption'],
  },
}

function preparedTree(tree: Root) {
  return () => (root: Root) => { root.children = tree.children }
}

function property(node: ExtraProps['node'], name: string): string | undefined {
  const value = node?.properties[`dataMdx${name}`]
  return typeof value === 'string' ? value : undefined
}

function StaticTabs({ children }: { children: ReactNode }) {
  const id = useId()
  const [selected, setSelected] = useState(0)
  const allChildren = Children.toArray(children)
  const tabs = allChildren.filter((child): child is ReactElement<{ node?: ExtraProps['node']; children?: ReactNode }> =>
    isValidElement(child) && property((child.props as { node?: ExtraProps['node'] }).node, 'Kind') === 'tab')
  const { t } = useTranslation()
  return <div className="my-4 rounded-lg border">
    <div role="tablist" aria-label={t('knowledge.mdx.tabs')} className="flex gap-1 overflow-x-auto border-b p-1">
      {tabs.map((tab, index) => <button key={index} type="button" role="tab" id={`${id}-tab-${index}`} aria-selected={selected === index} aria-controls={`${id}-panel-${index}`} tabIndex={selected === index ? 0 : -1}
        className={cn('min-h-10 shrink-0 rounded px-4 text-sm focus-visible:outline focus-visible:outline-2 focus-visible:outline-ring', selected === index && 'bg-secondary font-medium')}
        onClick={() => setSelected(index)} onKeyDown={(event) => {
          const next = event.key === 'ArrowRight' ? (index + 1) % tabs.length : event.key === 'ArrowLeft' ? (index + tabs.length - 1) % tabs.length : event.key === 'Home' ? 0 : event.key === 'End' ? tabs.length - 1 : undefined
          if (next === undefined) return
          event.preventDefault()
          setSelected(next)
          document.getElementById(`${id}-tab-${next}`)?.focus()
        }}>{property(tab.props.node, 'Label') ?? property(tab.props.node, 'Title') ?? property(tab.props.node, 'Value') ?? `${index + 1}`}</button>)}
    </div>
    {tabs.map((tab, index) => <div key={index} role="tabpanel" id={`${id}-panel-${index}`} aria-labelledby={`${id}-tab-${index}`} hidden={selected !== index} tabIndex={0} className="px-4 py-2">{tab.props.children}</div>)}
    {/* Retain unexpected content, including inert unsupported-source placeholders. */}
    {allChildren.filter((child) => !tabs.includes(child as typeof tabs[number]))}
  </div>
}

function StaticComponent({ node, children }: ExtraProps & { children?: ReactNode }) {
  const kind = property(node, 'Kind')
  const title = property(node, 'Title')
  const { t } = useTranslation()
  if (kind === 'callout') {
    const tone = property(node, 'Tone')
    return <aside className={cn('my-4 rounded-lg border-l-4 bg-secondary/40 px-4 py-2',
      tone === 'warning' ? 'border-amber-500' : tone === 'danger' ? 'border-destructive' : tone === 'tip' ? 'border-emerald-500' : 'border-primary')}>
      <p className="mb-1 font-semibold">{title ?? t(`knowledge.mdx.tones.${tone ?? 'note'}`)}</p>{children}
    </aside>
  }
  if (kind === 'accordion') return <details className="my-3 rounded-lg border px-4 py-3"><summary className="cursor-pointer font-medium focus-visible:outline focus-visible:outline-ring">{title ?? t('knowledge.mdx.details')}</summary><div className="pt-2">{children}</div></details>
  if (kind === 'tabs') return <StaticTabs>{children}</StaticTabs>
  if (kind === 'steps') return <ol className="my-4 space-y-4 border-l-2 pl-6">{children}</ol>
  if (kind === 'step') return <li className="pl-1">{title && <p className="font-semibold">{title}</p>}{children}</li>
  if (kind === 'cards') return <div className="my-4 grid gap-4 sm:grid-cols-2">{children}</div>
  if (kind === 'card') {
    const href = safeMdxLink(property(node, 'Href'))
    return <section className="min-w-0 rounded-lg border p-4">{title && <p className="font-semibold">{title}</p>}{property(node, 'Description') && <p>{property(node, 'Description')}</p>}{children}
      {href && <a href={href} target={href.startsWith('#') ? undefined : '_blank'} rel="noopener noreferrer" className="text-primary underline">{t('knowledge.mdx.openLink')}</a>}
    </section>
  }
  if (kind === 'figure') return <figure className="my-4">{children}{property(node, 'Caption') && <figcaption className="mt-2 text-center text-sm text-muted-foreground">{property(node, 'Caption')}</figcaption>}</figure>
  return <div>{children}</div>
}

export function MdxPreview({ content, resolveImageUrl }: { content: string; resolveImageUrl: (src: string) => string | undefined }) {
  const { t } = useTranslation()
  const parsed = useMemo(() => prepareMdxDocument(content), [content])
  const plugins = useMemo(() => parsed.tree ? [preparedTree(parsed.tree)] : [], [parsed.tree])
  const [source, setSource] = useState(false)
  return <div className="min-w-0 px-6 py-5 sm:px-10 sm:py-8">
    <div className="mb-4 flex gap-2" role="group" aria-label={t('knowledge.mdx.mode')}>
      {[false, true].map((value) => <button key={String(value)} type="button" aria-pressed={source === value} onClick={() => setSource(value)} className={cn('min-h-10 rounded-md border px-4 text-sm focus-visible:outline focus-visible:outline-2 focus-visible:outline-ring', source === value && 'bg-secondary font-medium')}>{t(value ? 'knowledge.mdx.source' : 'knowledge.mdx.preview')}</button>)}
    </div>
    {!source && (parsed.error || parsed.unsupported > 0) && <p role="status" className="mb-4 rounded-md bg-secondary p-3 text-sm text-muted-foreground">{parsed.error ? t(`knowledge.mdx.errors.${parsed.error}`) : t('knowledge.mdx.unsupported', { count: parsed.unsupported })}</p>}
    {source || !parsed.tree ? <pre className="max-h-[70vh] overflow-auto whitespace-pre-wrap break-words font-mono text-[13px] leading-6">{content}</pre> :
      <div className="break-words text-sm leading-7 [&_h1]:mb-5 [&_h1]:text-2xl [&_h1]:font-bold [&_h2]:my-4 [&_h2]:text-xl [&_h2]:font-semibold [&_h3]:my-3 [&_h3]:text-lg [&_h3]:font-semibold [&_p]:my-3 [&_ul]:list-disc [&_ul]:pl-6 [&_ol]:list-decimal [&_ol]:pl-6 [&_blockquote]:border-l-4 [&_blockquote]:pl-4 [&_code]:rounded [&_code]:bg-secondary [&_code]:px-1 [&_table]:w-full [&_th]:border [&_th]:p-2 [&_td]:border [&_td]:p-2">
        <ReactMarkdown remarkPlugins={plugins} rehypePlugins={[[rehypeSanitize, schema], [rehypeHighlight, { detect: false, ignoreMissing: true }]]}
          components={{
            div: StaticComponent,
            pre: ({ children }) => <pre className="my-4 max-w-full overflow-x-auto rounded-lg bg-secondary p-4 text-[13px] leading-6">{children}</pre>,
            table: ({ children }) => <div className="my-4 overflow-x-auto"><table>{children}</table></div>,
            a: ({ href, children }) => {
              const safe = safeMdxLink(href)
              return safe ? <a href={safe} target={safe.startsWith('#') ? undefined : '_blank'} rel="noopener noreferrer" className="text-primary underline">{children}</a> : <span>{children}</span>
            },
            img: ({ src, alt, title }) => {
              const url = typeof src === 'string' ? resolveImageUrl(src) : undefined
              return url ? <img src={url} alt={alt ?? ''} title={title} loading="lazy" decoding="async" className="h-auto max-w-full rounded-lg" /> : <span className="inline-block rounded border px-3 py-2 text-muted-foreground">{t('knowledge.mdx.imageUnavailable', { name: alt || src || '' })}</span>
            },
          }}>{''}</ReactMarkdown>
      </div>}
  </div>
}
