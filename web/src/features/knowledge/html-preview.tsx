import { useMutation } from '@tanstack/react-query'
import { Download, Loader2, Maximize2, Minimize2, Play, RotateCw } from 'lucide-react'
import { useEffect, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { knowledgeApi } from '@/api/client'
import type { KnowledgeDocument } from '@/api/knowledge-types'
import { Button, buttonVariants } from '@/shared/ui/button'

export const MAX_HTML_PREVIEW_BYTES = 8 * 1024 * 1024

export function KnowledgeHtmlPreview({ document: file }: { document: KnowledgeDocument }) {
  const { t } = useTranslation()
  const viewer = useRef<HTMLDivElement>(null)
  const [fullscreen, setFullscreen] = useState(false)
  const launch = useMutation({
    mutationFn: async () => {
      const { url } = await knowledgeApi.createHtmlPreview(file.id, file.currentVersion)
      const target = new URL(url)
      if (target.hostname === location.hostname || !['http:', 'https:'].includes(target.protocol)
          || target.username || target.password || !/^\/_html-preview\/[A-Za-z0-9_-]{32}$/.test(target.pathname)) {
        throw new Error('Invalid HTML preview address')
      }
      return target.href
    },
  })
  const unavailable = !!launch.error && 'status' in launch.error && launch.error.status === 503

  useEffect(() => {
    const syncFullscreen = () => setFullscreen(document.fullscreenElement === viewer.current)
    document.addEventListener('fullscreenchange', syncFullscreen)
    return () => document.removeEventListener('fullscreenchange', syncFullscreen)
  }, [])

  function toggleFullscreen() {
    const box = viewer.current
    if (!box) return
    if (fullscreen) {
      if (document.fullscreenElement === box) void document.exitFullscreen()
      else setFullscreen(false)
      return
    }
    if (box.requestFullscreen) void box.requestFullscreen().catch(() => setFullscreen(true))
    else setFullscreen(true)
  }

  return (
    <div ref={viewer} className={fullscreen
      ? 'fixed inset-0 z-50 flex flex-col bg-background'
      : 'flex flex-col bg-background'}>
      <div className="flex min-h-12 flex-wrap items-center justify-between gap-2 border-b px-4 py-2">
        <span className="text-sm font-medium">{t('knowledge.preview.htmlLabel')}</span>
        <div className="flex items-center gap-2">
          <a href={knowledgeApi.contentUrl(file.id, { version: file.currentVersion })} download
            className={buttonVariants({ variant: 'outline', size: 'sm' })}>
            <Download className="mr-1.5 h-4 w-4" aria-hidden />
            {t('knowledge.preview.downloadOriginal')}
          </a>
          {launch.data ? <>
            <Button variant="outline" size="sm" disabled={launch.isPending} onClick={() => launch.mutate()}>
              <RotateCw className="mr-1.5 h-4 w-4" aria-hidden />
              {t('knowledge.preview.htmlReload')}
            </Button>
            <Button variant="outline" size="sm" aria-pressed={fullscreen} onClick={toggleFullscreen}>
              {fullscreen ? <Minimize2 className="mr-1.5 h-4 w-4" aria-hidden /> : <Maximize2 className="mr-1.5 h-4 w-4" aria-hidden />}
              {t(fullscreen ? 'knowledge.preview.htmlExitFullscreen' : 'knowledge.preview.htmlFullscreen')}
            </Button>
          </> : null}
        </div>
      </div>
      {launch.isError ? <p role="alert" className="px-4 py-2 text-sm text-destructive">
        {t(unavailable ? 'knowledge.preview.htmlUnavailable' : 'knowledge.preview.htmlFailed')}
      </p> : null}
      {launch.data ? (
        <iframe src={launch.data} title={file.title} referrerPolicy="no-referrer"
          className={fullscreen ? 'w-full flex-1 border-0 bg-white' : 'h-[78vh] w-full border-0 bg-white'} />
      ) : (
        <div className="flex min-h-[24rem] items-center justify-center p-6">
          <Button disabled={launch.isPending || unavailable} onClick={() => launch.mutate()}>
            {launch.isPending ? <Loader2 className="mr-2 h-4 w-4 animate-spin" aria-hidden /> : <Play className="mr-2 h-4 w-4" aria-hidden />}
            {t(launch.isPending ? 'knowledge.preview.loading' : 'knowledge.preview.htmlStart')}
          </Button>
        </div>
      )}
    </div>
  )
}
