// @vitest-environment jsdom
import { act, cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { KnowledgeDocument } from '@/api/knowledge-types'
import { DocxCanvas } from './docx-preview'
import { KnowledgeFilePreview } from './file-preview'

const mocks = vi.hoisted(() => ({ officeContent: vi.fn(), renderAsync: vi.fn(), contentUrl: vi.fn(() => '/original') }))
vi.mock('@/api/client', () => ({ knowledgeApi: mocks }))
vi.mock('docx-preview', () => ({ renderAsync: mocks.renderAsync }))
vi.mock('@/features/auth/use-auth', () => ({ useAuth: () => ({ user: { userId: 'alice' } }) }))
vi.mock('react-i18next', () => ({ useTranslation: () => ({ t: (key: string) => key }) }))

const file = { id: 42, title: 'Word', currentVersion: 2, fileExtension: 'docx', previewKind: 'OFFICE', canDownload: true } as KnowledgeDocument
function mount(document = file) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false, gcTime: 0 } } })
  return render(<QueryClientProvider client={client}><KnowledgeFilePreview document={document} /></QueryClientProvider>)
}
function page(host: HTMLElement, text: string) {
  const section = document.createElement('section')
  section.textContent = text
  host.append(section)
}
beforeEach(() => {
  vi.spyOn(HTMLElement.prototype, 'offsetWidth', 'get').mockReturnValue(794)
  vi.spyOn(HTMLElement.prototype, 'offsetHeight', 'get').mockReturnValue(2400)
  vi.stubGlobal('ResizeObserver', class { observe() {} disconnect() {} })
  mocks.officeContent.mockResolvedValue(new Blob(['word']))
  mocks.renderAsync.mockImplementation(async (_blob: Blob, host: HTMLElement) => page(host, 'Complete document'))
})
afterEach(() => { cleanup(); vi.restoreAllMocks(); vi.unstubAllGlobals(); vi.resetAllMocks() })

describe('Word full preview', () => {
  it('loads the selected version and offers full preview and original download without calling image conversion', async () => {
    mount()
    expect(await screen.findByText('Complete document')).toBeTruthy()
    expect(mocks.officeContent).toHaveBeenCalledWith(42, 2, expect.any(AbortSignal))
    expect(screen.getByRole('link').getAttribute('href')).toBe('/original')
  })
  it('renders the same browser preview for members without download permission', async () => {
    mount({ ...file, canDownload: false })
    expect(await screen.findByText('Complete document')).toBeTruthy()
    expect(mocks.officeContent).toHaveBeenCalledWith(42, 2, expect.any(AbortSignal))
    expect(screen.queryByRole('link')).toBeNull()
  })
  it.each(['doc', 'ppt'])('offers download only for legacy %s, including stale Office metadata', async (extension) => {
    mount({ ...file, fileExtension: extension })
    expect(screen.getByText('knowledge.preview.unsupportedTitle')).toBeTruthy()
    expect(screen.getByRole('link')).toBeTruthy()
    expect(mocks.officeContent).not.toHaveBeenCalled()
    expect(mocks.renderAsync).not.toHaveBeenCalled()
  })
  it.each(['doc', 'ppt'])('hides the download action for restricted legacy %s', (extension) => {
    mount({ ...file, fileExtension: extension, previewKind: 'NONE', canDownload: false })
    expect(screen.getByText('knowledge.preview.unsupportedTitle')).toBeTruthy()
    expect(screen.queryByRole('link')).toBeNull()
  })
  it('keeps download and retry available if retrieving the original fails', async () => {
    mocks.officeContent.mockRejectedValue(new Error('network'))
    mount()
    expect(await screen.findByRole('alert')).toBeTruthy()
    expect(screen.getByRole('link')).toBeTruthy()
    mocks.officeContent.mockResolvedValue(new Blob(['word']))
    fireEvent.click(screen.getByRole('button', { name: 'knowledge.preview.retry' }))
    expect(await screen.findByText('Complete document')).toBeTruthy()
  })
  it('shows a fallback for a corrupt Word file', async () => {
    mocks.renderAsync.mockRejectedValue(new Error('invalid zip'))
    mount()
    expect(await screen.findByRole('alert')).toBeTruthy()
    expect(screen.getByRole('link')).toBeTruthy()
    expect(screen.queryByText('Complete document')).toBeNull()
  })
  it('does not activate executable hyperlinks embedded in the file', async () => {
    mocks.renderAsync.mockImplementation(async (_blob: Blob, host: HTMLElement) => {
      page(host, 'Complete document')
      const link = document.createElement('a')
      link.setAttribute('href', 'javascript:alert(1)')
      link.textContent = 'Document link'
      host.append(link)
    })
    mount()
    expect((await screen.findByText('Document link')).getAttribute('href')).toBeNull()
  })
  it('discards a slow render when the file changes', async () => {
    let finishOld: () => void = () => undefined
    mocks.renderAsync.mockImplementationOnce(async (_blob: Blob, host: HTMLElement) => {
      await new Promise<void>((resolve) => { finishOld = resolve })
      page(host, 'Old version')
    }).mockImplementationOnce(async (_blob: Blob, host: HTMLElement) => page(host, 'New version'))
    const view = render(<DocxCanvas blob={new Blob(['old'])} title="Word" />)
    await waitFor(() => expect(mocks.renderAsync).toHaveBeenCalledTimes(1))
    view.rerender(<DocxCanvas blob={new Blob(['new'])} title="Word" />)
    expect(await screen.findByText('New version')).toBeTruthy()
    await act(async () => { finishOld() })
    expect(screen.queryByText('Old version')).toBeNull()
    expect(screen.getByText('New version')).toBeTruthy()
  })
})
