// @vitest-environment jsdom
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { useKnowledgeDocuments } from './use-knowledge-queries'

const context = vi.hoisted(() => ({ user: { userId: 'alice' } as { userId: string } | null,
  listDocuments: vi.fn() }))
vi.mock('@/features/auth/use-auth', () => ({ useAuth: () => ({ user: context.user, isLoading: false }) }))
vi.mock('@/api/client', () => ({ knowledgeApi: { listDocuments: context.listDocuments } }))
afterEach(() => { cleanup(); vi.clearAllMocks(); context.user = { userId: 'alice' } })

function Files() {
  const files = useKnowledgeDocuments('team-a', 'handbook', { page: 0 })
  return <div>{files.data?.items.map((file) => <p key={file.id}>{file.title}</p>)}</div>
}

describe('knowledge permissions across session changes', () => {
  it('hides team results while visitor data is pending after a session expires', async () => {
    context.listDocuments.mockResolvedValueOnce({ items: [{ id: 1, title: 'Private payroll' }], total: 1 })
    const client = new QueryClient({ defaultOptions: { queries: { retry: false, gcTime: 0 } } })
    const view = render(<QueryClientProvider client={client}><Files /></QueryClientProvider>)
    await screen.findByText('Private payroll')
    context.user = null
    context.listDocuments.mockImplementationOnce(() => new Promise(() => {}))
    view.rerender(<QueryClientProvider client={client}><Files /></QueryClientProvider>)
    expect(screen.queryByText('Private payroll')).toBeNull()
    expect(context.listDocuments).toHaveBeenCalledTimes(2)
  })
})
