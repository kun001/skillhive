import { useQuery } from '@tanstack/react-query'
import { Link, useNavigate, useParams, useRouterState, useSearch } from '@tanstack/react-router'
import { ArrowLeft, Download, Folder, Lock, Search } from 'lucide-react'
import { useEffect, useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { knowledgeApi, knowledgeShareApi } from '@/api/client'
import { APP_SHELL_PAGE_CLASS_NAME } from '@/app/page-shell-style'
import { useAuth } from '@/features/auth/use-auth'
import { KnowledgeFileIcon } from '@/features/knowledge/file-icon'
import { KnowledgeFilePreview } from '@/features/knowledge/file-preview'
import { formatFileSize } from '@/features/knowledge/file-types'
import { KnowledgeFolderSidebar } from '@/features/knowledge/folder-sidebar'
import { buildFolderTree, folderAncestry } from '@/features/knowledge/folder-tree'
import { KnowledgeFolderTreeNav } from '@/features/knowledge/folder-tree-nav'
import { KnowledgeBreadcrumb } from '@/features/knowledge/knowledge-breadcrumb'
import { knowledgeKeys } from '@/features/knowledge/use-knowledge-queries'
import { EmptyState } from '@/shared/components/empty-state'
import { Pagination } from '@/shared/components/pagination'
import { formatLocalDateTime } from '@/shared/lib/date-time'
import { Button, buttonVariants } from '@/shared/ui/button'
import { Input } from '@/shared/ui/input'

const SHARE_ROUTE = '/knowledge-share/$shareId'
const PAGE_SIZE = 20

export function KnowledgeSharePage() {
  const { t, i18n } = useTranslation()
  const { shareId } = useParams({ from: SHARE_ROUTE })
  const search = useSearch({ from: SHARE_ROUTE })
  const navigate = useNavigate()
  const returnTo = useRouterState({ select: (state) => state.location.href })
  const { user, isLoading: isLoadingAuth } = useAuth()
  const [keyword, setKeyword] = useState(search.q ?? '')
  useEffect(() => setKeyword(search.q ?? ''), [search.q])

  const shareKey = [...knowledgeKeys.all, 'share', shareId] as const
  const share = useQuery({
    queryKey: [...shareKey, 'overview', user?.userId ?? 'guest'],
    queryFn: () => knowledgeShareApi.get(shareId),
    retry: false,
    staleTime: 0,
    gcTime: 0,
    refetchInterval: ({ state }) => {
      const expiresAt = state.data?.expiresAt
      return state.status === 'success' && expiresAt
        ? Math.min(Math.max(Date.parse(expiresAt) - Date.now(), 1_000), 2_147_483_647)
        : false
    },
  })
  const standaloneId = share.data?.documentId ?? undefined
  const requestedId = standaloneId ?? search.file
  const folder = share.data?.folders.find((item) => item.id === search.folder)
  const unavailableFolder = search.folder !== undefined && !folder
  const canBrowse = share.isSuccess && !unavailableFolder && !folder?.entryOnly
  const page = Math.max((search.page ?? 1) - 1, 0)
  const files = useQuery({
    queryKey: [...shareKey, 'files', { folderId: search.folder, q: search.q, page }],
    enabled: canBrowse && requestedId === undefined,
    queryFn: ({ signal }) => knowledgeShareApi.listFiles(shareId, { folderId: search.folder, q: search.q, page, size: PAGE_SIZE }, signal),
    retry: false,
    staleTime: 0,
    gcTime: 0,
  })
  const file = useQuery({
    queryKey: [...shareKey, 'file', requestedId],
    enabled: requestedId !== undefined && (standaloneId !== undefined || canBrowse),
    queryFn: () => knowledgeShareApi.getFile(shareId, requestedId!),
    retry: false,
    staleTime: 0,
    gcTime: 0,
  })
  const activeFolderId = file.data?.folderId ?? search.folder
  const tree = useMemo(() => buildFolderTree(share.data?.folders ?? []), [share.data?.folders])
  const ancestry = useMemo(() => folderAncestry(share.data?.folders ?? [], activeFolderId), [share.data?.folders, activeFolderId])

  if (share.isPending) return <div role="status" className="h-40 animate-shimmer rounded-xl" aria-label={t('knowledge.preview.loading')} />
  if (share.isError) return <EmptyState title={t('knowledge.share.unavailable')} />

  const workspace = share.data.memberWorkspace
  const parentFolder = folder?.parentId ?? undefined
  const children = share.data.folders.filter((item) => (item.parentId ?? undefined) === search.folder)
  const totalPages = Math.ceil((files.data?.total ?? 0) / PAGE_SIZE)
  const preview = file.isPending
    ? <div role="status" className="h-40 animate-shimmer rounded-xl" aria-label={t('knowledge.preview.loading')} />
    : file.isError ? <EmptyState title={t('knowledge.detail.notFound')} />
      : (
        <article className="overflow-hidden rounded-xl border border-border bg-card">
          <div className="flex flex-wrap items-center justify-between gap-3 border-b border-border p-4">
            {standaloneId === undefined ? <h2 className="min-w-0 break-words text-lg font-semibold">{file.data.title}</h2> : null}
            <a href={knowledgeApi.contentUrl(file.data.id, { shareId })} download className={buttonVariants({ variant: 'outline' })}>
              <Download className="h-4 w-4" aria-hidden />{t('knowledge.actions.download')}
            </a>
          </div>
          <KnowledgeFilePreview key={`${file.data.id}-${file.data.currentVersion}`} document={file.data} shareId={shareId} />
        </article>
      )

  return (
    <div className={APP_SHELL_PAGE_CLASS_NAME}>
      <header className="flex flex-wrap items-start justify-between gap-4">
        <div className="min-w-0 space-y-2">
          {standaloneId === undefined && (search.folder !== undefined || search.file !== undefined) ? (
            <Link to={SHARE_ROUTE} params={{ shareId }}
              search={search.file !== undefined ? { ...search, file: undefined } : { folder: parentFolder }}
              className="inline-flex items-center gap-2 text-sm text-muted-foreground hover:text-foreground"
            >
              <ArrowLeft className="h-4 w-4" aria-hidden />
              {t('knowledge.share.back')}
            </Link>
          ) : null}
          <h1 className="break-words text-3xl font-semibold text-foreground">{share.data.displayName}</h1>
          {share.data.expiresAt ? (
            <p className="text-sm text-muted-foreground">
              {t('knowledge.share.validUntil', { time: formatLocalDateTime(share.data.expiresAt, i18n.language) })}
            </p>
          ) : null}
        </div>
        {workspace ? (
          <Link to="/knowledge/$namespace/$base" params={workspace} className={buttonVariants({ variant: 'outline' })}>
            {t('knowledge.share.workspace')}
          </Link>
        ) : null}
      </header>

      {standaloneId !== undefined ? preview : <div className="grid grid-cols-1 gap-6 lg:grid-cols-[auto_minmax(0,1fr)]">
        <KnowledgeFolderSidebar>
          <div className="max-h-56 overflow-y-auto lg:max-h-none lg:overflow-visible">
            <KnowledgeFolderTreeNav
              tree={tree}
              ancestry={ancestry}
              selectedFolderId={activeFolderId}
              expandSelected
              onSelect={(folderId) => void navigate({ to: SHARE_ROUTE, params: { shareId }, search: { folder: folderId } })}
            />
          </div>
        </KnowledgeFolderSidebar>
        <section className="min-w-0 space-y-4">
          <KnowledgeBreadcrumb items={[
            { label: t('knowledge.tree.root'), to: SHARE_ROUTE, params: { shareId }, search: {} },
            ...ancestry.map((entry) => ({ label: entry.name, to: SHARE_ROUTE, params: { shareId }, search: { folder: entry.id } })),
            ...(file.data ? [{ label: file.data.title }] : []),
          ]} />
          {unavailableFolder ? (
            <EmptyState title={t('knowledge.share.unavailable')} />
          ) : folder?.entryOnly ? (
            <div className="space-y-3 rounded-xl border border-border bg-card p-6 sm:p-8">
              <h2 className="flex items-center gap-2 text-xl font-semibold">
                <Lock className="h-5 w-5" aria-hidden />{folder.name}
              </h2>
              {workspace ? (
                <Link to="/knowledge/$namespace/$base" params={workspace} search={{ folder: folder.id }} className={buttonVariants()}>
                  {t('knowledge.share.openFolder')}
                </Link>
              ) : (
                <>
                  <p className="text-sm text-muted-foreground">
                    {t(user ? 'knowledge.share.requestAccess' : 'knowledge.share.loginRequired')}
                  </p>
                  {!user && !isLoadingAuth ? (
                    <Link to="/login" search={{ returnTo }} className={buttonVariants()}>{t('knowledge.share.login')}</Link>
                  ) : null}
                </>
              )}
            </div>
          ) : search.file !== undefined ? preview : (
            <div className="space-y-4">
              {folder ? <h2 className="text-xl font-semibold">{folder.name}</h2> : null}
              <form className="flex max-w-xl gap-2" onSubmit={(event) => {
                event.preventDefault()
                void navigate({ to: SHARE_ROUTE, params: { shareId }, search: { folder: search.folder, q: keyword.trim() || undefined } })
              }}>
                <Input type="search" aria-label={t('knowledge.share.search')} placeholder={t('knowledge.share.search')}
                  value={keyword} maxLength={100} onChange={(event) => {
                    setKeyword(event.target.value)
                    if (!event.target.value && search.q) {
                      void navigate({ to: SHARE_ROUTE, params: { shareId }, search: { folder: search.folder }, replace: true })
                    }
                  }}
                />
                <Button type="submit" variant="outline" aria-label={t('knowledge.share.submitSearch')}><Search className="h-4 w-4" aria-hidden /></Button>
              </form>
              {children.length > 0 ? (
                <ul className="divide-y divide-border overflow-hidden rounded-xl border border-border bg-card">
                  {children.map((child) => (
                    <li key={child.id}>
                      <Link to={SHARE_ROUTE} params={{ shareId }} search={{ folder: child.id }}
                        className="flex items-center gap-3 px-4 py-3 hover:bg-accent/50">
                        {child.entryOnly ? <Lock className="h-5 w-5 shrink-0 text-muted-foreground" aria-hidden /> : <Folder className="h-5 w-5 shrink-0 text-primary" aria-hidden />}
                        <span className="min-w-0 flex-1 break-words font-medium">{child.name}</span>
                        {child.entryOnly ? <span className="shrink-0 text-xs text-muted-foreground">{t('knowledge.share.authorization')}</span> : null}
                      </Link>
                    </li>
                  ))}
                </ul>
              ) : null}
              {files.isPending ? <div role="status" className="h-14 animate-shimmer rounded-xl" aria-label={t('knowledge.preview.loading')} /> : null}
              {files.isError ? <EmptyState title={t('knowledge.loadFailed')} /> : null}
              {files.data?.items.length ? (
                <ul className="divide-y divide-border overflow-hidden rounded-xl border border-border bg-card">
                  {files.data.items.map((item) => (
                    <li key={item.id}>
                      <Link to={SHARE_ROUTE} params={{ shareId }} search={{ ...search, file: item.id }}
                        className="flex items-center gap-3 px-4 py-3 hover:bg-accent/50">
                        <KnowledgeFileIcon extension={item.fileExtension} />
                        <span className="min-w-0 flex-1 break-words font-medium">{item.title}</span>
                        <span className="shrink-0 text-xs text-muted-foreground">{formatFileSize(item.sizeBytes)}</span>
                      </Link>
                    </li>
                  ))}
                </ul>
              ) : null}
              {files.isSuccess && !files.data?.items.length && (!children.length || search.q) ? (
                <EmptyState title={t(search.q ? 'knowledge.share.noResults' : 'knowledge.share.empty')} />
              ) : null}
              {totalPages > 1 ? (
                <Pagination page={page} totalPages={totalPages} onPageChange={(next) => {
                  void navigate({ to: SHARE_ROUTE, params: { shareId }, search: { ...search, page: next > 0 ? next + 1 : undefined } })
                }} />
              ) : null}
            </div>
          )}
        </section>
      </div>}
    </div>
  )
}
