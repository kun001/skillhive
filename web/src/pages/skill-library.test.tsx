import { renderToStaticMarkup } from 'react-dom/server'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const mocks = vi.hoisted(() => ({
  navigate: vi.fn(), searchSkills: vi.fn(),
  search: { q: 'agent', label: 'tools', sort: 'downloads', page: 2, view: 'list', library: 'public' },
  authenticated: false,
}))
vi.mock('@tanstack/react-router', () => ({
  useNavigate: () => mocks.navigate,
  useSearch: () => mocks.search,
  useRouterState: () => '/skills?library=team',
  Link: ({ children }: { children: React.ReactNode }) => <a>{children}</a>,
}))
vi.mock('react-i18next', () => ({ useTranslation: () => ({ t: (key: string) => key }) }))
vi.mock('@/features/auth/use-auth', () => ({ useAuth: () => ({ isAuthenticated: mocks.authenticated, hasRole: () => false }) }))
vi.mock('@/features/namespace/use-my-namespaces', () => ({ useMyNamespaces: () => ({ data: [] }) }))
vi.mock('@/shared/hooks/use-label-queries', () => ({ useVisibleLabels: () => ({ data: [] }) }))
vi.mock('@/shared/hooks/use-skill-queries', () => ({ useSearchSkills: mocks.searchSkills }))

import { SkillLibraryPage } from './skill-library'

describe('SkillLibraryPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mocks.search.library = 'public'
    mocks.authenticated = false
    mocks.searchSkills.mockReturnValue({ data: { items: [], total: 0 }, isLoading: false })
  })

  it('allows guests to browse public skills', () => {
    const html = renderToStaticMarkup(<SkillLibraryPage />)
    expect(mocks.searchSkills).toHaveBeenCalledWith(expect.objectContaining({ library: 'public' }), true)
    expect(html).toContain('skillLibrary.publicLibrary')
    expect(html).toContain('skillLibrary.teamLibrary')
    expect(html).not.toContain('skillLibrary.teamLoginTitle')
  })

  it('queries the selected team library with its search and pagination', () => {
    mocks.search.library = 'team'
    mocks.authenticated = true
    renderToStaticMarkup(<SkillLibraryPage />)
    expect(mocks.searchSkills).toHaveBeenCalledWith(expect.objectContaining({ library: 'team', page: 2, q: 'agent', label: 'tools', sort: 'downloads' }), true)
  })

  it('prompts guests to sign in without querying team skills', () => {
    mocks.search.library = 'team'
    const html = renderToStaticMarkup(<SkillLibraryPage />)
    expect(mocks.searchSkills).toHaveBeenCalledWith(expect.objectContaining({ library: 'team' }), false)
    expect(html).toContain('skillLibrary.teamLoginTitle')
  })
})
