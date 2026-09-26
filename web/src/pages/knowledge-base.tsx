import { useNavigate, useParams, useSearch } from '@tanstack/react-router'
import { LayoutGrid, List, Search, Settings2, Upload, UploadCloud } from 'lucide-react'
import { useEffect, useMemo, useRef, useState, type DragEvent } from 'react'
import { useTranslation } from 'react-i18next'
import type { KnowledgeDocument, KnowledgeFolder } from '@/api/knowledge-types'
import { useAuth } from '@/features/auth/use-auth'
import { EditKnowledgeBaseDialog } from '@/features/knowledge/base-dialog'
import { EditKnowledgeDocumentDialog, NewKnowledgeVersionDialog } from '@/features/knowledge/document-dialogs'
import { KnowledgeDocumentGrid, KnowledgeDocumentTable } from '@/features/knowledge/document-list'
import { knowledgeErrorMessage } from '@/features/knowledge/errors'
import { extensionsForCategory, KNOWLEDGE_FILE_CATEGORIES } from '@/features/knowledge/file-types'
import { KnowledgeFolderDialog } from '@/features/knowledge/folder-dialog'
import { buildFolderTree, folderAncestry } from '@/features/knowledge/folder-tree'
import { KnowledgeFolderTreeNav } from '@/features/knowledge/folder-tree-nav'
import { KnowledgeBreadcrumb } from '@/features/knowledge/knowledge-breadcrumb'
import { updatedFromForRange, type KnowledgeBaseSearch } from '@/features/knowledge/search-params'
import { KnowledgeUploadDialog } from '@/features/knowledge/upload-dialog'
import {
  useDeleteKnowledgeDocument,
  useDeleteKnowledgeFolder,
  useKnowledgeBase,
  useKnowledgeDocuments,
  useKnowledgeFolders,
} from '@/features/knowledge/use-knowledge-queries'
import { ConfirmDialog } from '@/shared/components/confirm-dialog'
import { EmptyState } from '@/shared/components/empty-state'
import { Pagination } from '@/shared/components/pagination'
import { useDebounce } from '@/shared/hooks/use-debounce'
import { toast } from '@/shared/lib/toast'
import { cn } from '@/shared/lib/utils'
import { Button } from '@/shared/ui/button'
import { Input } from '@/shared/ui/input'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/shared/ui/select'

const PAGE_SIZE = 20
const ALL = '__all__'

type FolderDialogState = { folder?: KnowledgeFolder; parentId?: number } | null

/** Knowledge base workspace: folder tree, filters, file list and uploads. */
export function KnowledgeBasePage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const { namespace, base } = useParams({ from: '/knowledge/$namespace/$base' })
  const search = useSearch({ from: '/knowledge/$namespace/$base' })
  const { user } = useAuth()

  const baseQuery = useKnowledgeBase(namespace, base)
  const foldersQuery = useKnowledgeFolders(namespace, base)
  const folders = useMemo(() => foldersQuery.data ?? [], [foldersQuery.data])
  const tree = useMemo(() => buildFolderTree(folders), [folders])
  const ancestry = useMemo(() => folderAncestry(folders, search.folder), [folders, search.folder])
  const currentFolder = ancestry.at(-1)

  const [keyword, setKeyword] = useState(search.q ?? '')
  const debouncedKeyword = useDebounce(keyword, 300)

  const updateSearch = (patch: Partial<KnowledgeBaseSearch>) =>
    navigate({
      to: '/knowledge/$namespace/$base',
      params: { namespace, base },
      search: { ...search, page: undefined, ...patch },
      replace: true,
    })

  useEffect(() => {
    if ((debouncedKeyword.trim() || undefined) !== search.q) {
      updateSearch({ q: debouncedKeyword.trim() || undefined })
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps -- only react to the debounced input
  }, [debouncedKeyword])

  const updatedFrom = useMemo(() => updatedFromForRange(search.time), [search.time])
  const page = Math.max((search.page ?? 1) - 1, 0)
  const documentsQuery = useKnowledgeDocuments(namespace, base, {
    folderId: search.folder,
    q: search.q,
    extensions: extensionsForCategory(search.type),
    ownerId: search.owner === 'me' ? user?.userId : undefined,
    updatedFrom,
    sort: search.sort ?? 'updated',
    page,
    size: PAGE_SIZE,
  })
  const documents = documentsQuery.data?.items ?? []
  const totalPages = documentsQuery.data ? Math.max(Math.ceil(documentsQuery.data.total / PAGE_SIZE), 1) : 1
  const hasFilters = Boolean(search.q || search.type || search.owner || search.time)

  const [upload, setUpload] = useState<{ open: boolean; files?: File[] }>({ open: false })
  const [folderDialog, setFolderDialog] = useState<FolderDialogState>(null)
  const [deletingFolder, setDeletingFolder] = useState<KnowledgeFolder | null>(null)
  // Dialogs stay mounted while closing so Radix can release its body pointer lock.
  const [editing, setEditing] = useState<{ document: KnowledgeDocument; open: boolean } | null>(null)
  const [versioning, setVersioning] = useState<{ document: KnowledgeDocument; open: boolean } | null>(null)
  const [editBaseOpen, setEditBaseOpen] = useState(false)
  const [deletingDocument, setDeletingDocument] = useState<KnowledgeDocument | null>(null)
  const deleteFolder = useDeleteKnowledgeFolder(namespace, base)
  const deleteDocument = useDeleteKnowledgeDocument()

  const [dragDepth, setDragDepth] = useState(0)
  const dragCounter = useRef(0)
  const knowledgeBase = baseQuery.data
  const canContribute = knowledgeBase?.canContribute ?? false

  const isFileDrag = (event: DragEvent) => event.dataTransfer.types.includes('Files')
  const onDragEnter = (event: DragEvent) => {
    if (!canContribute || !isFileDrag(event)) return
    event.preventDefault()
    dragCounter.current += 1
    setDragDepth(dragCounter.current)
  }
  const onDragLeave = (event: DragEvent) => {
    if (!canContribute || !isFileDrag(event)) return
    dragCounter.current = Math.max(dragCounter.current - 1, 0)
    setDragDepth(dragCounter.current)
  }
  const onDrop = (event: DragEvent) => {
    if (!canContribute || !isFileDrag(event)) return
    event.preventDefault()
    dragCounter.current = 0
    setDragDepth(0)
    const files = Array.from(event.dataTransfer.files)
    if (files.length > 0) setUpload({ open: true, files })
  }

  const openDocument = (document: KnowledgeDocument) =>
    navigate({
      to: '/knowledge/$namespace/$base/$documentId',
      params: { namespace, base, documentId: String(document.id) },
    })

  if (baseQuery.isError) {
    return <EmptyState title={knowledgeErrorMessage(baseQuery.error)} />
  }

  return (
    <div className="space-y-6 animate-fade-up">
      <header className="space-y-2">
        <KnowledgeBreadcrumb
          items={[
            { label: t('knowledge.breadcrumbRoot'), to: '/knowledge' },
            { label: knowledgeBase?.namespaceDisplayName ?? namespace },
            { label: knowledgeBase?.displayName ?? base },
          ]}
        />
        <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
          <div className="min-w-0">
            <h1 className="truncate text-2xl font-semibold text-foreground">{knowledgeBase?.displayName ?? ' '}</h1>
            {knowledgeBase?.description ? <p className="mt-1 text-sm text-muted-foreground">{knowledgeBase.description}</p> : null}
          </div>
          <div className="flex shrink-0 items-center gap-2">
            {knowledgeBase?.canManage ? (
              <Button variant="outline" size="icon" aria-label={t('knowledge.baseSettings')} title={t('knowledge.baseSettings')} onClick={() => setEditBaseOpen(true)}>
                <Settings2 className="h-4 w-4" />
              </Button>
            ) : null}
            {canContribute ? (
              <Button onClick={() => setUpload({ open: true })}>
                <Upload className="mr-1.5 h-4 w-4" aria-hidden />
                {t('knowledge.toolbar.upload')}
              </Button>
            ) : null}
          </div>
        </div>
      </header>

      <div className="grid grid-cols-1 gap-6 lg:grid-cols-[15rem_minmax(0,1fr)]">
        <aside className="lg:sticky lg:top-24 lg:max-h-[calc(100vh-7rem)] lg:self-start lg:overflow-y-auto">
          <KnowledgeFolderTreeNav
            tree={tree}
            ancestry={ancestry}
            selectedFolderId={search.folder}
            totalCount={knowledgeBase?.documentCount ?? 0}
            canContribute={canContribute}
            onSelect={(folderId) => updateSearch({ folder: folderId })}
            onCreate={(parentId) => setFolderDialog({ parentId })}
            onEdit={(folder) => setFolderDialog({ folder })}
            onDelete={setDeletingFolder}
          />
        </aside>

        <section
          className="relative min-w-0 space-y-4"
          onDragEnter={onDragEnter}
          onDragOver={(event) => canContribute && isFileDrag(event) && event.preventDefault()}
          onDragLeave={onDragLeave}
          onDrop={onDrop}
        >
          <div className="flex flex-wrap items-center gap-2">
            <div className="relative min-w-[12rem] flex-1">
              <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" aria-hidden />
              <Input
                value={keyword}
                onChange={(event) => setKeyword(event.target.value)}
                placeholder={t('knowledge.toolbar.search')}
                aria-label={t('knowledge.toolbar.search')}
                className="pl-9"
              />
            </div>
            <Select value={search.type ?? ALL} onValueChange={(value) => updateSearch({ type: value === ALL ? undefined : value })}>
              <SelectTrigger className="w-[9.5rem]" aria-label={t('knowledge.toolbar.allTypes')}>
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value={ALL}>{t('knowledge.toolbar.allTypes')}</SelectItem>
                {KNOWLEDGE_FILE_CATEGORIES.map((category) => (
                  <SelectItem key={category.id} value={category.id}>{t(`knowledge.types.${category.id}`)}</SelectItem>
                ))}
              </SelectContent>
            </Select>
            <Select value={search.owner ?? ALL} onValueChange={(value) => updateSearch({ owner: value === 'me' ? 'me' : undefined })}>
              <SelectTrigger className="w-[9rem]" aria-label={t('knowledge.toolbar.allUploaders')}>
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value={ALL}>{t('knowledge.toolbar.allUploaders')}</SelectItem>
                <SelectItem value="me">{t('knowledge.toolbar.mine')}</SelectItem>
              </SelectContent>
            </Select>
            <Select
              value={search.time ?? ALL}
              onValueChange={(value) => updateSearch({ time: value === ALL ? undefined : (value as KnowledgeBaseSearch['time']) })}
            >
              <SelectTrigger className="w-[8.5rem]" aria-label={t('knowledge.toolbar.anyTime')}>
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value={ALL}>{t('knowledge.toolbar.anyTime')}</SelectItem>
                <SelectItem value="7">{t('knowledge.toolbar.last7')}</SelectItem>
                <SelectItem value="30">{t('knowledge.toolbar.last30')}</SelectItem>
                <SelectItem value="90">{t('knowledge.toolbar.last90')}</SelectItem>
              </SelectContent>
            </Select>
            <Select
              value={search.sort ?? 'updated'}
              onValueChange={(value) => updateSearch({ sort: value === 'updated' ? undefined : (value as KnowledgeBaseSearch['sort']) })}
            >
              <SelectTrigger className="w-[9rem]" aria-label={t('knowledge.toolbar.sortUpdated')}>
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="updated">{t('knowledge.toolbar.sortUpdated')}</SelectItem>
                <SelectItem value="created">{t('knowledge.toolbar.sortCreated')}</SelectItem>
                <SelectItem value="title">{t('knowledge.toolbar.sortTitle')}</SelectItem>
              </SelectContent>
            </Select>
            <div className="flex rounded-lg border border-border/60 p-0.5" role="group">
              {([['list', List, 'knowledge.toolbar.listView'], ['grid', LayoutGrid, 'knowledge.toolbar.gridView']] as const).map(([view, Icon, label]) => {
                const active = (search.view ?? 'list') === view
                return (
                  <button
                    key={view}
                    type="button"
                    aria-label={t(label)}
                    title={t(label)}
                    aria-pressed={active}
                    onClick={() => updateSearch({ view: view === 'grid' ? 'grid' : undefined, page: search.page })}
                    className={cn('flex h-8 w-8 items-center justify-center rounded-md transition-colors', active ? 'bg-primary/10 text-primary' : 'text-muted-foreground hover:text-foreground')}
                  >
                    <Icon className="h-4 w-4" />
                  </button>
                )
              })}
            </div>
          </div>

          {currentFolder ? (
            <KnowledgeBreadcrumb
              items={[
                { label: t('knowledge.tree.allFiles'), to: '/knowledge/$namespace/$base', params: { namespace, base }, search: { ...search, folder: undefined, page: undefined } },
                ...ancestry.map((folder, index) => ({
                  label: folder.name,
                  to: index < ancestry.length - 1 ? '/knowledge/$namespace/$base' : undefined,
                  params: { namespace, base },
                  search: { ...search, folder: folder.id, page: undefined },
                })),
              ]}
            />
          ) : null}

          {documentsQuery.isLoading ? (
            <div className="space-y-2">
              {Array.from({ length: 5 }).map((_, index) => (
                <div key={index} className="h-14 animate-shimmer rounded-xl" />
              ))}
            </div>
          ) : documents.length === 0 ? (
            <div className="rounded-xl border border-dashed border-border/70">
              <EmptyState
                title={t(hasFilters ? 'knowledge.empty.filtered' : 'knowledge.empty.title')}
                description={hasFilters || !canContribute ? undefined : t('knowledge.empty.description')}
                action={
                  hasFilters ? (
                    <Button
                      variant="outline"
                      onClick={() => {
                        setKeyword('')
                        updateSearch({ q: undefined, type: undefined, owner: undefined, time: undefined })
                      }}
                    >
                      {t('knowledge.empty.clearFilters')}
                    </Button>
                  ) : canContribute ? (
                    <Button onClick={() => setUpload({ open: true })}>{t('knowledge.toolbar.upload')}</Button>
                  ) : undefined
                }
              />
            </div>
          ) : search.view === 'grid' ? (
            <KnowledgeDocumentGrid
              documents={documents}
              canContribute={canContribute}
              onOpen={openDocument}
              onNewVersion={(document) => setVersioning({ document, open: true })}
              onEdit={(document) => setEditing({ document, open: true })}
              onDelete={setDeletingDocument}
            />
          ) : (
            <KnowledgeDocumentTable
              documents={documents}
              canContribute={canContribute}
              onOpen={openDocument}
              onNewVersion={(document) => setVersioning({ document, open: true })}
              onEdit={(document) => setEditing({ document, open: true })}
              onDelete={setDeletingDocument}
            />
          )}

          {totalPages > 1 ? (
            <Pagination page={page} totalPages={totalPages} onPageChange={(next) => updateSearch({ page: next > 0 ? next + 1 : undefined })} />
          ) : null}

          {dragDepth > 0 ? (
            <div className="pointer-events-none absolute inset-0 z-20 flex flex-col items-center justify-center gap-2 rounded-xl border-2 border-dashed border-primary bg-background/85 backdrop-blur-sm">
              <UploadCloud className="h-8 w-8 text-primary" aria-hidden />
              <p className="text-sm font-medium text-foreground">
                {t('knowledge.dropHint', { folder: currentFolder?.name ?? t('knowledge.tree.allFiles') })}
              </p>
            </div>
          ) : null}
        </section>
      </div>

      <KnowledgeUploadDialog
        open={upload.open}
        onOpenChange={(open) => setUpload(open ? upload : { open: false })}
        namespace={namespace}
        base={base}
        tree={tree}
        folderId={search.folder}
        initialFiles={upload.files}
      />
      <KnowledgeFolderDialog
        open={folderDialog !== null}
        onOpenChange={(open) => !open && setFolderDialog(null)}
        namespace={namespace}
        base={base}
        tree={tree}
        folder={folderDialog?.folder}
        parentId={folderDialog?.parentId}
        onSaved={(folder) => updateSearch({ folder: folder.id })}
      />
      <ConfirmDialog
        open={deletingFolder !== null}
        onOpenChange={(open) => !open && setDeletingFolder(null)}
        title={t('knowledge.folderDialog.deleteTitle', { name: deletingFolder?.name })}
        description={t('knowledge.folderDialog.deleteDescription')}
        variant="destructive"
        confirmText={t('knowledge.actions.delete')}
        onConfirm={async () => {
          if (!deletingFolder) return
          try {
            await deleteFolder.mutateAsync(deletingFolder.id)
            toast.success(t('knowledge.folderDialog.deleted'))
            if (search.folder === deletingFolder.id) {
              updateSearch({ folder: deletingFolder.parentId ?? undefined })
            }
          } catch (error) {
            toast.error(knowledgeErrorMessage(error))
          }
        }}
      />
      {editing ? (
        <EditKnowledgeDocumentDialog
          open={editing.open}
          onOpenChange={(open) => setEditing({ ...editing, open })}
          document={editing.document}
          tree={tree}
        />
      ) : null}
      {versioning ? (
        <NewKnowledgeVersionDialog
          open={versioning.open}
          onOpenChange={(open) => setVersioning({ ...versioning, open })}
          document={versioning.document}
        />
      ) : null}
      <ConfirmDialog
        open={deletingDocument !== null}
        onOpenChange={(open) => !open && setDeletingDocument(null)}
        title={t('knowledge.deleteDialog.title', { title: deletingDocument?.title })}
        description={t('knowledge.deleteDialog.description')}
        variant="destructive"
        confirmText={t('knowledge.deleteDialog.confirm')}
        onConfirm={async () => {
          if (!deletingDocument) return
          try {
            await deleteDocument.mutateAsync(deletingDocument.id)
            toast.success(t('knowledge.deleteDialog.deleted'))
          } catch (error) {
            toast.error(knowledgeErrorMessage(error))
          }
        }}
      />
      {knowledgeBase ? <EditKnowledgeBaseDialog open={editBaseOpen} onOpenChange={setEditBaseOpen} base={knowledgeBase} /> : null}
    </div>
  )
}
