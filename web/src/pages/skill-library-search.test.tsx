/** @vitest-environment jsdom */
import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

const mocks = vi.hoisted(() => ({
  navigate: vi.fn(),
  search: { q: '交互式网页', label: undefined as string | undefined, sort: 'all', page: 1, view: 'list', library: 'public' },
}))
vi.mock('@tanstack/react-router', () => ({
  useNavigate: () => mocks.navigate,
  useSearch: () => mocks.search,
  useRouterState: () => '/skills?library=public',
  Link: ({ children }: { children: React.ReactNode }) => <a>{children}</a>,
}))
vi.mock('react-i18next', () => ({ useTranslation: () => ({ t: (key: string) => key }) }))
vi.mock('@/features/auth/use-auth', () => ({ useAuth: () => ({ isAuthenticated: false, hasRole: () => false }) }))
vi.mock('@/features/namespace/use-my-namespaces', () => ({ useMyNamespaces: () => ({ data: [] }) }))
vi.mock('@/shared/hooks/use-label-queries', () => ({ useVisibleLabels: () => ({ data: [] }) }))
vi.mock('@/shared/hooks/use-skill-queries', () => ({ useSearchSkills: () => ({ data: { items: [], total: 0 }, isLoading: false }) }))

import { SkillLibraryPage } from './skill-library'

describe('SkillLibraryPage search box', () => {
  beforeEach(() => vi.clearAllMocks())
  afterEach(cleanup)

  it('restores the full list as soon as the search box is emptied', () => {
    render(<SkillLibraryPage />)
    const input = screen.getByRole('searchbox')
    fireEvent.change(input, { target: { value: '交互' } })
    expect(mocks.navigate).not.toHaveBeenCalled()
    fireEvent.change(input, { target: { value: '' } })
    expect(mocks.navigate).toHaveBeenCalledTimes(1)
    expect(mocks.navigate).toHaveBeenCalledWith({
      to: '/skills',
      search: expect.objectContaining({ q: '', page: 0, library: 'public' }),
    })
  })

  it('still searches on explicit submit', () => {
    render(<SkillLibraryPage />)
    const input = screen.getByRole('searchbox')
    fireEvent.change(input, { target: { value: '机器学习' } })
    fireEvent.submit(input.closest('form')!)
    expect(mocks.navigate).toHaveBeenCalledWith({
      to: '/skills',
      search: expect.objectContaining({ q: '机器学习', page: 0 }),
    })
  })
})