// @vitest-environment jsdom
import { createElement, type ReactNode } from 'react'
import { act, renderHook, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { describe, expect, it, vi } from 'vitest'
import { useDeleteKnowledgeDocument } from './use-knowledge-queries'
import { useDeleteNamespace } from '@/shared/hooks/use-namespace-queries'

vi.mock('@/api/client', () => ({
  knowledgeApi: { deleteDocument: vi.fn().mockResolvedValue(undefined) },
  namespaceApi: { delete: vi.fn().mockResolvedValue(undefined) },
}))

describe('namespace deletion cache refresh', () => {
  function setup() {
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    client.setQueryData(['namespaces', 'my'], [{ slug: 'team', canDelete: false }])
    client.setQueryData(['knowledge', 'bases'], [{ slug: 'base' }])
    const wrapper = ({ children }: { children: ReactNode }) =>
      createElement(QueryClientProvider, { client }, children)
    return { client, wrapper }
  }

  it('refreshes deletion permission after removing a knowledge file', async () => {
    const { client, wrapper } = setup()
    const { result } = renderHook(() => useDeleteKnowledgeDocument(), { wrapper })
    await act(() => result.current.mutateAsync(1))
    expect(client.getQueryState(['namespaces', 'my'])?.isInvalidated).toBe(true)
    expect(client.getQueryState(['knowledge', 'bases'])?.isInvalidated).toBe(true)
    client.clear()
  })

  it('refreshes knowledge lists after deleting their namespace', async () => {
    const { client, wrapper } = setup()
    const { result } = renderHook(() => useDeleteNamespace(), { wrapper })
    await act(() => result.current.mutateAsync({ slug: 'team' }))
    await waitFor(() => expect(client.getQueryState(['knowledge', 'bases'])?.isInvalidated).toBe(true))
    client.clear()
  })
})
