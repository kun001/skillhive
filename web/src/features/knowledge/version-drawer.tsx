import { Download, History, RotateCcw } from 'lucide-react'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { knowledgeApi } from '@/api/client'
import type { KnowledgeDocument, KnowledgeDocumentVersion } from '@/api/knowledge-types'
import { ConfirmDialog } from '@/shared/components/confirm-dialog'
import { formatLocalDateTime } from '@/shared/lib/date-time'
import { toast } from '@/shared/lib/toast'
import { cn } from '@/shared/lib/utils'
import { Button, buttonVariants } from '@/shared/ui/button'
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from '@/shared/ui/dialog'
import { knowledgeErrorMessage } from './errors'
import { formatFileSize } from './file-types'
import { useKnowledgeVersions, useRestoreKnowledgeVersion } from './use-knowledge-queries'

interface VersionDrawerProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  document: KnowledgeDocument
}

/** Right-hand drawer listing every version, newest first, with download and restore actions. */
export function KnowledgeVersionDrawer({ open, onOpenChange, document }: VersionDrawerProps) {
  const { t, i18n } = useTranslation()
  const versions = useKnowledgeVersions(document.id, open)
  const restore = useRestoreKnowledgeVersion(document.id)
  const [restoring, setRestoring] = useState<KnowledgeDocumentVersion | null>(null)

  const confirmRestore = async () => {
    if (!restoring) return
    try {
      await restore.mutateAsync({
        versionNumber: restoring.versionNumber,
        changeNote: t('knowledge.versions.restoredNote', { version: restoring.versionNumber }),
      })
      toast.success(t('knowledge.versions.restored'))
    } catch (error) {
      toast.error(knowledgeErrorMessage(error))
    }
  }

  return (
    <>
      <Dialog open={open} onOpenChange={onOpenChange}>
        <DialogContent className="left-auto right-0 top-0 flex h-full max-h-screen w-[min(100vw,30rem)] translate-x-0 translate-y-0 flex-col gap-0 rounded-none rounded-l-2xl p-0">
          <DialogHeader className="border-b border-border/60 px-6 py-5 text-left">
            <DialogTitle className="flex items-center gap-2">
              <History className="h-5 w-5 text-primary" aria-hidden />
              {t('knowledge.versions.title')}
            </DialogTitle>
            <DialogDescription className="truncate">{document.title}</DialogDescription>
          </DialogHeader>
          <div className="flex-1 overflow-y-auto px-4 py-4">
            {versions.isLoading ? (
              <div className="space-y-3">
                {Array.from({ length: 3 }).map((_, index) => (
                  <div key={index} className="h-20 animate-shimmer rounded-xl" />
                ))}
              </div>
            ) : (versions.data ?? []).length === 0 ? (
              <p className="py-10 text-center text-sm text-muted-foreground">{t('knowledge.versions.empty')}</p>
            ) : (
              <ol className="relative space-y-3 before:absolute before:bottom-4 before:left-[1.15rem] before:top-4 before:w-px before:bg-border">
                {(versions.data ?? []).map((version) => (
                  <li key={version.id} className="relative flex gap-3">
                    <span
                      className={cn(
                        'relative z-10 mt-3 flex h-5 w-5 shrink-0 translate-x-2 items-center justify-center rounded-full border-2 bg-card',
                        version.current ? 'border-primary' : 'border-border',
                      )}
                      aria-hidden
                    >
                      {version.current ? <span className="h-2 w-2 rounded-full bg-primary" /> : null}
                    </span>
                    <div className={cn('min-w-0 flex-1 rounded-xl border p-3 pl-4', version.current ? 'border-primary/40 bg-primary/5' : 'border-border/60 bg-card')}>
                      <div className="flex items-center gap-2">
                        <span className="font-semibold text-foreground">v{version.versionNumber}</span>
                        {version.current ? (
                          <span className="rounded-full bg-primary/10 px-2 py-0.5 text-xs font-medium text-primary">{t('knowledge.versions.current')}</span>
                        ) : null}
                        <span className="ml-auto text-xs text-muted-foreground">
                          {formatLocalDateTime(version.createdAt, i18n.language, { dateStyle: 'short', timeStyle: 'short' })}
                        </span>
                      </div>
                      {version.changeNote ? <p className="mt-1 text-sm text-foreground/85">{version.changeNote}</p> : null}
                      <p className="mt-1 truncate text-xs text-muted-foreground">
                        {version.fileName} · {formatFileSize(version.sizeBytes)} · {version.createdBy.displayName}
                      </p>
                      <div className="mt-2 flex gap-2">
                        <a
                          href={knowledgeApi.contentUrl(document.id, { version: version.versionNumber })}
                          download
                          className={buttonVariants({ variant: 'outline', size: 'sm' })}
                        >
                          <Download className="mr-1.5 h-3.5 w-3.5" aria-hidden />
                          {t('knowledge.actions.download')}
                        </a>
                        {document.canManage && !version.current ? (
                          <Button variant="outline" size="sm" onClick={() => setRestoring(version)}>
                            <RotateCcw className="mr-1.5 h-3.5 w-3.5" aria-hidden />
                            {t('knowledge.versions.restore')}
                          </Button>
                        ) : null}
                      </div>
                    </div>
                  </li>
                ))}
              </ol>
            )}
          </div>
        </DialogContent>
      </Dialog>
      <ConfirmDialog
        open={restoring !== null}
        onOpenChange={(next) => !next && setRestoring(null)}
        title={t('knowledge.versions.restoreTitle', { version: restoring?.versionNumber })}
        description={t('knowledge.versions.restoreDescription', { version: restoring?.versionNumber })}
        confirmText={t('knowledge.versions.restore')}
        onConfirm={confirmRestore}
      />
    </>
  )
}
