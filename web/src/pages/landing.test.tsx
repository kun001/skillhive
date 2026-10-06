import { describe, expect, it, vi } from 'vitest'

vi.mock('@tanstack/react-router', () => ({
  Link: ({ children }: { children: unknown }) => children,
  useNavigate: () => vi.fn(),
}))

vi.mock('react-i18next', async () => {
  const actual = await vi.importActual<typeof import('react-i18next')>('react-i18next')
  return {
    ...actual,
    useTranslation: () => ({
      t: (key: string) => key,
    }),
  }
})

vi.mock('lucide-react', () => ({
  ArrowRight: () => null,
  BookOpen: () => null,
  ChevronDown: () => null,
  CheckCircle2: () => null,
  Clock3: () => null,
  Copy: () => null,
  PackageOpen: () => null,
  Terminal: () => null,
  Shield: () => null,
  Users: () => null,
  GitBranch: () => null,
  FileText: () => null,
  Download: () => null,
  Eye: () => null,
  FolderOpen: () => null,
  History: () => null,
  Layers3: () => null,
  Lock: () => null,
  Monitor: () => null,
  Search: () => null,
  ShieldCheck: () => null,
  Server: () => null,
  Settings: () => null,
}))

vi.mock('@/features/skill/skill-card', () => ({
  SkillCard: () => null,
}))

vi.mock('@/shared/components/skeleton-loader', () => ({
  SkeletonList: () => null,
}))

vi.mock('@/shared/hooks/use-skill-queries', () => ({
  useSearchSkills: () => ({
    data: { items: [] },
    isLoading: false,
  }),
}))

vi.mock('@/shared/hooks/use-in-view', () => ({
  useInView: () => ({ ref: vi.fn(), inView: true }),
}))

vi.mock('@/shared/ui/button', () => ({
  Button: ({ children }: { children: unknown }) => children,
}))

import { renderToStaticMarkup } from 'react-dom/server'
import { LandingPage } from './landing'

describe('LandingPage', () => {
  it('exports a named component function', () => {
    expect(typeof LandingPage).toBe('function')
  })

  it('renders the new homepage sections without hard-coded registry totals', () => {
    const html = renderToStaticMarkup(<LandingPage />)

    expect(html).toContain('hiveLanding.heroTitleFirst')
    expect(html).toContain('hiveLanding.knowledgeTitle')
    expect(html).toContain('hiveLanding.knowledgeBenefits.organize.title')
    expect(html).toContain('hiveLanding.knowledgePreview.title')
    expect(html).toContain('hiveLanding.knowledgeCta')
    expect(html).toContain('hiveLanding.libraryTitle')
    expect(html).not.toContain('Skill 已收录 24 个')
  })

  it('keeps the hero free of the registry search box and skill count', () => {
    const html = renderToStaticMarkup(<LandingPage />)

    expect(html).not.toContain('role="search"')
    expect(html).not.toContain('hiveLanding.skillTotal')
  })
})
