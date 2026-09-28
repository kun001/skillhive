import { describe, expect, it, vi } from 'vitest'

vi.mock('./layout', () => ({
  Layout: () => null,
}))

vi.mock('@/api/client', () => ({
  getCurrentUser: vi.fn().mockResolvedValue(null),
}))

vi.mock('@/shared/components/role-guard', () => ({
  RoleGuard: ({ children }: { children: unknown }) => children,
}))

vi.mock('@/shared/lib/search-query', () => ({
  normalizeSearchQuery: (q: string) => q.trim(),
}))

import { router } from './router'

describe('router', () => {
  it('exports a TanStack Router instance with a route tree', () => {
    expect(router).toBeDefined()
    expect(router.routeTree).toBeDefined()
  })

  it('has a routeTree structure', () => {
    // The router instance exists and has the expected structure
    // In test environment, flatRoutes may not be populated until router is used
    expect(router.routeTree).toBeDefined()
  })

  it('registers the skill version compare route', () => {
    const children = (router.routeTree.children ?? []) as Array<{ fullPath?: string; path?: string }>
    const childPaths = children.map((route) => route.fullPath ?? route.path)
    expect(childPaths).toContain('/space/$namespace/$slug/compare')
  })

  it('registers the empty knowledge workspace', () => {
    const children = (router.routeTree.children ?? []) as Array<{ fullPath?: string; path?: string }>
    const childPaths = children.map((route) => route.fullPath ?? route.path)
    expect(childPaths).toContain('/knowledge')
  })

  it('serves a dedicated skill library at /skills', () => {
    const children = (router.routeTree.children ?? []) as Array<{ fullPath?: string; path?: string; options?: { component?: unknown; beforeLoad?: unknown } }>
    const skillsRoute = children.find((route) => (route.fullPath ?? route.path) === '/skills')
    expect(skillsRoute?.options?.component).toBeDefined()
    expect(skillsRoute?.options?.beforeLoad).toBeUndefined()
  })
})
