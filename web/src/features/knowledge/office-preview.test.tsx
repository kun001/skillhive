// @vitest-environment jsdom
import { cleanup, render, screen } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type { KnowledgeDocument } from '@/api/knowledge-types'
import { KnowledgeOfficeFilePreview } from './office-preview'
import { KnowledgeFilePreview } from './file-preview'

const api = vi.hoisted(() => ({ officePreview: vi.fn(), contentUrl: vi.fn(() => '/original'),
  previewPageUrl: vi.fn((_id: number, version: number, page: number) => `/page/${page}?version=${version}`) }))
vi.mock('@/api/client', () => ({ knowledgeApi: api }))
vi.mock('@/features/auth/use-auth', () => ({ useAuth: () => ({ user: null }) }))
vi.mock('react-i18next', () => ({ useTranslation: () => ({ t: (key: string, args?: { count?: number }) => `${key}${args?.count ?? ''}` }) }))
afterEach(() => { cleanup(); vi.clearAllMocks() })

const document = { id: 42, currentVersion: 2, previewKind: 'OFFICE', canDownload: true } as KnowledgeDocument
const base = { status: 'READY', kind: 'OFFICE', pageCount: 5, pageLimit: 5 }
function mount(file = document) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false, gcTime: 0 } } })
  render(<QueryClientProvider client={client}><KnowledgeOfficeFilePreview document={file} /></QueryClientProvider>)
}

describe('bounded Office previews', () => {
  it('keeps preview pages available but hides original downloads for read-only members without download permission', async () => {
    api.officePreview.mockResolvedValue(base)
    mount({ ...document, canDownload: false })
    expect(await screen.findAllByRole('img')).toHaveLength(5)
    expect(screen.queryByRole('link')).toBeNull()
  })
  it.each(['xls', 'xlsx'])('offers download without requesting a preview for %s', (extension) => {
    const client = new QueryClient({ defaultOptions: { queries: { retry: false, gcTime: 0 } } })
    render(<QueryClientProvider client={client}><KnowledgeFilePreview document={{ ...document,
      title: 'Excel', fileExtension: extension, previewKind: 'NONE' }} /></QueryClientProvider>)
    expect(screen.getByText('knowledge.preview.unsupportedTitle')).toBeTruthy()
    expect(screen.getByRole('link').getAttribute('href')).toBe('/original')
    expect(api.officePreview).not.toHaveBeenCalled()
    expect(screen.queryByRole('table')).toBeNull()
  })

  it('renders version-specific page images with lazy loading and original download', async () => {
    api.officePreview.mockResolvedValue(base)
    mount()
    const images = await screen.findAllByRole('img')
    expect(images).toHaveLength(5)
    expect(images[0].getAttribute('src')).toBe('/page/1?version=2')
    expect(images[4].getAttribute('loading')).toBe('lazy')
    expect(api.officePreview).toHaveBeenCalledWith(42, 2)
    expect(screen.getByRole('link').getAttribute('href')).toBe('/original')
  })

  it('keeps downloading available when rendering fails', async () => {
    api.officePreview.mockResolvedValue({ ...base, status: 'FAILED' })
    mount()
    await screen.findByText('knowledge.preview.officeFailed')
    expect(screen.getByRole('link').getAttribute('href')).toBe('/original')
    expect(screen.queryByRole('img')).toBeNull()
  })
})
