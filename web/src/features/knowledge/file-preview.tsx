import { useQuery } from '@tanstack/react-query'
import { Download, Loader2 } from 'lucide-react'
import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useAuth } from '@/features/auth/use-auth'
import { knowledgeApi } from '@/api/client'
import type { KnowledgePreviewDocument } from '@/api/knowledge-types'
import { CodeRenderer } from '@/features/skill/code-renderer'
import { MAX_SYNTAX_HIGHLIGHT_BYTES } from '@/features/skill/file-type-utils'
import { MarkdownRenderer } from '@/features/skill/markdown-renderer'
import { buttonVariants } from '@/shared/ui/button'
import { KnowledgeFileIcon } from './file-icon'
import { knowledgeKeys } from './use-knowledge-queries'
import { resolveImagePath } from './markdown-images'
import { KnowledgeBrowserOfficePreview } from './browser-office-preview'
import { MdxPreview } from './mdx-preview'

/** Text previews beyond this size are offered as downloads instead of rendering in the page. */
export const MAX_TEXT_PREVIEW_BYTES = 2 * 1024 * 1024

async function fetchPreview(url: string, as: 'text' | 'blob') {
  const response = await fetch(url, { credentials: 'include' })
  if (!response.ok) {
    throw new Error(`HTTP ${response.status}`)
  }
  return as === 'text' ? response.text() : response.blob()
}

function PreviewMessage({ document, title, description, shareId }: { document: KnowledgePreviewDocument; title: string; description?: string; shareId?: string }) {
  const { t } = useTranslation()
  return (
    <div className="flex min-h-[24rem] flex-col items-center justify-center gap-4 p-8 text-center">
      <KnowledgeFileIcon extension={document.fileExtension} size="lg" />
      <div className="space-y-1">
        <p className="font-medium text-foreground">{title}</p>
        {description ? <p className="text-sm text-muted-foreground">{description}</p> : null}
      </div>
      {document.canDownload ? <a href={knowledgeApi.contentUrl(document.id, shareId ? { shareId } : undefined)} download className={buttonVariants({ variant: 'default' })}>
        <Download className="mr-2 h-4 w-4" aria-hidden />
        {t('knowledge.actions.download')}
      </a> : null}
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

export function KnowledgeFilePreview({ document, shareId }: { document: KnowledgePreviewDocument; shareId?: string }) {
  const { t } = useTranslation()
  const { user } = useAuth()
  const url = knowledgeApi.contentUrl(document.id, { version: document.currentVersion, inline: true, ...(shareId && { shareId }) })
  const previewKey = shareId ? [...knowledgeKeys.all, 'share', shareId, 'document', document.id] : knowledgeKeys.document(document.id)
  const extension = document.fileExtension?.toLowerCase()
  const kind = ['html', 'htm'].includes(extension ?? '') ? 'NONE' : document.previewKind
  const isMdx = extension === 'mdx'
  const hasImages = kind === 'MARKDOWN' || isMdx
  const isText = kind === 'MARKDOWN' || kind === 'TEXT'
  const textTooLarge = isText && document.sizeBytes > MAX_TEXT_PREVIEW_BYTES

  const content = useQuery({
    queryKey: [...previewKey, 'preview', document.currentVersion, user?.userId ?? 'guest'],
    queryFn: () => fetchPreview(url, isText ? 'text' : 'blob'),
    // Preview streams the original file, so it shares the download permission.
    enabled: !!document.canDownload && ((isText && !textTooLarge) || kind === 'PDF'),
    staleTime: shareId ? 0 : Infinity,
    gcTime: shareId ? 0 : undefined,
  })
  const images = useQuery({
    queryKey: [...previewKey, 'images', document.currentVersion, user?.userId ?? 'guest'],
    queryFn: () => shareId ? knowledgeApi.listImages(document.id, document.currentVersion, shareId) : knowledgeApi.listImages(document.id, document.currentVersion),
    enabled: !!document.canDownload && hasImages && !textTooLarge,
    staleTime: shareId ? 0 : Infinity,
    gcTime: shareId ? 0 : undefined,
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

  if (!document.canDownload) {
    return (
      <PreviewMessage
        document={document}
        shareId={shareId}
        title={t('knowledge.preview.noDownloadTitle')}
        description={t('knowledge.preview.noDownloadDescription')}
      />
    )
  }
  if (kind === 'NONE' || (kind === 'OFFICE' && !['docx', 'pptx'].includes(extension ?? ''))) {
    return (
      <PreviewMessage
        document={document}
        shareId={shareId}
        title={t('knowledge.preview.unsupportedTitle')}
        description={t('knowledge.preview.unsupportedDescription')}
      />
    )
  }
  if (kind === 'OFFICE') {
    return <KnowledgeBrowserOfficePreview key={`${document.id}:${document.currentVersion}`} document={document} shareId={shareId} />
  }
  if (textTooLarge) {
    return <PreviewMessage document={document} shareId={shareId} title={t('knowledge.preview.tooLarge')} />
  }
  if (kind === 'IMAGE') {
    return (
      <div className="flex min-h-[24rem] items-center justify-center bg-[repeating-conic-gradient(hsl(var(--muted))_0%_25%,transparent_0%_50%)] bg-[length:20px_20px] p-4">
        <img src={url} alt={document.title} className="max-h-[75vh] max-w-full rounded-lg object-contain shadow-sm" />
      </div>
    )
  }
  if (content.isError || (hasImages && images.isError)) {
    return <PreviewMessage document={document} shareId={shareId} title={t('knowledge.preview.failed')} />
  }
  if (content.isLoading || (hasImages && images.isLoading) || (kind === 'PDF' && !pdfUrl)) {
    return <PreviewLoading />
  }
  if (kind === 'PDF') {
    return <iframe title={document.title} src={pdfUrl} className="h-[78vh] w-full rounded-b-xl border-0 bg-muted" />
  }
  const text = typeof content.data === 'string' ? content.data : ''
  if (isMdx) {
    return <MdxPreview key={`${document.id}:${document.currentVersion}`} content={text} resolveImageUrl={(src) => {
      const path = resolveImagePath(images.data?.sourcePath ?? document.fileName, src)
      const image = images.data?.images.find((candidate) => candidate.path === path)
      return image ? knowledgeApi.imageUrl(document.id, image.id, document.currentVersion, shareId) : undefined
    }} />
  }
  if (kind === 'MARKDOWN') {
    return (
      <div className="px-6 py-5 sm:px-10 sm:py-8">
        <MarkdownRenderer content={text} resolveImageUrl={(src) => {
          const path = resolveImagePath(images.data?.sourcePath ?? document.fileName, src)
          const image = images.data?.images.find((candidate) => candidate.path === path)
          return image ? knowledgeApi.imageUrl(document.id, image.id, document.currentVersion, shareId) : src
        }} />
      </div>
    )
  }
  if (kind === 'TEXT' && extension === 'sql' && document.sizeBytes <= MAX_SYNTAX_HIGHLIGHT_BYTES) {
    return <CodeRenderer code={text} language="sql" className="min-w-0 px-4 [&_pre]:max-h-[78vh] [&_pre]:overflow-y-auto" />
  }
  return (
    <pre className="max-h-[78vh] overflow-auto whitespace-pre-wrap break-words px-6 py-5 font-mono text-[13px] leading-6 text-foreground/90">
      {text}
    </pre>
  )
}
