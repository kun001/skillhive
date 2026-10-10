import { useEffect, useState, type MouseEvent } from 'react'
import { Copy, Check, Download, Loader2, X } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { Dialog, DialogContent } from '@/shared/ui/dialog'
import { Button } from '@/shared/ui/button'
import { DocxCanvas } from '@/features/knowledge/docx-preview'
import { PptxCanvas } from '@/features/knowledge/pptx-preview'
import { MarkdownRenderer } from './markdown-renderer'
import { CodeRenderer } from './code-renderer'
import { toast } from '@/shared/lib/toast'
import { copyToClipboard } from '@/shared/lib/clipboard'
import {
  getFileTypeLabel,
  canPreviewFile,
  getLanguageForHighlight,
  getFileExtension,
  isBrowserOfficePreviewable,
} from './file-type-utils'
import type { FileTreeNode } from './file-tree-builder'

interface FilePreviewDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  node: FileTreeNode | null
  content: string | null
  isLoading: boolean
  error: Error | null
  canDownload?: boolean
  onDownload: () => void
  onLinkClick?: (href: string, event: MouseEvent<HTMLAnchorElement>) => void
  /** Absolute or same-origin URL used to fetch binary Office content for in-browser preview. */
  contentUrl?: string | null
}

function SkillOfficePreview({ url, fileName, title }: { url: string; fileName: string; title: string }) {
  const { t } = useTranslation()
  const [blob, setBlob] = useState<Blob | null>(null)
  const [status, setStatus] = useState<'loading' | 'ready' | 'failed'>('loading')

  useEffect(() => {
    let active = true
    setStatus('loading')
    setBlob(null)
    void fetch(url, { credentials: 'include' })
      .then((response) => {
        if (!response.ok) {
          throw new Error(`HTTP ${response.status}`)
        }
        return response.blob()
      })
      .then((data) => {
        if (!active) return
        setBlob(data)
        setStatus('ready')
      })
      .catch(() => {
        if (active) setStatus('failed')
      })
    return () => {
      active = false
    }
  }, [url])

  const Canvas = getFileExtension(fileName) === 'pptx' ? PptxCanvas : DocxCanvas

  return (
    <div className="space-y-4">
      {status === 'loading' ? (
        <div role="status" className="flex min-h-48 items-center justify-center gap-2 text-sm text-muted-foreground">
          <Loader2 className="h-4 w-4 animate-spin" aria-hidden />
          {t('knowledge.preview.loading')}
        </div>
      ) : null}
      {status === 'failed' ? (
        <p role="alert" className="p-8 text-center text-muted-foreground">{t('knowledge.preview.officeFailed')}</p>
      ) : null}
      {status === 'ready' && blob ? <Canvas blob={blob} title={title} /> : null}
    </div>
  )
}

/**
 * Dialog component for previewing file contents.
 * Supports Markdown, code, plain text, and browser Office (docx/pptx) preview.
 * Shows appropriate messages for non-previewable files.
 */
export function FilePreviewDialog({
  open,
  onOpenChange,
  node,
  content,
  isLoading,
  error,
  canDownload = true,
  onDownload,
  onLinkClick,
  contentUrl,
}: FilePreviewDialogProps) {
  const { t } = useTranslation()
  // Tracks the copy animation state: idle → spinning → done
  const [copyState, setCopyState] = useState<'idle' | 'spinning' | 'done'>('idle')

  if (!node) return null

  const fileTypeLabel = getFileTypeLabel(node.name)
  const previewCheck = canPreviewFile(node.name, node.file?.fileSize || 0)
  const isOffice = previewCheck.mode === 'office' || isBrowserOfficePreviewable(node.name)
  const isMarkdown = ['md', 'mdx', 'markdown'].includes(fileTypeLabel)
  const fileSize = node.file?.fileSize || 0
  const language = getLanguageForHighlight(node.name)

  // Syntax highlighting threshold: 500KB
  const SYNTAX_HIGHLIGHT_THRESHOLD = 500 * 1024
  const shouldHighlight = language && fileSize <= SYNTAX_HIGHLIGHT_THRESHOLD

  /**
   * Copies file content to clipboard with animation feedback.
   */
  const handleCopy = async () => {
    if (!content || copyState !== 'idle') return
    setCopyState('spinning')
    try {
      await copyToClipboard(content)
    } catch {
      toast.error(t('filePreview.copyError', { defaultValue: t('filePreview.loadError') }))
      setCopyState('idle')
      return
    }
    // Show checkmark after the spin completes
    setTimeout(() => {
      setCopyState('done')
      toast.success(t('filePreview.copySuccess'))
      // Reset back to idle after a short delay
      setTimeout(() => setCopyState('idle'), 1500)
    }, 300)
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      {/* Hide the default close button; override base width to fill most of the viewport */}
      <DialogContent className="w-[min(calc(100vw-2rem),72rem)] max-h-[90vh] p-0 gap-0 flex flex-col [&>button]:hidden">
        {/* Header: file name, type badge, and action buttons (including close) */}
        <div className="flex items-center justify-between px-5 py-3 border-b border-border/40 bg-muted/30 flex-shrink-0">
          <div className="flex items-center gap-3 min-w-0 flex-1">
            <span className="font-mono text-sm font-medium text-foreground truncate">{node.name}</span>
            <span className="px-2 py-0.5 text-xs rounded border border-border/60 bg-background/60 text-muted-foreground flex-shrink-0">
              {fileTypeLabel}
            </span>
          </div>
          <div className="flex items-center gap-1 flex-shrink-0">
            {content && !isOffice ? (
              <Button
                variant="ghost"
                size="icon"
                className="h-8 w-8 rounded-lg opacity-60 hover:opacity-100 hover:bg-accent transition-all duration-200 active:scale-95"
                onClick={handleCopy}
                title={t('filePreview.copy')}
                disabled={copyState !== 'idle'}
              >
                {copyState === 'done'
                  ? <Check className="h-4 w-4 text-emerald-500" />
                  : <Copy className={`h-4 w-4 transition-transform duration-300 ${copyState === 'spinning' ? 'animate-spin' : 'hover:rotate-180'}`} />}
              </Button>
            ) : null}
            <Button
              variant="ghost"
              size="icon"
              className="h-8 w-8 rounded-lg opacity-60 hover:opacity-100 hover:bg-accent transition-all duration-200 hover:scale-110 active:scale-95"
              disabled={!canDownload}
              onClick={onDownload}
              title={t('filePreview.downloadHint', { name: node.name })}
            >
              <Download className="h-4 w-4 transition-transform duration-200 hover:translate-y-0.5" />
            </Button>
            <Button
              variant="ghost"
              size="icon"
              className="h-8 w-8 rounded-lg opacity-60 hover:opacity-100 hover:bg-destructive/10 hover:text-destructive transition-all duration-200 hover:rotate-90 active:scale-95"
              onClick={() => onOpenChange(false)}
              title={t('filePreview.close')}
            >
              <X className="h-4 w-4" />
            </Button>
          </div>
        </div>

        {/* Content area: loading, error, office, non-previewable, or actual content */}
        <div className="overflow-auto p-6 bg-card flex-1 min-h-0">
          {isOffice && contentUrl ? (
            <SkillOfficePreview url={contentUrl} fileName={node.name} title={node.name} />
          ) : isLoading ? (
            <div className="flex items-center justify-center py-12">
              <div className="h-8 w-8 animate-spin rounded-full border-4 border-primary border-t-transparent" />
            </div>
          ) : error ? (
            <div className="text-center py-12">
              <p className="text-sm font-medium text-foreground">{t('filePreview.loadError')}</p>
              <p className="text-sm text-muted-foreground mt-2">{error.message}</p>
            </div>
          ) : !previewCheck.canPreview || (isOffice && !contentUrl) ? (
            <div className="text-center py-12 space-y-4">
              <p className="text-sm font-medium text-foreground">
                {previewCheck.reason === 'too-large'
                  ? t('filePreview.tooLarge')
                  : previewCheck.reason === 'binary' || isOffice
                    ? t('filePreview.binaryFile')
                    : t('filePreview.unsupported')}
              </p>
              <Button onClick={onDownload}>{t('filePreview.downloadHint', { name: node.name })}</Button>
            </div>
          ) : content && isMarkdown ? (
            <MarkdownRenderer content={content} onLinkClick={onLinkClick} />
          ) : content && shouldHighlight ? (
            <CodeRenderer code={content} language={language} />
          ) : content ? (
            <pre className="text-sm font-mono whitespace-pre-wrap break-words">
              <code>{content}</code>
            </pre>
          ) : null}
        </div>

        {/* Footer: shows the full file path */}
        <div className="px-5 py-2 border-t border-border/40 bg-muted/20 flex-shrink-0">
          <span className="text-xs text-muted-foreground font-mono">{node.path}</span>
        </div>
      </DialogContent>
    </Dialog>
  )
}