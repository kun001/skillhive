/** @vitest-environment jsdom */
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { tokenApi } from '@/api/client'
import { CreateTokenDialog } from './create-token-dialog'

vi.mock('react-i18next', () => ({ useTranslation: () => ({ t: (key: string) => key, i18n: { language: 'en' } }) }))
vi.mock('@/api/client', () => ({ tokenApi: { createToken: vi.fn() } }))
vi.mock('@/features/namespace/use-my-namespaces', () => ({ useMyNamespaces: () => ({ data: [{ id: 7, slug: 'team', displayName: 'Team', status: 'ACTIVE' }] }) }))
vi.mock('@/shared/hooks/use-namespace-queries', () => ({ useNamespaceDetail: () => ({ data: undefined }) }))
vi.mock('@/features/knowledge/use-knowledge-queries', () => ({ useKnowledgeBases: () => ({ data: [{ id: 17, namespace: 'team', displayName: 'Files' }] }) }))

describe('MCP token creation', () => {
  beforeEach(() => { vi.mocked(tokenApi.createToken).mockReset() })
  afterEach(cleanup)
  function openUploadOnly() {
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    render(<QueryClientProvider client={client}><CreateTokenDialog><button>Open</button></CreateTokenDialog></QueryClientProvider>)
    fireEvent.click(screen.getByRole('button', { name: 'Open' }))
    expect(screen.queryByRole('checkbox', { name: 'mcp.purpose' })).toBeNull()
    expect(screen.queryByRole('combobox', { name: 'createToken.scopeSkill' })).toBeNull()
    expect(screen.queryByRole('checkbox', { name: 'createToken.scopeKnowledge' })).toBeNull()
    fireEvent.change(screen.getByRole('textbox', { name: 'createToken.nameLabel' }), { target: { value: 'mcp-upload' } })
    fireEvent.click(screen.getByRole('checkbox', { name: 'mcp.skillRead' }))
    fireEvent.click(screen.getByRole('checkbox', { name: 'mcp.knowledgeUpload' }))
  }

  it('requires an explicit resource ceiling before contacting the server', () => {
    openUploadOnly()
    fireEvent.click(screen.getByRole('button', { name: 'createToken.create' }))
    expect(screen.getByText('mcp.resourcesRequired')).toBeTruthy()
    expect(tokenApi.createToken).not.toHaveBeenCalled()
  })

  it('issues independent file-update and base-management grants with write expiry', async () => {
    vi.mocked(tokenApi.createToken).mockResolvedValue({ id: 10, name: 'manage', tokenPrefix: 'unit', token: 'fixture', createdAt: new Date().toISOString(), scopes: ['mcp:access', 'knowledge:update', 'knowledge:manage'] })
    openUploadOnly()
    fireEvent.click(screen.getByRole('checkbox', { name: 'mcp.knowledgeUpload' }))
    fireEvent.click(screen.getByRole('checkbox', { name: 'mcp.knowledgeUpdate' }))
    fireEvent.click(screen.getByRole('checkbox', { name: 'mcp.knowledgeManage' }))
    fireEvent.click(screen.getByRole('checkbox', { name: 'Team @team' }))
    expect(screen.getByText('mcp.manageBaseHint')).toBeTruthy()
    fireEvent.click(screen.getByRole('button', { name: 'createToken.create' }))
    await waitFor(() => expect(tokenApi.createToken).toHaveBeenCalledTimes(1))
    expect(vi.mocked(tokenApi.createToken).mock.calls[0][0]).toMatchObject({
      scopes: ['knowledge:update', 'knowledge:manage'], namespaceIds: [7], knowledgeBaseIds: undefined,
    })
    expect(new Date(vi.mocked(tokenApi.createToken).mock.calls[0][0].expiresAt!).getTime() - Date.now())
      .toBeLessThanOrEqual(7 * 24 * 60 * 60 * 1000)
  })

  it('issues only upload permission with selected namespace/base and a seven-day write expiry', async () => {
    vi.mocked(tokenApi.createToken).mockResolvedValue({ id: 9, name: 'mcp-upload', tokenPrefix: 'unit', token: 'not-a-real-token-unit-fixture', createdAt: new Date().toISOString(), scopes: ['mcp:access', 'knowledge:upload'] })
    openUploadOnly()
    fireEvent.click(screen.getByRole('checkbox', { name: 'Team @team' }))
    fireEvent.click(screen.getByRole('checkbox', { name: 'mcp.limitBases' }))
    fireEvent.click(screen.getByRole('checkbox', { name: 'Files' }))
    expect(screen.getByText('mcp.writeExpiryHint')).toBeTruthy()
    fireEvent.click(screen.getByRole('button', { name: 'createToken.create' }))
    await waitFor(() => expect(tokenApi.createToken).toHaveBeenCalledTimes(1))
    const request = vi.mocked(tokenApi.createToken).mock.calls[0][0]
    expect(request).toMatchObject({ mcp: true, scopes: ['knowledge:upload'], namespaceIds: [7], knowledgeBaseIds: [17] })
    expect(new Date(request.expiresAt!).getTime() - Date.now()).toBeGreaterThan(6 * 24 * 60 * 60 * 1000)
    expect(new Date(request.expiresAt!).getTime() - Date.now()).toBeLessThanOrEqual(7 * 24 * 60 * 60 * 1000)
  })
})
