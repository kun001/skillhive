import { renderToStaticMarkup } from 'react-dom/server'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const mocks = vi.hoisted(() => ({
  navigate: vi.fn(), searchSkills: vi.fn(),
  search: { q: 'agent', label: 'tools', sort: 'downloads', page: 2, view: 'list', scope: undefined as string | undefined },
  authenticated: false,
  loadingAuth: false,
}))
vi.mock('@tanstack/react-router', () => ({
  useNavigate: () => mocks.navigate,
  useSearch: () => mocks.search,
  useRouterState: () => '/skills',
  Link: ({ children }: { children: React.ReactNode }) => <a>{children}</a>,
}))
vi.mock('react-i18next', () => ({ useTranslation: () => ({ t: (key: string) => key, i18n: { resolvedLanguage: 'zh-CN' } }) }))
vi.mock('@/features/auth/use-auth', () => ({ useAuth: () => ({ isAuthenticated: mocks.authenticated, isLoading: mocks.loadingAuth, user: mocks.authenticated ? { userId: 'member' } : null, hasRole: () => false }) }))
vi.mock('@/features/namespace/use-my-namespaces', () => ({ useMyNamespaces: () => ({ data: [] }) }))
vi.mock('@/shared/hooks/use-label-queries', () => ({ useVisibleLabels: () => ({ data: [] }) }))
vi.mock('@/shared/hooks/use-skill-queries', () => ({ useSearchSkills: mocks.searchSkills }))

import { SkillLibraryPage } from './skill-library'

describe('SkillLibraryPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mocks.search.scope = undefined
    mocks.authenticated = false
    mocks.loadingAuth = false
    mocks.searchSkills.mockReturnValue({ data: { items: [], total: 0 }, isLoading: false })
  })

  it('shows one accessible library to guests', () => {
    const html = renderToStaticMarkup(<SkillLibraryPage />)
    expect(mocks.searchSkills).toHaveBeenCalledWith(expect.objectContaining({ library: 'accessible' }), true, undefined, 'omit')
    expect(html).not.toContain('skillLibrary.publicLibrary')
    expect(html).not.toContain('skillLibrary.teamLibrary')
  })

  it('renders public skills while the session check is pending', () => {
    mocks.loadingAuth = true
    const item = { id: 1, namespace: 'global', slug: 'public-skill', displayName: 'Public skill', starCount: 0, downloadCount: 0 }
    mocks.searchSkills.mockImplementation((_params, _enabled, _viewer, credentials) => ({
      data: credentials === 'omit' ? { items: [item], total: 1 } : undefined,
      isLoading: false,
    }))
    const html = renderToStaticMarkup(<SkillLibraryPage />)
    expect(mocks.searchSkills).toHaveBeenCalledWith(expect.objectContaining({ library: 'accessible' }), true, undefined, 'omit')
    expect(html).toContain('Public skill')
  })

  it('keeps the public list visible while the signed-in list is loading', () => {
    mocks.authenticated = true
    const item = { id: 1, namespace: 'global', slug: 'public-skill', displayName: 'Public skill', starCount: 0, downloadCount: 0 }
    mocks.searchSkills.mockImplementation((_params, _enabled, _viewer, credentials) => credentials === 'omit'
      ? { data: { items: [item], total: 1 }, isLoading: false }
      : { isPending: true, isLoading: true })
    const html = renderToStaticMarkup(<SkillLibraryPage />)
    expect(mocks.searchSkills).toHaveBeenCalledWith(expect.objectContaining({ library: 'accessible' }), false, undefined, 'omit')
    expect(mocks.searchSkills).toHaveBeenCalledWith(expect.objectContaining({ library: 'accessible' }), true, 'member')
    expect(html).toContain('Public skill')
  })

  it('queries a signed-in viewer’s spaces with the selected search and pagination', () => {
    mocks.search.scope = '__mine__'
    mocks.authenticated = true
    const html = renderToStaticMarkup(<SkillLibraryPage />)
    expect(mocks.searchSkills).toHaveBeenCalledWith(
      expect.objectContaining({ library: 'mine', page: 2, q: 'agent', label: 'tools', sort: 'downloads' }), true, 'member',
    )
    expect(html).toContain('knowledge.namespaceFilter')
  })

  it('queries a specific space within the accessible library', () => {
    mocks.search.scope = 'team-a'
    mocks.authenticated = true
    renderToStaticMarkup(<SkillLibraryPage />)
    expect(mocks.searchSkills).toHaveBeenCalledWith(
      expect.objectContaining({ library: 'accessible', namespace: 'team-a' }), true, 'member',
    )
  })

  it('prompts guests to sign in on a team-scoped link', () => {
    mocks.search.scope = '__mine__'
    const html = renderToStaticMarkup(<SkillLibraryPage />)
    expect(mocks.searchSkills).toHaveBeenCalledWith(expect.objectContaining({ library: 'mine' }), false, undefined, 'omit')
    expect(html).toContain('skillLibrary.teamLoginTitle')
  })
})
