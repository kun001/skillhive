import { useQuery } from '@tanstack/react-query'
import { Download, Loader2 } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { knowledgeApi } from '@/api/client'
import type { KnowledgeDocument } from '@/api/knowledge-types'
import { useAuth } from '@/features/auth/use-auth'
import { Button, buttonVariants } from '@/shared/ui/button'
import { DocxCanvas } from './docx-preview'
import { PptxCanvas } from './pptx-preview'
import { knowledgeKeys } from './use-knowledge-queries'

export function KnowledgeBrowserOfficePreview({ document }: { document: KnowledgeDocument }) {
  const { t } = useTranslation()
  const { user } = useAuth()
  const content = useQuery({
    queryKey: [...knowledgeKeys.document(document.id), 'office-content', document.currentVersion, user?.userId ?? 'guest'],
    queryFn: ({ signal }) => knowledgeApi.officeContent(document.id, document.currentVersion, signal),
    staleTime: Infinity, gcTime: 60_000, retry: false,
  })
  const Canvas = document.fileExtension?.toLowerCase() === 'pptx' ? PptxCanvas : DocxCanvas
  return <div className="space-y-4 p-4 sm:p-6">
    <div className="flex flex-wrap items-center justify-between gap-3">
      <p className="text-sm text-muted-foreground">{t('knowledge.preview.officeFull')}</p>
      {document.canDownload ? <a href={knowledgeApi.contentUrl(document.id, { version: document.currentVersion })} download
        className={buttonVariants({ variant: 'outline', size: 'sm' })}>
        <Download className="h-4 w-4" aria-hidden />{t('knowledge.preview.downloadOriginal')}
      </a> : null}
    </div>
    {content.isPending ? <div role="status" className="flex min-h-48 items-center justify-center gap-2 text-sm text-muted-foreground">
      <Loader2 className="h-4 w-4 animate-spin" aria-hidden />{t('knowledge.preview.loading')}
    </div> : null}
    {content.isError ? <div className="space-y-3 p-8 text-center">
      <p role="alert" className="text-muted-foreground">{t('knowledge.preview.officeFailed')}</p>
      <Button variant="outline" size="sm" onClick={() => void content.refetch()}>{t('knowledge.preview.retry')}</Button>
    </div> : null}
    {content.data ? <Canvas blob={content.data} title={document.title} /> : null}
  </div>
}
