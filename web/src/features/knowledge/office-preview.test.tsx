// @vitest-environment jsdom
import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type { KnowledgeDocument } from '@/api/knowledge-types'
import { KnowledgeOfficeFilePreview } from './office-preview'

const api = vi.hoisted(() => ({ officePreview: vi.fn(), contentUrl: vi.fn(() => '/original'),
  previewPageUrl: vi.fn((_id: number, version: number, page: number) => `/page/${page}?version=${version}`) }))
vi.mock('@/api/client', () => ({ knowledgeApi: api }))
vi.mock('react-i18next', () => ({ useTranslation: () => ({ t: (key: string, args?: { count?: number }) => `${key}${args?.count ?? ''}` }) }))
afterEach(() => { cleanup(); vi.clearAllMocks() })

const document = { id: 42, currentVersion: 2, previewKind: 'OFFICE' } as KnowledgeDocument
const base = { status: 'READY', kind: 'OFFICE', pageCount: 5, pageLimit: 5, sheets: [], sheetLimit: 3, rowLimit: 100, columnLimit: 20 }
function mount(file = document) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false, gcTime: 0 } } })
  render(<QueryClientProvider client={client}><KnowledgeOfficeFilePreview document={file} /></QueryClientProvider>)
}

describe('bounded Office previews', () => {
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

  it('switches Excel sheets and uses merged cells without executing cell markup', async () => {
    api.officePreview.mockResolvedValue({ ...base, kind: 'SPREADSHEET', pageCount: 0, sheets: [
      { name: '明细', rows: [[{ text: '<script>evil()</script>', bold: true, align: 'center' }, { text: '', bold: false, align: 'left' }]],
        merges: [{ row: 0, column: 0, rowSpan: 1, columnSpan: 2 }], truncated: true },
      { name: '汇总', rows: [[{ text: '汇总金额', bold: false, align: 'left' }]], merges: [], truncated: false },
    ] })
    mount({ ...document, previewKind: 'SPREADSHEET' })
    const first = await screen.findByRole('table', { name: '明细' })
    expect(first.querySelectorAll('td')).toHaveLength(1)
    expect(first.querySelector('td')?.getAttribute('colspan')).toBe('2')
    expect(first.querySelector('script')).toBeNull()
    fireEvent.click(screen.getByRole('button', { name: '汇总' }))
    expect(screen.getByRole('table', { name: '汇总' }).textContent).toContain('汇总金额')
    expect(screen.queryByRole('img')).toBeNull()
  })

  it('keeps downloading available when rendering fails', async () => {
    api.officePreview.mockResolvedValue({ ...base, status: 'FAILED' })
    mount()
    await screen.findByText('knowledge.preview.officeFailed')
    expect(screen.getByRole('link').getAttribute('href')).toBe('/original')
    expect(screen.queryByRole('img')).toBeNull()
  })
})
