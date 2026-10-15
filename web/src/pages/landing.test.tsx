// @vitest-environment jsdom
import type { ReactNode } from 'react'
import { cleanup, render, screen } from '@testing-library/react'
import { afterEach, beforeAll, describe, expect, it, vi } from 'vitest'
import i18n from '@/i18n/config'
import { hiveLandingEn, hiveLandingZh } from '@/i18n/hive-landing'

const skills = vi.hoisted(() => ({ items: [] as unknown[] }))

vi.mock('@tanstack/react-router', () => ({
  Link: ({ children, className }: { children: ReactNode; className?: string }) => <a className={className}>{children}</a>,
}))

vi.mock('@/shared/hooks/use-skill-queries', () => ({
  useSearchSkills: () => ({ data: { items: skills.items }, isLoading: false, isError: false }),
}))

import { LandingPage } from './landing'

function shapeOf(value: unknown): unknown {
  if (Array.isArray(value)) return value.map(shapeOf)
  if (value && typeof value === 'object') return Object.fromEntries(Object.entries(value).map(([key, item]) => [key, shapeOf(item)]))
  return typeof value
}

function mockReducedMotion(reduced: boolean) {
  vi.stubGlobal('matchMedia', (query: string) => ({ matches: reduced && query.includes('reduce'), addEventListener: vi.fn(), removeEventListener: vi.fn() }))
}

beforeAll(async () => {
  await i18n.changeLanguage('zh')
})

afterEach(() => {
  cleanup()
  skills.items = []
  vi.unstubAllGlobals()
})

describe('LandingPage', () => {
  it('leads with the agent context positioning', () => {
    mockReducedMotion(true)
    render(<LandingPage />)

    expect(screen.getByRole('heading', { level: 1 }).textContent).toBe('让每个 Agent，都懂你的团队。')
    expect(screen.getByText('团队 Agent 的私有上下文中心')).toBeTruthy()
    expect(screen.getAllByText('连接你的 Agent').length).toBeGreaterThan(0)
    expect(screen.getByRole('figure', { name: hiveLandingZh.layers.flowLabel })).toBeTruthy()
  })

  it('renders the whole transcript at once when motion is reduced', () => {
    mockReducedMotion(true)
    const { container } = render(<LandingPage />)
    const scenario = hiveLandingZh.console.scenarios.report

    expect(container.querySelectorAll('.hv-step.is-done')).toHaveLength(scenario.steps.length)
    expect(container.querySelector('.hv-console-result.is-shown')?.textContent).toContain(scenario.result)
  })

  it('omits zero download counts on skill cards', () => {
    mockReducedMotion(true)
    skills.items = [
      { id: 1, displayName: 'weekly-report', slug: 'weekly-report', namespace: '@team', downloadCount: 0, summary: 'Draft reports' },
      { id: 2, displayName: 'incident-triage', slug: 'incident-triage', namespace: '@team', downloadCount: 12, summary: 'Triage incidents' },
    ]
    render(<LandingPage />)

    expect(screen.getByText('12 次下载')).toBeTruthy()
    expect(screen.queryByText('0 次下载')).toBeNull()
  })

  it('keeps the Chinese and English copy in the same shape', () => {
    expect(shapeOf(hiveLandingEn)).toEqual(shapeOf(hiveLandingZh))
  })
})
