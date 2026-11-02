import { Archive, Trash2 } from 'lucide-react'
import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import type { ArchivedKnowledgeDocument, UserStorageQuota } from '@/api/knowledge-types'
import { ConfirmDialog } from '@/shared/components/confirm-dialog'
import { Pagination } from '@/shared/components/pagination'
import { formatLocalDateTime } from '@/shared/lib/date-time'
import { toast } from '@/shared/lib/toast'
import { Button } from '@/shared/ui/button'
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from '@/shared/ui/dialog'
import { knowledgeErrorMessage } from './errors'
import { formatFileSize } from './file-types'
import {
  useArchivedKnowledgeDocuments,
  useMyStorageQuota,
  usePurgeArchivedKnowledgeDocuments,
  usePurgeKnowledgeDocument,
} from './use-knowledge-queries'

export const ARCHIVED_PAGE_SIZE = 20
/** The server purges at most this many files per base-wide request. */
export const PURGE_BATCH_LIMIT = 100

interface ArchivedDrawerProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  namespace: string
  base: string
}

/**
 * Right-hand drawer listing the base's deleted (archived) files with permanent deletion.
 * The server decides visibility (team admins see every file, members only their own) and
 * sets {@code canPurge}; this view only renders what it returns.
 */
export function KnowledgeArchivedDrawer({ open, onOpenChange, namespace, base }: ArchivedDrawerProps) {
  const { t, i18n } = useTranslation()
  const [page, setPage] = useState(0)
  const archived = useArchivedKnowledgeDocuments(namespace, base, page, ARCHIVED_PAGE_SIZE, open)
  const quota = useMyStorageQuota(open)
  const purgeOne = usePurgeKnowledgeDocument()
  const purgeAll = usePurgeArchivedKnowledgeDocuments(namespace, base)
  const [purging, setPurging] = useState<ArchivedKnowledgeDocument | null>(null)
  const [confirmAll, setConfirmAll] = useState(false)
  const [remaining, setRemaining] = useState(0)

  const items = archived.data?.items ?? []
  const total = archived.data?.total ?? 0
  const totalPages = Math.max(Math.ceil(total / ARCHIVED_PAGE_SIZE), 1)
  const canPurgeAny = items.some((item) => item.canPurge)
  const batchCount = Math.min(total, PURGE_BATCH_LIMIT)

  useEffect(() => {
    if (archived.data && page > 0 && page >= totalPages) setPage(totalPages - 1)
  }, [archived.data, page, totalPages])

  useEffect(() => {
    if (!open) setRemaining(0)
  }, [open])

  const confirmPurge = async () => {
    if (!purging) return
    try {
      const result = await purgeOne.mutateAsync(purging.id)
      if (result.purgedDocuments > 0) {
        toast.success(t('knowledge.archived.purged', { size: formatFileSize(result.freedBytes) }))
      } else {
        toast.success(t('knowledge.archived.alreadyPurged'))
      }
    } catch (error) {
      toast.error(knowledgeErrorMessage(error))
    }
  }

  const confirmPurgeAll = async () => {
    try {
      const result = await purgeAll.mutateAsync()
      toast.success(t('knowledge.archived.purgedMany', { count: result.purgedDocuments, size: formatFileSize(result.freedBytes) }))
      setRemaining(result.remainingArchived ?? 0)
      setPage(0)
    } catch (error) {
      toast.error(knowledgeErrorMessage(error))
    }
  }

  return (
    <>
      <Dialog open={open} onOpenChange={onOpenChange}>
        <DialogContent className="left-auto right-0 top-0 flex h-full max-h-screen w-[min(100vw,34rem)] translate-x-0 translate-y-0 flex-col gap-0 rounded-none rounded-l-2xl p-0">
          <DialogHeader className="space-y-2 border-b border-border/60 px-6 py-5 text-left">
            <DialogTitle className="flex items-center gap-2">
              <Archive className="h-5 w-5 text-primary" aria-hidden />
              {t('knowledge.archived.title')}
            </DialogTitle>
            <DialogDescription>{t('knowledge.archived.description')}</DialogDescription>
            {quota.data ? <StorageQuotaSummary quota={quota.data} /> : null}
          </DialogHeader>
          <div className="flex-1 space-y-3 overflow-y-auto px-4 py-4">
            {remaining > 0 && total > 0 ? (
              <div role="status" className="flex items-center justify-between gap-3 rounded-xl border border-amber-500/40 bg-amber-500/10 px-3 py-2 text-sm text-foreground">
                <span>{t('knowledge.archived.remaining', { count: remaining })}</span>
                {canPurgeAny ? (
                  <Button size="sm" variant="outline" onClick={() => setConfirmAll(true)}>
                    {t('knowledge.archived.continue')}
                  </Button>
                ) : null}
              </div>
            ) : null}
            {archived.isLoading ? (
              <div className="space-y-3">
                {Array.from({ length: 3 }).map((_, index) => (
                  <div key={index} className="h-16 animate-shimmer rounded-xl" />
                ))}
              </div>
            ) : archived.isError ? (
              <p role="alert" className="py-10 text-center text-sm text-destructive">
                {t('knowledge.archived.loadFailed', { message: knowledgeErrorMessage(archived.error) })}
              </p>
            ) : items.length === 0 ? (
              <p className="py-10 text-center text-sm text-muted-foreground">{t('knowledge.archived.empty')}</p>
            ) : (
              <ul className="space-y-2" aria-label={t('knowledge.archived.title')}>
                {items.map((item) => (
                  <li key={item.id} className="flex items-center gap-3 rounded-xl border border-border/60 bg-card p-3">
                    <div className="min-w-0 flex-1">
                      <p className="truncate font-medium text-foreground" title={item.title}>{item.title}</p>
                      <p className="mt-0.5 truncate text-xs text-muted-foreground">
                        {t('knowledge.archived.meta', {
                          versions: item.versionCount,
                          size: formatFileSize(item.storedBytes),
                          owner: item.owner.displayName,
                          time: formatLocalDateTime(item.updatedAt, i18n.language, { dateStyle: 'short', timeStyle: 'short' }),
                        })}
                      </p>
                    </div>
                    {item.canPurge ? (
                      <Button
                        variant="outline"
                        size="sm"
                        className="shrink-0 text-destructive hover:text-destructive"
                        aria-label={`${t('knowledge.archived.purge')} ${item.title}`}
                        onClick={() => setPurging(item)}
                      >
                        <Trash2 className="mr-1.5 h-3.5 w-3.5" aria-hidden />
                        {t('knowledge.archived.purge')}
                      </Button>
                    ) : null}
                  </li>
                ))}
              </ul>
            )}
            {totalPages > 1 ? <Pagination page={page} totalPages={totalPages} onPageChange={setPage} /> : null}
          </div>
          {canPurgeAny ? (
            <div className="border-t border-border/60 px-6 py-4">
              <Button variant="destructive" className="w-full" disabled={purgeAll.isPending} onClick={() => setConfirmAll(true)}>
                <Trash2 className="mr-1.5 h-4 w-4" aria-hidden />
                {t('knowledge.archived.purgeAll', { count: batchCount })}
              </Button>
            </div>
          ) : null}
        </DialogContent>
      </Dialog>
      <ConfirmDialog
        open={purging !== null}
        onOpenChange={(next) => !next && setPurging(null)}
        title={t('knowledge.archived.purgeTitle', { title: purging?.title })}
        description={t('knowledge.archived.purgeDescription', {
          size: formatFileSize(purging?.storedBytes ?? 0),
          versions: purging?.versionCount ?? 0,
        })}
        variant="destructive"
        confirmText={t('knowledge.archived.purge')}
        onConfirm={confirmPurge}
      />
      <ConfirmDialog
        open={confirmAll}
        onOpenChange={setConfirmAll}
        title={t('knowledge.archived.purgeAllTitle', { count: batchCount })}
        description={
          <>
            {t('knowledge.archived.purgeAllDescription', { count: batchCount })}
            {total > PURGE_BATCH_LIMIT ? (
              <span className="mt-1 block">{t('knowledge.archived.purgeAllLimit', { total, limit: PURGE_BATCH_LIMIT })}</span>
            ) : null}
          </>
        }
        variant="destructive"
        confirmText={t('knowledge.archived.purge')}
        onConfirm={confirmPurgeAll}
      />
    </>
  )
}

function StorageQuotaSummary({ quota }: { quota: UserStorageQuota }) {
  const { t } = useTranslation()
  const used = formatFileSize(quota.usedBytes)
  if (quota.unlimited || !quota.quotaBytes) {
    return <p className="text-xs text-muted-foreground">{t('knowledge.archived.quotaUnlimited', { used })}</p>
  }
  const percent = Math.min(Math.round((quota.usedBytes / quota.quotaBytes) * 100), 100)
  return (
    <div className="space-y-1">
      <p className="text-xs text-muted-foreground">{t('knowledge.archived.quotaUsed', { used, quota: formatFileSize(quota.quotaBytes) })}</p>
      <div
        role="progressbar"
        aria-label={t('knowledge.archived.quotaLabel')}
        aria-valuemin={0}
        aria-valuemax={100}
        aria-valuenow={percent}
        className="h-1.5 overflow-hidden rounded-full bg-muted"
      >
        <div className={percent >= 90 ? 'h-full bg-destructive' : 'h-full bg-primary'} style={{ width: `${percent}%` }} />
      </div>
    </div>
  )
}
