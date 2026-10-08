import { Loader2 } from 'lucide-react'
import { useEffect, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'

/** Render slides at their native aspect ratio, then scale the whole deck to the viewport. */
export function PptxCanvas({ blob, title }: { blob: Blob; title: string }) {
  const { t } = useTranslation()
  const viewport = useRef<HTMLDivElement>(null)
  const canvas = useRef<HTMLDivElement>(null)
  const [status, setStatus] = useState<'loading' | 'ready' | 'failed'>('loading')
  const [layout, setLayout] = useState({ width: 0, height: 0, scale: 1 })

  useEffect(() => {
    const frame = viewport.current
    const host = canvas.current
    if (!frame || !host) return
    let active = true
    let observer: ResizeObserver | undefined
    let dispose: (() => void) | undefined
    setStatus('loading')
    host.replaceChildren()
    const rendered = document.createElement('div')
    const render = async () => {
      const [{ renderPptx }, { preparePptx, hasAllSlides }, data] = await Promise.all([
        import('./pptx-renderer'), import('./prepare-pptx'), blob.arrayBuffer(),
      ])
      if (!active) return
      const prepared = await preparePptx(data)
      if (!active) return
      const preview = await renderPptx(rendered, prepared, () => active)
      if (!preview) return
      dispose = preview.dispose
      if (!hasAllSlides(preview.result, prepared.slideCount)) throw new Error('Incomplete PPTX preview')
      // The library defaults to a single-slide-height scrolling wrapper. Let
      // the complete deck grow so scrolling belongs to our scaled viewport.
      const wrapper = rendered.querySelector<HTMLElement>('.pptx-preview-wrapper')
      if (wrapper) {
        wrapper.style.height = 'auto'
        wrapper.style.overflow = 'visible'
        wrapper.style.background = 'transparent'
      }
      rendered.querySelectorAll<HTMLElement>('.pptx-preview-slide-wrapper').forEach((slide) => {
        slide.style.margin = '0 auto 24px'
        slide.classList.add('shadow-sm')
      })
      rendered.querySelectorAll('script, iframe, object, embed').forEach((node) => node.remove())
      rendered.querySelectorAll('*').forEach((element) => {
        for (const attr of Array.from(element.attributes)) {
          if (/^on/i.test(attr.name)) element.removeAttribute(attr.name)
        }
      })
      rendered.querySelectorAll('a[href]').forEach((link) => {
        if (!/^(#|https?:|mailto:|tel:)/i.test((link.getAttribute('href') ?? '').trim())) link.removeAttribute('href')
      })
      host.replaceChildren(rendered)
      await document.fonts?.ready
      if (!active) return
      const measure = () => {
        if (active) setLayout({ width: prepared.width, height: rendered.offsetHeight, scale: Math.min(1, frame.clientWidth / prepared.width) })
      }
      measure()
      observer = new ResizeObserver(measure)
      observer.observe(frame)
      observer.observe(rendered)
      setStatus('ready')
    }
    void render().catch(() => {
      if (active) { host.replaceChildren(); setStatus('failed') }
      dispose?.()
    })
    return () => {
      active = false
      observer?.disconnect()
      dispose?.()
      host.replaceChildren()
    }
  }, [blob])

  return <div className="min-w-0">
    {status === 'loading' ? <div role="status" className="flex min-h-48 items-center justify-center gap-2 text-sm text-muted-foreground">
      <Loader2 className="h-4 w-4 animate-spin" aria-hidden />{t('knowledge.preview.loading')}
    </div> : null}
    {status === 'failed' ? <p role="alert" className="p-8 text-center text-muted-foreground">{t('knowledge.preview.officeFailed')}</p> : null}
    <div ref={viewport} role="region" aria-label={title} tabIndex={0}
      className="max-h-[78vh] overflow-auto rounded-lg bg-muted outline-none focus-visible:ring-2 focus-visible:ring-ring">
      <div className="relative mx-auto" style={{ width: layout.width * layout.scale || undefined, height: layout.height * layout.scale || undefined }}>
        <div ref={canvas} aria-hidden={status !== 'ready'} className="absolute left-0 top-0 origin-top-left"
          style={{ width: layout.width || undefined, transform: `scale(${layout.scale})`, visibility: status === 'ready' ? 'visible' : 'hidden' }} />
      </div>
    </div>
  </div>
}
