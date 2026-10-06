import type { ReactNode } from 'react'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const buttonRecords: Array<{ label: string }> = []
const routeSearch: { tab?: 'skills' | 'knowledge' } = {}

vi.mock('@tanstack/react-router', () => ({
  useNavigate: () => vi.fn(),
  useParams: () => ({ namespace: 'global' }),
  useSearch: () => routeSearch,
  Link: ({ children }: { children?: ReactNode }) => <a>{children}</a>,
}))

vi.mock('react-i18next', async () => {
  const actual = await vi.importActual<typeof import('react-i18next')>('react-i18next')
  return {
    ...actual,
    useTranslation: () => ({
      t: (key: string) => key,
      i18n: { language: 'en' },
    }),
  }
})

vi.mock('@/features/namespace/namespace-header', () => ({
  NamespaceHeader: () => null,
}))

vi.mock('@/features/skill/skill-card', () => ({
  SkillCard: () => null,
}))

vi.mock('@/shared/ui/button', () => ({
  Button: ({ children }: { children?: ReactNode }) => {
    const label = Array.isArray(children) ? children.join('') : String(children ?? '')
    buttonRecords.push({ label })
    return <button>{children}</button>
  },
}))

vi.mock('@/shared/components/skeleton-loader', () => ({
  SkeletonList: () => null,
}))

vi.mock('@/shared/components/empty-state', () => ({
  EmptyState: ({ title }: { title: string }) => <div>{title}</div>,
}))

const useAuthMock = vi.fn()
vi.mock('@/features/auth/use-auth', () => ({
  useAuth: () => useAuthMock(),
}))

const useNamespaceDetailMock = vi.fn()
const useMyNamespacesMock = vi.fn()
vi.mock('@/shared/hooks/use-namespace-queries', () => ({
  useNamespaceDetail: () => useNamespaceDetailMock(),
  useMyNamespaces: () => useMyNamespacesMock(),
}))

const useKnowledgeBasesMock = vi.fn()
vi.mock('@/features/knowledge/use-knowledge-queries', () => ({
  useKnowledgeBases: () => useKnowledgeBasesMock(),
  useKnowledgeDocumentSearch: () => ({ data: undefined, isLoading: false, isError: false }),
}))

vi.mock('@/shared/hooks/use-skill-queries', () => ({
  useSearchSkills: () => ({
    data: {
      items: [
        {
          id: 1,
          displayName: 'Demo Skill',
          summary: 'summary',
          namespace: 'global',
          slug: 'demo',
          downloadCount: 1,
          starCount: 1,
          ratingCount: 0,
          updatedAt: '2026-03-20T00:00:00Z',
          canSubmitPromotion: false,
          publishedVersion: { id: 10, version: '1.0.0', status: 'PUBLISHED' },
        },
      ],
      total: 1,
      page: 0,
      size: 20,
    },
    isLoading: false,
  }),
}))

import { renderToStaticMarkup } from 'react-dom/server'
import { NamespacePage } from './namespace'

const handbook = {
  id: 3,
  namespace: 'global',
  namespaceDisplayName: 'Global',
  slug: 'handbook',
  displayName: 'Team Handbook',
  description: 'Policies',
  status: 'ACTIVE',
  documentCount: 4,
  updatedAt: '2026-10-01T00:00:00Z',
  canManage: false,
  canContribute: true,
}
const otherSpaceBase = { ...handbook, id: 4, namespace: 'other', namespaceDisplayName: 'Other', displayName: 'Other Space Docs' }

describe('NamespacePage', () => {
  beforeEach(() => {
    buttonRecords.length = 0
    delete routeSearch.tab
    useNamespaceDetailMock.mockReturnValue({
      data: { id: 1, slug: 'global', displayName: 'Global', type: 'GLOBAL', status: 'ACTIVE' },
      isLoading: false,
    })
    useAuthMock.mockReturnValue({ user: { userId: 'alice' }, hasRole: () => false })
    useMyNamespacesMock.mockReturnValue({ data: [{ slug: 'global' }] })
    useKnowledgeBasesMock.mockReturnValue({ data: [handbook, otherSpaceBase], isLoading: false })
  })

  it('exports a named component function', () => {
    expect(typeof NamespacePage).toBe('function')
  })

  it('renders the not-found state when namespace data is missing', () => {
    useNamespaceDetailMock.mockReturnValue({
      data: null,
      isLoading: false,
    })

    const html = renderToStaticMarkup(<NamespacePage />)
    expect(html).toContain('namespace.notFound')
  })

  it('does not render namespace distribution controls when skills are available', () => {
    const html = renderToStaticMarkup(<NamespacePage />)

    expect(buttonRecords).toHaveLength(0)
    expect(html).not.toContain('type="checkbox"')
  })

  it('offers skills and knowledge tabs with the skills tab open by default', () => {
    const html = renderToStaticMarkup(<NamespacePage />)

    expect(html).toContain('namespace.tabSkills')
    expect(html).toContain('namespace.tabKnowledge')
    expect(html).not.toContain('Team Handbook')
  })

  it('lists only this space’s knowledge bases to members', () => {
    routeSearch.tab = 'knowledge'
    const html = renderToStaticMarkup(<NamespacePage />)

    expect(html).toContain('Team Handbook')
    expect(html).not.toContain('Other Space Docs')
    expect(html).toContain('namespace.knowledgeSearch')
  })

  it('tells non-members that knowledge is for space members', () => {
    routeSearch.tab = 'knowledge'
    useMyNamespacesMock.mockReturnValue({ data: [] })
    useKnowledgeBasesMock.mockReturnValue({ data: [otherSpaceBase], isLoading: false })
    const html = renderToStaticMarkup(<NamespacePage />)

    expect(html).toContain('namespace.knowledgeMembersOnly')
    expect(html).not.toContain('namespace.knowledgeSearch')
  })
})
