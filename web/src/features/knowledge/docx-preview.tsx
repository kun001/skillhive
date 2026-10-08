import { Loader2 } from 'lucide-react'
import { useEffect, useId, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'

/** Keep the document's layout intact and scale the entire paper to fit the viewport. */
export function DocxCanvas({ blob, title }: { blob: Blob; title: string }) {
  const { t } = useTranslation()
  const id = useId().replace(/[^a-zA-Z0-9]/g, '')
  const viewport = useRef<HTMLDivElement>(null)
  const canvas = useRef<HTMLDivElement>(null)
  const [status, setStatus] = useState<'loading' | 'ready' | 'failed'>('loading')
  const [layout, setLayout] = useState({ width: 0, height: 0, scale: 1 })

  useEffect(() => {
    const host = canvas.current
    const frame = viewport.current
    if (!host || !frame) return
    let active = true
    let observer: ResizeObserver | undefined
    setStatus('loading')
    host.replaceChildren()
    // Render off-screen so an old, slower render can never replace a newer version.
    const rendered = window.document.createElement('div')
    rendered.style.width = 'max-content'
    const styles = window.document.createElement('div')
    const render = async () => {
      const { renderAsync } = await import('docx-preview')
      if (!active) return
      await renderAsync(blob, rendered, styles, {
        className: `skillhive-docx-${id}`, inWrapper: false,
        ignoreWidth: false, ignoreHeight: false, ignoreFonts: false,
        breakPages: true, ignoreLastRenderedPageBreak: false,
        useBase64URL: true, renderAltChunks: false,
      })
      if (!active) return
      rendered.querySelectorAll('a[href]').forEach((link) => {
        const href = link.getAttribute('href') ?? ''
        if (!href.startsWith('#') && !/^(https?:|mailto:|tel:)/i.test(href.trim())) link.removeAttribute('href')
      })
      host.replaceChildren(styles, rendered)
      // Only position the paper. Never constrain its tables, images or text to the UI width.
      const pages = Array.from(rendered.children).filter((node): node is HTMLElement => node instanceof HTMLElement)
      pages.forEach((page) => {
        page.classList.add('bg-white', 'text-black', 'shadow-sm')
        page.style.margin = '0 auto 24px'
      })
      const width = Math.max(...pages.map((page) => page.offsetWidth))
      if (!Number.isFinite(width) || width <= 0) throw new Error('Empty Word preview')
      rendered.style.width = `${width}px`
      await Promise.all(Array.from(rendered.querySelectorAll('img')).map((img) => img.decode().catch(() => undefined)))
      await window.document.fonts?.ready
      if (!active) return
      const measure = () => {
        if (!active) return
        setLayout({ width, height: rendered.offsetHeight, scale: Math.min(1, frame.clientWidth / width) })
      }
      measure()
      observer = new ResizeObserver(measure)
      observer.observe(frame)
      observer.observe(rendered)
      setStatus('ready')
    }
    void render().catch(() => {
      if (active) { host.replaceChildren(); setStatus('failed') }
    })
    return () => { active = false; observer?.disconnect(); host.replaceChildren() }
  }, [blob, id])

  const scale = layout.scale
  return <div className="min-w-0">
    {status === 'loading' ? <div role="status" className="flex min-h-48 items-center justify-center gap-2 text-sm text-muted-foreground">
      <Loader2 className="h-4 w-4 animate-spin" aria-hidden />{t('knowledge.preview.loading')}
    </div> : null}
    {status === 'failed' ? <p role="alert" className="p-8 text-center text-muted-foreground">{t('knowledge.preview.officeFailed')}</p> : null}
    <div ref={viewport} role="region" aria-label={title} tabIndex={0}
      className="max-h-[78vh] overflow-auto rounded-lg bg-muted p-0 outline-none focus-visible:ring-2 focus-visible:ring-ring">
      <div className="relative mx-auto" style={{ width: layout.width * scale || undefined, height: layout.height * scale || undefined }}>
        <div ref={canvas} aria-hidden={status !== 'ready'} className="absolute left-0 top-0 origin-top-left"
          style={{ width: layout.width || undefined, transform: `scale(${scale})`, visibility: status === 'ready' ? 'visible' : 'hidden' }} />
      </div>
    </div>
  </div>
}

