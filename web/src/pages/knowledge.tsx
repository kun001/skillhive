import { useNavigate, useSearch } from '@tanstack/react-router'
import { Plus, Search } from 'lucide-react'
import { useEffect, useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import type { KnowledgeBase } from '@/api/knowledge-types'
import { APP_SHELL_PAGE_CLASS_NAME } from '@/app/page-shell-style'
import { useAuth } from '@/features/auth/use-auth'
import { KnowledgeBaseCard } from '@/features/knowledge/base-card'
import { CreateKnowledgeBaseDialog } from '@/features/knowledge/base-dialog'
import { KnowledgeDocumentSearchResults } from '@/features/knowledge/document-search-results'
import type { KnowledgeIndexSearch } from '@/features/knowledge/search-params'
import { useKnowledgeBases, useKnowledgeDocumentSearch } from '@/features/knowledge/use-knowledge-queries'
import { useMyNamespaces } from '@/features/namespace/use-my-namespaces'
import { EmptyState } from '@/shared/components/empty-state'
import { Pagination } from '@/shared/components/pagination'
import { useDebounce } from '@/shared/hooks/use-debounce'
import { Button } from '@/shared/ui/button'
import { Input } from '@/shared/ui/input'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/shared/ui/select'

const FILE_PAGE_SIZE = 20
const ALL = '__all__'

/** Knowledge bases the caller can see, with their team space, plus file search across all of them. */
export function KnowledgePage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const search = useSearch({ from: '/knowledge' })
  const { user, hasRole } = useAuth()
  const bases = useKnowledgeBases()
  const myNamespaces = useMyNamespaces(!!user)
  const [keyword, setKeyword] = useState(search.q ?? '')
  const debouncedKeyword = useDebounce(keyword, 300)
  const [filePage, setFilePage] = useState(0)
  const [createOpen, setCreateOpen] = useState(false)

  const updateSearch = (patch: Partial<KnowledgeIndexSearch>) =>
    navigate({ to: '/knowledge', search: { ...search, ...patch }, replace: true })

  useEffect(() => {
    const q = debouncedKeyword.trim() || undefined
    if (q !== search.q) {
      updateSearch({ q })
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps -- only react to the debounced input
  }, [debouncedKeyword])

  useEffect(() => {
    setFilePage(0)
  }, [search.q, search.namespace])

  const manageableNamespaces = useMemo(
    () =>
      (user ? myNamespaces.data ?? [] : [])
        .filter((namespace) => namespace.type === 'TEAM' && namespace.status === 'ACTIVE')
        .filter((namespace) => hasRole('SUPER_ADMIN') || namespace.currentUserRole === 'OWNER' || namespace.currentUserRole === 'ADMIN')
        .map((namespace) => ({ slug: namespace.slug, displayName: namespace.displayName })),
    [myNamespaces.data, hasRole, user],
  )

  /** Team spaces that own at least one visible knowledge base, for the filter. */
  const baseNamespaces = useMemo(() => {
    const seen = new Map<string, string>()
    for (const base of bases.data ?? []) {
      seen.set(base.namespace, base.namespaceDisplayName)
    }
    return [...seen].map(([slug, displayName]) => ({ slug, displayName }))
  }, [bases.data])
  const namespaceFilter = baseNamespaces.some((namespace) => namespace.slug === search.namespace) ? search.namespace : undefined

  const query = search.q ?? ''
  const visibleBases = useMemo(() => {
    const lowered = query.toLowerCase()
    return (bases.data ?? []).filter(
      (base) =>
        (!namespaceFilter || base.namespace === namespaceFilter)
        && (!lowered
          || base.displayName.toLowerCase().includes(lowered)
          || (base.description ?? '').toLowerCase().includes(lowered)
          || base.namespaceDisplayName.toLowerCase().includes(lowered)),
    )
  }, [bases.data, query, namespaceFilter])

  const fileSearch = useKnowledgeDocumentSearch({ q: query, namespace: namespaceFilter, page: filePage, size: FILE_PAGE_SIZE })
  const fileHits = fileSearch.data?.items ?? []
  const fileTotal = fileSearch.data?.total ?? 0
  const fileTotalPages = Math.max(Math.ceil(fileTotal / FILE_PAGE_SIZE), 1)

  const openBase = (base: KnowledgeBase) =>
    navigate({ to: '/knowledge/$namespace/$base', params: { namespace: base.namespace, base: base.slug } })

  const canCreate = manageableNamespaces.length > 0
  const total = bases.data?.length ?? 0
  const searching = query.length > 0

  const baseGrid = (
    <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
      {visibleBases.map((base) => (
        <KnowledgeBaseCard key={base.id} base={base} onOpen={openBase} />
      ))}
    </div>
  )

  return (
    <div className={APP_SHELL_PAGE_CLASS_NAME}>
      <div className="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <h1 className="text-3xl font-semibold text-foreground">{t('knowledge.title')}</h1>
          <p className="mt-2 text-sm text-muted-foreground">{t('knowledge.subtitle')}</p>
        </div>
        {canCreate ? (
          <Button onClick={() => setCreateOpen(true)} className="shrink-0 self-start sm:self-auto">
            <Plus className="mr-1.5 h-4 w-4" aria-hidden />
            {t('knowledge.createBase')}
          </Button>
        ) : null}
      </div>

      {total > 0 ? (
        <div className="flex flex-col gap-2 sm:flex-row">
          <div className="relative min-w-0 flex-1">
            <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" aria-hidden />
            <Input
              type="search"
              value={keyword}
              onChange={(event) => setKeyword(event.target.value)}
              placeholder={t('knowledge.searchBases')}
              aria-label={t('knowledge.searchBases')}
              className="pl-9"
            />
          </div>
          {baseNamespaces.length > 1 ? (
            <Select
              value={namespaceFilter ?? ALL}
              onValueChange={(value) => updateSearch({ namespace: value === ALL ? undefined : value })}
            >
              <SelectTrigger className="w-full sm:w-[12rem]" aria-label={t('knowledge.allNamespaces')}>
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value={ALL}>{t('knowledge.allNamespaces')}</SelectItem>
                {baseNamespaces.map((namespace) => (
                  <SelectItem key={namespace.slug} value={namespace.slug}>{namespace.displayName}</SelectItem>
                ))}
              </SelectContent>
            </Select>
          ) : null}
        </div>
      ) : null}

      {bases.isLoading ? (
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {Array.from({ length: 3 }).map((_, index) => (
            <div key={index} className="h-36 animate-shimmer rounded-xl" />
          ))}
        </div>
      ) : bases.isError ? (
        <EmptyState title={t('knowledge.loadFailed')} />
      ) : total === 0 ? (
        <EmptyState
          title={t('knowledge.emptyTitle')}
          description={t(canCreate ? 'knowledge.emptyAdmin' : 'knowledge.emptyMember')}
          action={canCreate ? <Button onClick={() => setCreateOpen(true)}>{t('knowledge.createBase')}</Button> : undefined}
        />
      ) : !searching ? (
        visibleBases.length === 0 ? <EmptyState title={t('knowledge.noMatch')} /> : baseGrid
      ) : (
        <div className="space-y-8">
          {visibleBases.length > 0 ? (
            <section className="space-y-3">
              <h2 className="text-sm font-semibold text-muted-foreground">
                {t('knowledge.search.basesHeading')} · {visibleBases.length}
              </h2>
              {baseGrid}
            </section>
          ) : null}

          <section className="space-y-3">
            <h2 className="text-sm font-semibold text-muted-foreground">
              {t('knowledge.search.filesHeading')}{fileSearch.data ? ` · ${fileTotal}` : ''}
            </h2>
            {fileSearch.isLoading ? (
              <div className="space-y-2">
                {Array.from({ length: 3 }).map((_, index) => (
                  <div key={index} className="h-14 animate-shimmer rounded-xl" />
                ))}
              </div>
            ) : fileSearch.isError ? (
              <EmptyState title={t('knowledge.search.failed')} />
            ) : fileHits.length > 0 ? (
              <>
                <KnowledgeDocumentSearchResults hits={fileHits} showNamespace={!namespaceFilter} />
                {fileTotalPages > 1 ? (
                  <Pagination page={filePage} totalPages={fileTotalPages} onPageChange={setFilePage} />
                ) : null}
              </>
            ) : (
              <div className="rounded-xl border border-dashed border-border/70">
                <EmptyState
                  title={visibleBases.length === 0 ? t('knowledge.search.noResults', { q: query }) : t('knowledge.search.noFiles')}
                />
              </div>
            )}
          </section>
        </div>
      )}

      <CreateKnowledgeBaseDialog
        open={createOpen}
        onOpenChange={setCreateOpen}
        namespaces={manageableNamespaces}
        onCreated={openBase}
      />
    </div>
  )
}
