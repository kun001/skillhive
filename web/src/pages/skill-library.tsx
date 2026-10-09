import { getSkillSummaryDescription } from '@/features/skill/skill-summary-description'
import { useEffect, useState, type FormEvent } from 'react'
import { Link, useNavigate, useRouterState, useSearch } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import { Download, Grid2X2, List, Package, Plus, Search, Star } from 'lucide-react'
import type { SkillSummary } from '@/api/types'
import { APP_SHELL_PAGE_CLASS_NAME } from '@/app/page-shell-style'
import { useVisibleLabels } from '@/shared/hooks/use-label-queries'
import { useSearchSkills } from '@/shared/hooks/use-skill-queries'
import { useMyNamespaces } from '@/features/namespace/use-my-namespaces'
import { useAuth } from '@/features/auth/use-auth'
import { normalizeSearchQuery } from '@/shared/lib/search-query'
import { formatCompactCount } from '@/shared/lib/number-format'
import { EmptyState } from '@/shared/components/empty-state'
import { Pagination } from '@/shared/components/pagination'
import { SkeletonList } from '@/shared/components/skeleton-loader'
import { buttonVariants } from '@/shared/ui/button'

const PAGE_SIZE = 20
type LibrarySort = 'all' | 'downloads' | 'newest'

function SkillLibraryItem({ skill, view, returnTo }: { skill: SkillSummary; view: 'list' | 'grid'; returnTo: string }) {
  const { t, i18n } = useTranslation()
  const description = getSkillSummaryDescription(skill, i18n.resolvedLanguage || i18n.language)
  const namespace = skill.namespace.replace(/^@/, '')
  const isGrid = view === 'grid'

  return (
    <Link
      to="/space/$namespace/$slug"
      params={{ namespace, slug: skill.slug }}
      search={{ returnTo }}
      className={`group flex min-w-0 gap-4 p-5 text-left transition-colors hover:bg-secondary/50 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-ring ${isGrid ? 'h-full flex-col' : 'items-start sm:items-center'}`}
      aria-label={`${skill.displayName} · @${namespace}/${skill.slug}`}
    >
      <span className="flex h-11 w-11 shrink-0 items-center justify-center rounded-xl bg-primary/10 text-primary" aria-hidden="true">
        <Package className="h-5 w-5" />
      </span>
      <span className="min-w-0 flex-1">
        <span className="flex flex-wrap items-center gap-2">
          <strong className="min-w-0 break-words text-base font-semibold text-foreground">{skill.displayName}</strong>
          <span className="rounded-full border border-border px-2 py-0.5 text-[11px] text-muted-foreground">@{namespace}</span>
        </span>
        <span className="mt-1 block truncate text-sm text-muted-foreground" title={description || undefined}>
          {description || t('skillLibrary.noSummary')}
        </span>
      </span>
      <span className={`flex shrink-0 items-center gap-4 text-xs text-muted-foreground ${isGrid ? 'w-full border-t border-border/60 pt-3' : 'hidden sm:flex'}`}>
        <span className="inline-flex items-center gap-1" title={t('skillLibrary.stars')}><Star className="h-3.5 w-3.5" aria-hidden="true" />{formatCompactCount(skill.starCount)}</span>
        <span className="inline-flex items-center gap-1" title={t('skillLibrary.downloads')}><Download className="h-3.5 w-3.5" aria-hidden="true" />{formatCompactCount(skill.downloadCount)}</span>
      </span>
    </Link>
  )
}

/** Published Skills, searchable and sortable from one dedicated library route. */
export function SkillLibraryPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const returnTo = useRouterState({ select: (state) => state.location.href })
  const { q, label, sort, page, view, library = 'public' } = useSearch({ from: '/skills' })
  const { isAuthenticated, hasRole } = useAuth()
  const [queryInput, setQueryInput] = useState(q)
  const labels = useVisibleLabels()
  const skills = useSearchSkills({ q, label, library, sort: sort === 'all' ? 'relevance' : sort, page, size: PAGE_SIZE }, library === 'public' || isAuthenticated)
  const namespaces = useMyNamespaces(isAuthenticated)
  const canPublish = library === 'public'
    ? hasRole('SUPER_ADMIN') || hasRole('SKILL_ADMIN')
    : namespaces.data?.some((namespace) => namespace.type === 'TEAM' && namespace.status === 'ACTIVE' && namespace.canEdit !== false)
  const total = skills.data?.total ?? 0
  const totalPages = Math.ceil(total / PAGE_SIZE)

  useEffect(() => setQueryInput(q), [q])

  const update = (patch: Partial<{ q: string; label?: string; library: 'public' | 'team'; sort: LibrarySort; page: number; view: 'list' | 'grid' }>) => {
    void navigate({ to: '/skills', search: { q, label, library, sort, page, view, ...patch } })
  }

  const submitSearch = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    update({ q: normalizeSearchQuery(queryInput), page: 0 })
  }

  return (
    <div className={`${APP_SHELL_PAGE_CLASS_NAME} mx-auto w-full max-w-[1280px]`}>
      <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <div className="flex min-w-0 flex-wrap items-baseline gap-x-4 gap-y-2">
          <h1 className="text-3xl font-semibold text-foreground">{t('skillLibrary.title')}</h1>
          <p className="text-sm text-muted-foreground">{t(`skillLibrary.${library}Description`)}{skills.data && (library === 'public' || isAuthenticated) ? ` · ${t('skillLibrary.count', { count: total })}` : ''}</p>
        </div>
        {canPublish ? <Link
          to="/dashboard/publish"
          search={library === 'public' ? { namespace: 'global', visibility: 'PUBLIC' } : {}}
          className={buttonVariants({ className: 'shrink-0 self-start sm:self-auto' })}
        >
          <Plus className="h-4 w-4" aria-hidden="true" />
          {t('publish.title')}
        </Link> : null}
      </div>

      <section aria-label={t('skillLibrary.title')} className="space-y-4">
        <div role="group" aria-label={t('skillLibrary.libraryLabel')} className="flex flex-wrap gap-2">
          {(['public', 'team'] as const).map((option) => (
            <button key={option} type="button" aria-pressed={library === option}
              onClick={() => update({ library: option, page: 0 })}
              className={buttonVariants({ variant: library === option ? 'default' : 'outline' })}>
              {t(`skillLibrary.${option}Library`)}
            </button>
          ))}
        </div>
        <div className="flex flex-wrap items-center justify-between gap-3 border-b border-border pb-3">
          <div className="flex flex-wrap items-center gap-1" role="group" aria-label={t('skillLibrary.sortLabel')}>
            {(['all', 'downloads', 'newest'] as const).map((option) => (
              <button
                key={option}
                type="button"
                onClick={() => update({ sort: option, page: 0 })}
                aria-pressed={sort === option}
                className={`rounded-lg px-3 py-2 text-sm transition-colors ${sort === option ? 'bg-secondary font-semibold text-foreground' : 'text-muted-foreground hover:bg-secondary/60 hover:text-foreground'}`}
              >
                {t(`skillLibrary.sort.${option}`)}
              </button>
            ))}
          </div>
          <div className="flex flex-wrap items-center gap-2">
            <select
              value={label ?? ''}
              onChange={(event) => update({ label: event.target.value || undefined, page: 0 })}
              aria-label={t('skillLibrary.labelFilter')}
              className="h-9 max-w-[12rem] rounded-lg border border-border bg-background px-3 text-sm text-foreground"
            >
              <option value="">{t('skillLibrary.allLabels')}</option>
              {(labels.data ?? []).map((item) => <option key={item.slug} value={item.slug}>{item.displayName}</option>)}
            </select>
            <div className="flex rounded-lg border border-border p-0.5" role="group" aria-label={t('skillLibrary.viewLabel')}>
              <button type="button" onClick={() => update({ view: 'list' })} aria-label={t('skillLibrary.listView')} aria-pressed={view === 'list'} className={`rounded-md p-1.5 ${view === 'list' ? 'bg-secondary text-foreground' : 'text-muted-foreground'}`}><List className="h-4 w-4" /></button>
              <button type="button" onClick={() => update({ view: 'grid' })} aria-label={t('skillLibrary.gridView')} aria-pressed={view === 'grid'} className={`rounded-md p-1.5 ${view === 'grid' ? 'bg-secondary text-foreground' : 'text-muted-foreground'}`}><Grid2X2 className="h-4 w-4" /></button>
            </div>
          </div>
        </div>

        <form role="search" onSubmit={submitSearch} className="flex min-h-11 items-center gap-2 rounded-xl border border-border bg-card px-4 focus-within:ring-2 focus-within:ring-ring/30">
          <Search className="h-4 w-4 shrink-0 text-muted-foreground" aria-hidden="true" />
          <input
            type="search"
            value={queryInput}
            onChange={(event) => setQueryInput(event.target.value)}
            placeholder={t('skillLibrary.searchPlaceholder')}
            aria-label={t('skillLibrary.searchPlaceholder')}
            className="min-w-0 flex-1 bg-transparent py-2 text-sm text-foreground outline-none placeholder:text-muted-foreground"
          />
          <button type="submit" className="text-sm font-medium text-primary hover:underline">{t('skillLibrary.search')}</button>
        </form>

        {library === 'team' && !isAuthenticated ? (
          <EmptyState title={t('skillLibrary.teamLoginTitle')} description={t('skillLibrary.teamLoginDescription')} action={
            <Link to="/login" search={{ returnTo }} className={buttonVariants()}>{t('skillLibrary.login')}</Link>
          } />
        ) : skills.isLoading ? (
          <SkeletonList count={6} />
        ) : skills.isError ? (
          <EmptyState title={t('skillLibrary.loadFailed')} />
        ) : skills.data?.items.length ? (
          <>
            <div className={view === 'grid' ? 'grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3' : 'overflow-hidden rounded-xl border border-border bg-card'}>
              {skills.data.items.map((skill) => (
                <div key={skill.id} className={view === 'grid' ? 'overflow-hidden rounded-xl border border-border bg-card' : 'border-b border-border/70 last:border-b-0'}>
                  <SkillLibraryItem skill={skill} view={view} returnTo={returnTo} />
                </div>
              ))}
            </div>
            {totalPages > 1 ? <Pagination page={page} totalPages={totalPages} onPageChange={(nextPage) => update({ page: nextPage })} /> : null}
          </>
        ) : (
          <EmptyState title={q || label ? t('skillLibrary.noMatch') : t('skillLibrary.empty')} />
        )}
      </section>
    </div>
  )
}
