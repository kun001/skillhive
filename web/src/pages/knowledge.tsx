import { useNavigate } from '@tanstack/react-router'
import { BookOpen, Plus, Search } from 'lucide-react'
import { useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import type { KnowledgeBase } from '@/api/knowledge-types'
import { APP_SHELL_PAGE_CLASS_NAME } from '@/app/page-shell-style'
import { useAuth } from '@/features/auth/use-auth'
import { CreateKnowledgeBaseDialog } from '@/features/knowledge/base-dialog'
import { useKnowledgeBases } from '@/features/knowledge/use-knowledge-queries'
import { useMyNamespaces } from '@/features/namespace/use-my-namespaces'
import { EmptyState } from '@/shared/components/empty-state'
import { formatLocalDateTime } from '@/shared/lib/date-time'
import { Button } from '@/shared/ui/button'
import { Input } from '@/shared/ui/input'

/** Knowledge bases the caller can see, grouped by team space. */
export function KnowledgePage() {
  const { t, i18n } = useTranslation()
  const navigate = useNavigate()
  const { hasRole } = useAuth()
  const bases = useKnowledgeBases()
  const myNamespaces = useMyNamespaces()
  const [filter, setFilter] = useState('')
  const [createOpen, setCreateOpen] = useState(false)

  const manageableNamespaces = useMemo(
    () =>
      (myNamespaces.data ?? [])
        .filter((namespace) => namespace.type === 'TEAM' && namespace.status === 'ACTIVE')
        .filter((namespace) => hasRole('SUPER_ADMIN') || namespace.currentUserRole === 'OWNER' || namespace.currentUserRole === 'ADMIN')
        .map((namespace) => ({ slug: namespace.slug, displayName: namespace.displayName })),
    [myNamespaces.data, hasRole],
  )

  const groups = useMemo(() => {
    const keyword = filter.trim().toLowerCase()
    const visible = (bases.data ?? []).filter(
      (base) =>
        !keyword
        || base.displayName.toLowerCase().includes(keyword)
        || (base.description ?? '').toLowerCase().includes(keyword)
        || base.namespaceDisplayName.toLowerCase().includes(keyword),
    )
    const byNamespace = new Map<string, { displayName: string; bases: KnowledgeBase[] }>()
    for (const base of visible) {
      const group = byNamespace.get(base.namespace) ?? { displayName: base.namespaceDisplayName, bases: [] }
      group.bases.push(base)
      byNamespace.set(base.namespace, group)
    }
    return [...byNamespace.entries()]
  }, [bases.data, filter])

  const openBase = (base: KnowledgeBase) =>
    navigate({ to: '/knowledge/$namespace/$base', params: { namespace: base.namespace, base: base.slug } })

  const canCreate = manageableNamespaces.length > 0
  const total = bases.data?.length ?? 0

  return (
    <div className={APP_SHELL_PAGE_CLASS_NAME}>
      <div className="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <h1 className="text-3xl font-semibold text-foreground">{t('knowledge.title')}</h1>
          <p className="mt-2 text-sm text-muted-foreground">{t('knowledge.subtitle')}</p>
        </div>
        <div className="flex items-center gap-2">
          {total > 0 ? (
            <div className="relative w-full sm:w-64">
              <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" aria-hidden />
              <Input
                value={filter}
                onChange={(event) => setFilter(event.target.value)}
                placeholder={t('knowledge.searchBases')}
                aria-label={t('knowledge.searchBases')}
                className="pl-9"
              />
            </div>
          ) : null}
          {canCreate ? (
            <Button onClick={() => setCreateOpen(true)} className="shrink-0">
              <Plus className="mr-1.5 h-4 w-4" aria-hidden />
              {t('knowledge.createBase')}
            </Button>
          ) : null}
        </div>
      </div>

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
      ) : groups.length === 0 ? (
        <EmptyState title={t('knowledge.noMatch')} />
      ) : (
        <div className="space-y-8">
          {groups.map(([namespace, group]) => (
            <section key={namespace} className="space-y-3">
              <h2 className="flex items-baseline gap-2 text-sm font-semibold text-foreground">
                {group.displayName}
                <span className="font-normal text-muted-foreground">@{namespace}</span>
              </h2>
              <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
                {group.bases.map((base) => (
                  <button
                    key={base.id}
                    type="button"
                    onClick={() => openBase(base)}
                    className="group flex flex-col rounded-xl border border-border/60 bg-card p-5 text-left transition-all hover:-translate-y-0.5 hover:border-primary/30 hover:shadow-md focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
                  >
                    <div className="flex items-center gap-3">
                      <span className="flex h-10 w-10 items-center justify-center rounded-xl bg-primary/10 text-primary">
                        <BookOpen className="h-5 w-5" aria-hidden />
                      </span>
                      <span className="min-w-0 flex-1 truncate font-semibold text-foreground">{base.displayName}</span>
                    </div>
                    <p className="mt-3 line-clamp-2 min-h-[2.5rem] text-sm text-muted-foreground">{base.description || ' '}</p>
                    <p className="mt-4 text-xs text-muted-foreground">
                      {t('knowledge.fileCount', { count: base.documentCount })}
                      {' · '}
                      {t('knowledge.updatedAt', { time: formatLocalDateTime(base.updatedAt, i18n.language, { dateStyle: 'medium' }) })}
                    </p>
                  </button>
                ))}
              </div>
            </section>
          ))}
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
