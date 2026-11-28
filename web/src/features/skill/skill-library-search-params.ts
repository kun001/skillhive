import type { SearchParams } from '@/api/types'

export const ALL_SKILL_SPACES = '__all__'
export const TEAM_SKILL_SPACES = '__mine__'
export const SKILL_LIBRARY_PAGE_SIZE = 20

export function getSkillLibrarySearchParams({ q, label, scope, sort, page }: {
  q: string
  label?: string
  scope?: string
  sort: 'all' | 'downloads' | 'newest'
  page: number
}): SearchParams {
  const memberOnly = scope === TEAM_SKILL_SPACES
  return {
    q,
    label,
    namespace: scope && scope !== ALL_SKILL_SPACES && !memberOnly ? scope : undefined,
    library: memberOnly ? 'mine' : 'accessible',
    sort: sort === 'all' ? 'relevance' : sort,
    page,
    size: SKILL_LIBRARY_PAGE_SIZE,
  }
}
