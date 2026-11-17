// @vitest-environment jsdom
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, render, screen, waitFor } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type { KnowledgeDocument } from '@/api/knowledge-types'
import { MAX_SYNTAX_HIGHLIGHT_BYTES } from '@/features/skill/file-type-utils'
import { KnowledgeFilePreview } from './file-preview'

vi.mock('@/api/client', () => ({ knowledgeApi: { contentUrl: () => '/sql' } }))
vi.mock('@/features/auth/use-auth', () => ({ useAuth: () => ({ user: { userId: 'viewer' } }) }))
vi.mock('react-i18next', () => ({ useTranslation: () => ({ t: (key: string) => key }) }))

const sql = "SELECT '<img src=x>' AS 字段;\n-- 中文注释"
const file = {
  id: 24, title: 'SQL file', fileExtension: 'SQL', previewKind: 'TEXT', currentVersion: 1,
  sizeBytes: 100, canDownload: true,
} as KnowledgeDocument

function mount(overrides?: Partial<KnowledgeDocument>) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false, gcTime: 0 } } })
  return render(
    <QueryClientProvider client={client}>
      <KnowledgeFilePreview document={{ ...file, ...overrides }} />
    </QueryClientProvider>,
  )
}

afterEach(() => {
  cleanup()
  vi.unstubAllGlobals()
})

describe('knowledge SQL preview', () => {
  it('highlights SQL and preserves markup and Chinese as source text', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(sql)))
    const { container } = mount()
    await waitFor(() => expect(container.querySelector('code')?.textContent).toBe(sql))
    expect(container.querySelector('.hljs-keyword')?.textContent).toBe('SELECT')
    expect(container.querySelector('img[src="x"]')).toBeNull()
  })

  it('renders larger SQL as plain text for responsiveness', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(sql)))
    const { container } = mount({ sizeBytes: MAX_SYNTAX_HIGHLIGHT_BYTES + 1 })
    await waitFor(() => expect(container.querySelector('pre')?.textContent).toBe(sql))
    expect(container.querySelector('code')).toBeNull()
  })

  it('does not retrieve file contents when the viewer lacks download permission', () => {
    const fetchFile = vi.fn()
    vi.stubGlobal('fetch', fetchFile)
    mount({ canDownload: false })
    expect(screen.getByText('knowledge.preview.noDownloadTitle')).toBeTruthy()
    expect(fetchFile).not.toHaveBeenCalled()
  })
})
