import { useState, useEffect, useMemo } from 'react'
import { useNavigate, useParams, useSearch } from '@tanstack/react-router'
import { Search } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import type { KnowledgeBase } from '@/api/knowledge-types'
import { useAuth } from '@/features/auth/use-auth'
import { KnowledgeBaseCard } from '@/features/knowledge/base-card'
import { KnowledgeDocumentSearchResults } from '@/features/knowledge/document-search-results'
import { useKnowledgeBases, useKnowledgeDocumentSearch } from '@/features/knowledge/use-knowledge-queries'
import { NamespaceHeader } from '@/features/namespace/namespace-header'
import { SkillCard } from '@/features/skill/skill-card'
import { SkeletonList } from '@/shared/components/skeleton-loader'
import { EmptyState } from '@/shared/components/empty-state'
import { Pagination } from '@/shared/components/pagination'
import { useDebounce } from '@/shared/hooks/use-debounce'
import { useSearchSkills } from '@/shared/hooks/use-skill-queries'
import { useMyNamespaces, useNamespaceDetail } from '@/shared/hooks/use-namespace-queries'
import { Input } from '@/shared/ui/input'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/shared/ui/tabs'

const PAGE_SIZE = 20

type NamespaceTab = 'skills' | 'knowledge'

/**
 * Team space page: namespace metadata with its skills and knowledge bases side by side.
 * Knowledge is only visible to members, so other visitors see a members-only notice there.
 */
export function NamespacePage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const { namespace } = useParams({ from: '/space/$namespace' })
  const search = useSearch({ from: '/space/$namespace' })
  const { user, hasRole } = useAuth()
  const [page, setPage] = useState(0)
  const [keyword, setKeyword] = useState('')
  const debouncedKeyword = useDebounce(keyword, 300).trim()
  const [filePage, setFilePage] = useState(0)

  // Reset paging and search when namespace changes
  useEffect(() => {
    setPage(0)
    setKeyword('')
  }, [namespace])

  useEffect(() => {
    setFilePage(0)
  }, [debouncedKeyword, namespace])

  const { data: namespaceData, isLoading: isLoadingNamespace } = useNamespaceDetail(namespace)
  const { data: skillsData, isLoading: isLoadingSkills } = useSearchSkills({
    namespace,
    page,
    size: PAGE_SIZE,
  })
  const bases = useKnowledgeBases()
  const myNamespaces = useMyNamespaces(!!user)
  const namespaceBases = useMemo(
    () => (bases.data ?? []).filter((base) => base.namespace === namespace),
    [bases.data, namespace],
  )
  const fileSearch = useKnowledgeDocumentSearch({ q: debouncedKeyword, namespace, page: filePage, size: PAGE_SIZE })
  const fileHits = fileSearch.data?.items ?? []
  const fileTotal = fileSearch.data?.total ?? 0

  const isMember = hasRole('SUPER_ADMIN') || (myNamespaces.data ?? []).some((item) => item.slug === namespace)
  const skillTotal = skillsData?.total ?? 0
  const totalPages = skillsData ? Math.max(Math.ceil(skillsData.total / skillsData.size), 1) : 1
  // Open the tab that has content when the link does not pick one.
  const activeTab: NamespaceTab = search.tab
    ?? (!isLoadingSkills && skillTotal === 0 && namespaceBases.length > 0 ? 'knowledge' : 'skills')

  const selectTab = (tab: string) =>
    navigate({
      to: '/space/$namespace',
      params: { namespace },
      search: { tab: tab === 'knowledge' ? 'knowledge' : 'skills' },
      replace: true,
    })

  const handleSkillClick = (slug: string) => {
    navigate({ to: `/space/${namespace}/${encodeURIComponent(slug)}` })
  }

  const openBase = (base: KnowledgeBase) =>
    navigate({ to: '/knowledge/$namespace/$base', params: { namespace: base.namespace, base: base.slug } })

  if (isLoadingNamespace) {
    return (
      <div className="space-y-6 animate-fade-up">
        <div className="h-12 w-48 animate-shimmer rounded-lg" />
        <div className="h-6 w-96 animate-shimmer rounded-md" />
      </div>
    )
  }

  if (!namespaceData) {
    return <EmptyState title={t('namespace.notFound')} />
  }

  return (
    <div className="space-y-8 animate-fade-up">
      <NamespaceHeader namespace={namespaceData} />

      <Tabs value={activeTab} onValueChange={selectTab} className="space-y-6">
        <TabsList>
          <TabsTrigger value="skills">
            {t('namespace.tabSkills')}
            <span className="ml-1.5 text-xs text-muted-foreground">{skillsData ? skillTotal : ''}</span>
          </TabsTrigger>
          <TabsTrigger value="knowledge">
            {t('namespace.tabKnowledge')}
            <span className="ml-1.5 text-xs text-muted-foreground">{bases.data && isMember ? namespaceBases.length : ''}</span>
          </TabsTrigger>
        </TabsList>

        <TabsContent value="skills" className="space-y-6">
          {isLoadingSkills ? (
            <SkeletonList count={6} />
          ) : skillsData && skillsData.items.length > 0 ? (
            <>
              <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-5">
                {skillsData.items.map((skill, idx) => (
                  <div key={skill.id} className={`relative animate-fade-up delay-${Math.min(idx + 1, 6)}`}>
                    <SkillCard
                      skill={skill}
                      onClick={() => handleSkillClick(skill.slug)}
                    />
                  </div>
                ))}
              </div>

              {skillsData.total > PAGE_SIZE ? (
                <Pagination page={page} totalPages={totalPages} onPageChange={setPage} />
              ) : null}
            </>
          ) : (
            <EmptyState
              title={t('namespace.emptyTitle')}
              description={t('namespace.emptyDescription')}
            />
          )}
        </TabsContent>

        <TabsContent value="knowledge" className="space-y-6">
          {bases.isLoading ? (
            <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
              {Array.from({ length: 3 }).map((_, index) => (
                <div key={index} className="h-36 animate-shimmer rounded-xl" />
              ))}
            </div>
          ) : !isMember ? (
            <EmptyState title={t('namespace.knowledgeMembersOnly')} description={t('namespace.knowledgeMembersOnlyDescription')} />
          ) : namespaceBases.length === 0 ? (
            <EmptyState title={t('namespace.knowledgeEmptyTitle')} description={t('namespace.knowledgeEmptyDescription')} />
          ) : (
            <>
              <div className="relative">
                <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" aria-hidden />
                <Input
                  type="search"
                  value={keyword}
                  onChange={(event) => setKeyword(event.target.value)}
                  placeholder={t('namespace.knowledgeSearch')}
                  aria-label={t('namespace.knowledgeSearch')}
                  className="pl-9"
                />
              </div>

              {debouncedKeyword ? (
                fileSearch.isLoading ? (
                  <div className="space-y-2">
                    {Array.from({ length: 3 }).map((_, index) => (
                      <div key={index} className="h-14 animate-shimmer rounded-xl" />
                    ))}
                  </div>
                ) : fileSearch.isError ? (
                  <EmptyState title={t('knowledge.search.failed')} />
                ) : fileHits.length > 0 ? (
                  <>
                    <KnowledgeDocumentSearchResults hits={fileHits} showNamespace={false} />
                    {fileTotal > PAGE_SIZE ? (
                      <Pagination
                        page={filePage}
                        totalPages={Math.max(Math.ceil(fileTotal / PAGE_SIZE), 1)}
                        onPageChange={setFilePage}
                      />
                    ) : null}
                  </>
                ) : (
                  <div className="rounded-xl border border-dashed border-border/70">
                    <EmptyState title={t('knowledge.search.noFiles')} />
                  </div>
                )
              ) : (
                <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
                  {namespaceBases.map((base) => (
                    <KnowledgeBaseCard key={base.id} base={base} onOpen={openBase} showNamespace={false} />
                  ))}
                </div>
              )}
            </>
          )}
        </TabsContent>
      </Tabs>
    </div>
  )
}
