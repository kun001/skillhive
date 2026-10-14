/** @vitest-environment jsdom */
import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type { ApiToken } from '@/api/types'
import { TokenPermissionsDialog } from './token-permissions-dialog'

vi.mock('react-i18next', () => ({ useTranslation: () => ({ t: (key: string) => key }) }))
vi.mock('@/features/namespace/use-my-namespaces', () => ({ useMyNamespaces: () => ({ data: [{ id: 1, slug: 'team', displayName: 'Team' }] }) }))
vi.mock('@/shared/hooks/use-namespace-queries', () => ({ useNamespaceDetail: () => ({ data: undefined }) }))
vi.mock('@/features/knowledge/use-knowledge-queries', () => ({ useKnowledgeBases: () => ({ data: [{ id: 5, namespace: 'team', slug: 'files', displayName: 'Files' }] }) }))

const token: ApiToken = { id: 1, name: 'Test token', tokenPrefix: 'unit', createdAt: '2026-10-14T00:00:00Z' }
function open(value: ApiToken) {
  render(<TokenPermissionsDialog token={value} />)
  fireEvent.click(screen.getByRole('button', { name: 'mcp.viewPermissions' }))
}
describe('token permission inspection', () => {
  afterEach(cleanup)

  it('keeps legacy API publish/delete scopes distinct from MCP upload/archive grants', () => {
    open({ ...token, scopes: ['skill:read', 'skill:publish', 'skill:delete', 'knowledge:read'] })
    expect((screen.getByRole('checkbox', { name: 'mcp.skillRead' }) as HTMLInputElement).checked).toBe(true)
    expect((screen.getByRole('checkbox', { name: 'mcp.knowledgeRead' }) as HTMLInputElement).checked).toBe(true)
    expect((screen.getByRole('checkbox', { name: 'mcp.skillUpload' }) as HTMLInputElement).checked).toBe(false)
    expect((screen.getByRole('checkbox', { name: 'mcp.skillDelete' }) as HTMLInputElement).checked).toBe(false)
    expect(screen.getByText('skill:publish')).toBeTruthy()
    expect(screen.getByText('skill:delete')).toBeTruthy()
    expect(screen.getByText('mcp.legacyResources')).toBeTruthy()
  })

  it('shows exact grants including IDs whose resource names are no longer available', () => {
    open({ ...token, scopes: ['mcp:access', 'knowledge:read', 'knowledge:delete'], resourceGrants: { namespaceIds: [1, 9], knowledgeBaseIds: [5, 12] } })
    expect((screen.getByRole('checkbox', { name: 'mcp.knowledgeDelete' }) as HTMLInputElement).checked).toBe(true)
    expect((screen.getByRole('checkbox', { name: 'mcp.knowledgeUpload' }) as HTMLInputElement).checked).toBe(false)
    for (const name of ['Team @team', '#9', 'Files @team/files', '#12']) {
      const checkbox = screen.getByRole('checkbox', { name }) as HTMLInputElement
      expect(checkbox.checked).toBe(true)
      expect(checkbox.disabled).toBe(true)
    }
  })

  it.each([
    { knowledgeBaseIds: undefined, expected: 'mcp.allGrantedBases' },
    { knowledgeBaseIds: [], expected: 'mcp.noKnowledgeBases' },
  ])('distinguishes an unrestricted base set from an empty grant ($expected)', ({ knowledgeBaseIds, expected }) => {
    open({ ...token, scopes: ['mcp:access', 'knowledge:read'], resourceGrants: { namespaceIds: [1], knowledgeBaseIds } })
    expect(screen.getByText(expected)).toBeTruthy()
  })
})
