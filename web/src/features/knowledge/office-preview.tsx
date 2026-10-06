import { useQuery } from '@tanstack/react-query'
import { Download, Loader2 } from 'lucide-react'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useAuth } from '@/features/auth/use-auth'
import { knowledgeApi } from '@/api/client'
import type { KnowledgeDocument } from '@/api/knowledge-types'
import { buttonVariants } from '@/shared/ui/button'
import { knowledgeKeys } from './use-knowledge-queries'

export function KnowledgeOfficeFilePreview({ document }: { document: KnowledgeDocument }) {
  const { t } = useTranslation()
  const { user } = useAuth()
  const [imageFailed, setImageFailed] = useState(false)
  const preview = useQuery({
    queryKey: [...knowledgeKeys.document(document.id), 'office-preview', document.currentVersion, user?.userId ?? 'guest'],
    queryFn: () => knowledgeApi.officePreview(document.id, document.currentVersion),
    refetchInterval: (query) => query.state.data?.status === 'PROCESSING' ? 2000 : false,
    staleTime: Infinity,
    retry: false,
  })
  const data = preview.data
  const download = document.canDownload ? <a href={knowledgeApi.contentUrl(document.id, { version: document.currentVersion })} download
    className={buttonVariants({ variant: 'outline', size: 'sm' })}><Download className="mr-2 h-4 w-4" aria-hidden />{t('knowledge.preview.downloadOriginal')}</a> : null

  if (preview.isPending || data?.status === 'PROCESSING') {
    return <div className="flex min-h-[24rem] flex-col items-center justify-center gap-4 px-6 text-center">
      <div className="flex items-center gap-2 text-sm text-muted-foreground"><Loader2 className="h-4 w-4 animate-spin" aria-hidden />{t('knowledge.preview.generating')}</div>
      {download}
    </div>
  }
  if (preview.isError || !data || data.status !== 'READY' || imageFailed) {
    return <div className="flex min-h-[24rem] flex-col items-center justify-center gap-4 px-6 text-center">
      <p className="text-sm text-muted-foreground">{t('knowledge.preview.officeFailed')}</p>
      {download}
      {data?.status !== 'FAILED' && <button className="text-sm underline" onClick={() => { setImageFailed(false); void preview.refetch() }}>{t('knowledge.preview.retry')}</button>}
    </div>
  }
  return <div className="space-y-4 p-4 sm:p-6">
    <div className="flex flex-wrap items-center justify-between gap-3">
      <p className="text-sm text-muted-foreground">{t('knowledge.preview.officeLimit', { count: data.pageLimit })}</p>
      {download}
    </div>
    <div className="space-y-5 rounded-lg bg-muted/40 p-3 sm:p-5">
      {Array.from({ length: data.pageCount }, (_, index) => <figure key={index} className="space-y-2">
        <img src={knowledgeApi.previewPageUrl(document.id, document.currentVersion, index + 1)}
          alt={t('knowledge.preview.page', { count: index + 1 })} loading={index === 0 ? 'eager' : 'lazy'}
          onError={() => setImageFailed(true)} className="mx-auto h-auto max-w-full rounded border bg-white shadow-sm" />
        <figcaption className="text-center text-xs text-muted-foreground">{t('knowledge.preview.page', { count: index + 1 })}</figcaption>
      </figure>)}
    </div>
  </div>
}
