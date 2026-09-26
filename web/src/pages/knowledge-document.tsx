import { useNavigate, useParams } from '@tanstack/react-router'
import { Download, History, MoreHorizontal, Pencil, Trash2, Upload } from 'lucide-react'
import { useMemo, useState, type ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import { knowledgeApi } from '@/api/client'
import { EditKnowledgeDocumentDialog, NewKnowledgeVersionDialog } from '@/features/knowledge/document-dialogs'
import { knowledgeErrorMessage } from '@/features/knowledge/errors'
import { KnowledgeFileIcon } from '@/features/knowledge/file-icon'
import { KnowledgeFilePreview } from '@/features/knowledge/file-preview'
import { formatFileSize } from '@/features/knowledge/file-types'
import { buildFolderTree } from '@/features/knowledge/folder-tree'
import { KnowledgeBreadcrumb } from '@/features/knowledge/knowledge-breadcrumb'
import { useDeleteKnowledgeDocument, useKnowledgeDocument, useKnowledgeFolders } from '@/features/knowledge/use-knowledge-queries'
import { KnowledgeVersionDrawer } from '@/features/knowledge/version-drawer'
import { ConfirmDialog } from '@/shared/components/confirm-dialog'
import { CopyButton } from '@/shared/components/copy-button'
import { EmptyState } from '@/shared/components/empty-state'
import { formatLocalDateTime } from '@/shared/lib/date-time'
import { toast } from '@/shared/lib/toast'
import { Button, buttonVariants } from '@/shared/ui/button'
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuSeparator, DropdownMenuTrigger } from '@/shared/ui/dropdown-menu'

function InfoRow({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="space-y-0.5">
      <dt className="text-xs text-muted-foreground">{label}</dt>
      <dd className="break-words text-sm text-foreground [overflow-wrap:anywhere]">{children}</dd>
    </div>
  )
}

/** A single knowledge file: preview, details, versions and file actions. */
export function KnowledgeDocumentPage() {
  const { t, i18n } = useTranslation()
  const navigate = useNavigate()
  const { namespace, base, documentId } = useParams({ from: '/knowledge/$namespace/$base/$documentId' })
  const id = Number(documentId)
  const detail = useKnowledgeDocument(id)
  const folders = useKnowledgeFolders(namespace, base)
  const tree = useMemo(() => buildFolderTree(folders.data ?? []), [folders.data])
  const deleteDocument = useDeleteKnowledgeDocument()
  const [versionsOpen, setVersionsOpen] = useState(false)
  const [newVersionOpen, setNewVersionOpen] = useState(false)
  const [editOpen, setEditOpen] = useState(false)
  const [deleteOpen, setDeleteOpen] = useState(false)

  if (detail.isLoading) {
    return (
      <div className="space-y-4 animate-fade-up">
        <div className="h-8 w-64 animate-shimmer rounded-lg" />
        <div className="h-[60vh] animate-shimmer rounded-xl" />
      </div>
    )
  }
  if (detail.isError || !detail.data) {
    return <EmptyState title={t('knowledge.detail.notFound')} />
  }

  const { document, knowledgeBase, folderPath, sha256 } = detail.data
  const formatTime = (value: string) => formatLocalDateTime(value, i18n.language, { dateStyle: 'medium', timeStyle: 'short' })
  const backToBase = (folder?: number) =>
    navigate({ to: '/knowledge/$namespace/$base', params: { namespace, base }, search: folder ? { folder } : {} })

  return (
    <div className="space-y-6 animate-fade-up">
      <header className="space-y-3">
        <KnowledgeBreadcrumb
          items={[
            { label: t('knowledge.breadcrumbRoot'), to: '/knowledge' },
            { label: knowledgeBase.displayName, to: '/knowledge/$namespace/$base', params: { namespace, base }, search: {} },
            ...folderPath.map((folder) => ({
              label: folder.name,
              to: '/knowledge/$namespace/$base',
              params: { namespace, base },
              search: { folder: folder.id },
            })),
            { label: document.title },
          ]}
        />
        <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
          <div className="flex min-w-0 items-center gap-3">
            <KnowledgeFileIcon extension={document.fileExtension} size="md" />
            <div className="min-w-0">
              <h1 className="truncate text-2xl font-semibold text-foreground">{document.title}</h1>
              <p className="mt-0.5 flex flex-wrap items-center gap-2 text-xs text-muted-foreground">
                <span className="rounded-md bg-secondary px-1.5 py-0.5 font-medium text-secondary-foreground">v{document.currentVersion}</span>
                <span className="uppercase">{document.fileExtension}</span>
                <span>{formatFileSize(document.sizeBytes)}</span>
                <span>{document.owner.displayName}</span>
                <span>{formatTime(document.updatedAt)}</span>
              </p>
            </div>
          </div>
          <div className="flex shrink-0 items-center gap-2">
            <Button variant="outline" onClick={() => setVersionsOpen(true)}>
              <History className="mr-1.5 h-4 w-4" aria-hidden />
              {t('knowledge.actions.versions')}
            </Button>
            <a href={knowledgeApi.contentUrl(document.id)} download className={buttonVariants({ variant: 'default' })}>
              <Download className="mr-1.5 h-4 w-4" aria-hidden />
              {t('knowledge.actions.download')}
            </a>
            {knowledgeBase.canContribute ? (
              <DropdownMenu modal={false}>
                <DropdownMenuTrigger asChild>
                  <Button variant="outline" size="icon" aria-label={t('knowledge.actions.more')}>
                    <MoreHorizontal className="h-4 w-4" />
                  </Button>
                </DropdownMenuTrigger>
                <DropdownMenuContent align="end">
                  <DropdownMenuItem onSelect={() => setNewVersionOpen(true)}>
                    <Upload className="mr-2 h-4 w-4" aria-hidden />
                    {t('knowledge.actions.newVersion')}
                  </DropdownMenuItem>
                  {document.canManage ? (
                    <>
                      <DropdownMenuItem onSelect={() => setEditOpen(true)}>
                        <Pencil className="mr-2 h-4 w-4" aria-hidden />
                        {t('knowledge.actions.edit')}
                      </DropdownMenuItem>
                      <DropdownMenuSeparator />
                      <DropdownMenuItem className="text-destructive focus:text-destructive" onSelect={() => setDeleteOpen(true)}>
                        <Trash2 className="mr-2 h-4 w-4" aria-hidden />
                        {t('knowledge.actions.delete')}
                      </DropdownMenuItem>
                    </>
                  ) : null}
                </DropdownMenuContent>
              </DropdownMenu>
            ) : null}
          </div>
        </div>
      </header>

      <div className="grid grid-cols-1 gap-6 xl:grid-cols-[minmax(0,1fr)_18rem]">
        <section className="min-w-0 overflow-hidden rounded-xl border border-border/60 bg-card">
          <KnowledgeFilePreview key={`${document.id}-${document.currentVersion}`} document={document} />
        </section>
        <aside className="space-y-4 xl:sticky xl:top-24 xl:self-start">
          <div className="rounded-xl border border-border/60 bg-card p-4">
            <h2 className="mb-3 text-sm font-semibold text-foreground">{t('knowledge.detail.description')}</h2>
            <p className="whitespace-pre-wrap text-sm text-foreground/85">
              {document.description || <span className="text-muted-foreground">{t('knowledge.detail.noDescription')}</span>}
            </p>
          </div>
          <dl className="space-y-3 rounded-xl border border-border/60 bg-card p-4">
            <h2 className="text-sm font-semibold text-foreground">{t('knowledge.detail.info')}</h2>
            <InfoRow label={t('knowledge.detail.fileName')}>{document.fileName}</InfoRow>
            <InfoRow label={t('knowledge.detail.type')}>{document.fileExtension.toUpperCase()}</InfoRow>
            <InfoRow label={t('knowledge.detail.size')}>{formatFileSize(document.sizeBytes)}</InfoRow>
            <InfoRow label={t('knowledge.detail.currentVersion')}>v{document.currentVersion}</InfoRow>
            <InfoRow label={t('knowledge.detail.owner')}>{document.owner.displayName}</InfoRow>
            <InfoRow label={t('knowledge.detail.createdAt')}>{formatTime(document.createdAt)}</InfoRow>
            <InfoRow label={t('knowledge.detail.updatedAt')}>{formatTime(document.updatedAt)}</InfoRow>
            <InfoRow label={t('knowledge.detail.sha256')}>
              <span className="flex items-center gap-1">
                <code className="truncate font-mono text-xs" title={sha256}>{sha256.slice(0, 16)}…</code>
                <CopyButton text={sha256} ariaLabel={t('knowledge.detail.sha256')} />
              </span>
            </InfoRow>
          </dl>
        </aside>
      </div>

      <KnowledgeVersionDrawer open={versionsOpen} onOpenChange={setVersionsOpen} document={document} />
      <NewKnowledgeVersionDialog open={newVersionOpen} onOpenChange={setNewVersionOpen} document={document} />
      <EditKnowledgeDocumentDialog open={editOpen} onOpenChange={setEditOpen} document={document} tree={tree} />
      <ConfirmDialog
        open={deleteOpen}
        onOpenChange={setDeleteOpen}
        title={t('knowledge.deleteDialog.title', { title: document.title })}
        description={t('knowledge.deleteDialog.description')}
        variant="destructive"
        confirmText={t('knowledge.deleteDialog.confirm')}
        onConfirm={async () => {
          try {
            await deleteDocument.mutateAsync(document.id)
            toast.success(t('knowledge.deleteDialog.deleted'))
            setDeleteOpen(false)
            backToBase(document.folderId ?? undefined)
          } catch (error) {
            toast.error(knowledgeErrorMessage(error))
          }
        }}
      />
    </div>
  )
}
