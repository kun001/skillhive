// @vitest-environment jsdom
import { act, cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { KnowledgeDocument } from '@/api/knowledge-types'
import { DocxCanvas } from './docx-preview'
import { brokenEmptyCmapFont } from './fixtures/fonts'
import { KnowledgeFilePreview } from './file-preview'
import { fontUnicodeCodepoints } from './repair-docx-embedded-font'

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
    expect(screen.queryByText('knowledge.preview.officeFull')).toBeNull()
    expect(mocks.officeContent).toHaveBeenCalledWith(42, 2, expect.any(AbortSignal))
    expect(screen.getByRole('link').getAttribute('href')).toBe('/original')
  })
  it('shows a no-permission message instead of fetching the file for members without download permission', () => {
    mount({ ...file, canDownload: false })
    expect(screen.getByText('knowledge.preview.noDownloadTitle')).toBeTruthy()
    expect(screen.queryByText('Complete document')).toBeNull()
    expect(mocks.officeContent).not.toHaveBeenCalled()
    expect(mocks.renderAsync).not.toHaveBeenCalled()
    expect(screen.queryByRole('link')).toBeNull()
  })
  it.each([
    ['md', 'MARKDOWN'],
    ['mdx', 'TEXT'],
    ['txt', 'TEXT'],
    ['pdf', 'PDF'],
    ['png', 'IMAGE'],
  ] as const)('does not request %s content without download permission', (extension, previewKind) => {
    const fetchSpy = vi.fn()
    vi.stubGlobal('fetch', fetchSpy)
    mount({ ...file, fileExtension: extension, previewKind, sizeBytes: 10, canDownload: false })
    expect(screen.getByText('knowledge.preview.noDownloadTitle')).toBeTruthy()
    expect(fetchSpy).not.toHaveBeenCalled()
    expect(document.querySelector('img')).toBeNull()
    expect(document.querySelector('iframe')).toBeNull()
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
    expect(screen.getByText('knowledge.preview.noDownloadTitle')).toBeTruthy()
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
  it('repairs an embedded face the renderer emits so empty glyphs are not advertised', async () => {
    const binary = []
    for (let offset = 0; offset < brokenEmptyCmapFont.length; offset += 8192) {
      binary.push(String.fromCharCode(...brokenEmptyCmapFont.subarray(offset, offset + 8192)))
    }
    const font = btoa(binary.join(''))
    mocks.renderAsync.mockImplementation(async (_blob: Blob, host: HTMLElement, styles?: HTMLElement) => {
      const style = document.createElement('style')
      style.textContent = `@font-face { font-family: BrokenFace; src: url(data:font/ttf;base64,${font}); }`
      styles?.append(style)
      const section = document.createElement('section')
      const span = document.createElement('span')
      span.style.fontFamily = 'BrokenFace'
      span.textContent = '甲乙'
      section.append(span)
      host.append(section)
    })
    render(<DocxCanvas blob={new Blob(['synthetic'])} title="Synthetic" />)
    const text = await screen.findByText('甲乙')
    const css = document.querySelector('style')?.textContent ?? ''
    expect(css).toContain('SkillHiveEmbedded0')
    expect(text.style.fontFamily).toContain('Droid Sans Fallback')
    const payload = css.match(/base64,([A-Za-z0-9+/=]+)/)?.[1] ?? ''
    expect(fontUnicodeCodepoints(Uint8Array.from(atob(payload), (char) => char.charCodeAt(0)))).not.toContain(0x4e59)
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
