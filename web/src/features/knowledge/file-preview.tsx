import { useQuery } from '@tanstack/react-query'
import { Download, Loader2 } from 'lucide-react'
import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { knowledgeApi } from '@/api/client'
import type { KnowledgeDocument } from '@/api/knowledge-types'
import { MarkdownRenderer } from '@/features/skill/markdown-renderer'
import { buttonVariants } from '@/shared/ui/button'
import { KnowledgeFileIcon } from './file-icon'
import { knowledgeKeys } from './use-knowledge-queries'

/** Text previews beyond this size are offered as downloads instead of rendering in the page. */
export const MAX_TEXT_PREVIEW_BYTES = 2 * 1024 * 1024

async function fetchPreview(url: string, as: 'text' | 'blob') {
  const response = await fetch(url, { credentials: 'include' })
  if (!response.ok) {
    throw new Error(`HTTP ${response.status}`)
  }
  return as === 'text' ? response.text() : response.blob()
}

function PreviewMessage({ document, title, description }: { document: KnowledgeDocument; title: string; description?: string }) {
  const { t } = useTranslation()
  return (
    <div className="flex min-h-[24rem] flex-col items-center justify-center gap-4 p-8 text-center">
      <KnowledgeFileIcon extension={document.fileExtension} size="lg" />
      <div className="space-y-1">
        <p className="font-medium text-foreground">{title}</p>
        {description ? <p className="text-sm text-muted-foreground">{description}</p> : null}
      </div>
      <a href={knowledgeApi.contentUrl(document.id)} download className={buttonVariants({ variant: 'default' })}>
        <Download className="mr-2 h-4 w-4" aria-hidden />
        {t('knowledge.actions.download')}
      </a>
    </div>
  )
}

function PreviewLoading() {
  const { t } = useTranslation()
  return (
    <div className="flex min-h-[24rem] items-center justify-center gap-2 text-sm text-muted-foreground">
      <Loader2 className="h-4 w-4 animate-spin" aria-hidden />
      {t('knowledge.preview.loading')}
    </div>
  )
}

export function KnowledgeFilePreview({ document }: { document: KnowledgeDocument }) {
  const { t } = useTranslation()
  const url = knowledgeApi.contentUrl(document.id, { version: document.currentVersion, inline: true })
  const kind = document.previewKind
  const isText = kind === 'MARKDOWN' || kind === 'TEXT'
  const textTooLarge = isText && document.sizeBytes > MAX_TEXT_PREVIEW_BYTES

  const content = useQuery({
    queryKey: [...knowledgeKeys.document(document.id), 'preview', document.currentVersion],
    queryFn: () => fetchPreview(url, isText ? 'text' : 'blob'),
    enabled: (isText && !textTooLarge) || kind === 'PDF',
    staleTime: Infinity,
  })

  const [pdfUrl, setPdfUrl] = useState<string>()
  useEffect(() => {
    if (kind !== 'PDF' || !(content.data instanceof Blob)) {
      return undefined
    }
    // The API forbids framing, so PDFs are shown from a local object URL.
    const objectUrl = URL.createObjectURL(content.data)
    setPdfUrl(objectUrl)
    return () => URL.revokeObjectURL(objectUrl)
  }, [kind, content.data])

  if (kind === 'NONE') {
    return (
      <PreviewMessage
        document={document}
        title={t('knowledge.preview.unsupportedTitle')}
        description={t('knowledge.preview.unsupportedDescription')}
      />
    )
  }
  if (textTooLarge) {
    return <PreviewMessage document={document} title={t('knowledge.preview.tooLarge')} />
  }
  if (kind === 'IMAGE') {
    return (
      <div className="flex min-h-[24rem] items-center justify-center bg-[repeating-conic-gradient(hsl(var(--muted))_0%_25%,transparent_0%_50%)] bg-[length:20px_20px] p-4">
        <img src={url} alt={document.title} className="max-h-[75vh] max-w-full rounded-lg object-contain shadow-sm" />
      </div>
    )
  }
  if (content.isError) {
    return <PreviewMessage document={document} title={t('knowledge.preview.failed')} />
  }
  if (content.isLoading || (kind === 'PDF' && !pdfUrl)) {
    return <PreviewLoading />
  }
  if (kind === 'PDF') {
    return <iframe title={document.title} src={pdfUrl} className="h-[78vh] w-full rounded-b-xl border-0 bg-muted" />
  }
  const text = typeof content.data === 'string' ? content.data : ''
  if (kind === 'MARKDOWN') {
    return (
      <div className="px-6 py-5 sm:px-10 sm:py-8">
        <MarkdownRenderer content={text} />
      </div>
    )
  }
  return (
    <pre className="max-h-[78vh] overflow-auto whitespace-pre-wrap break-words px-6 py-5 font-mono text-[13px] leading-6 text-foreground/90">
      {text}
    </pre>
  )
}
