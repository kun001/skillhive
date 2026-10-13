// @vitest-environment jsdom
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import '@/i18n/config'
import { KnowledgeUploadDialog } from './upload-dialog'

const api = vi.hoisted(() => ({ createFolder: vi.fn(), listFolders: vi.fn(), uploadDocument: vi.fn() }))
vi.mock('@/api/client', () => ({ knowledgeApi: api }))
vi.mock('@/shared/lib/toast', () => ({ toast: { success: vi.fn(), error: vi.fn() } }))
afterEach(() => { cleanup(); vi.clearAllMocks() })

function directoryFile(path: string) {
  const file = new File(['export const Example = () => <div>Hello</div>'], path.split('/').at(-1)!)
  Object.defineProperty(file, 'webkitRelativePath', { value: path })
  return file
}

describe('folder upload confirmation', () => {
  it('does not create or upload before confirmation and retains nested destination ids', async () => {
    api.listFolders.mockResolvedValue([])
    api.createFolder.mockImplementation(async (_namespace, _base, { name, parentId }) => ({ id: name === 'root' ? 10 : 11, name, parentId }))
    api.uploadDocument.mockResolvedValue({ id: 100 })
    const client = new QueryClient()
    const view = render(<QueryClientProvider client={client}>
      <KnowledgeUploadDialog open onOpenChange={vi.fn()} namespace="team" base="files" tree={[]} folderId={9} />
    </QueryClientProvider>)
    const input = view.container.ownerDocument.querySelector('input[webkitdirectory]')!
    fireEvent.change(input, { target: { files: [directoryFile('root/top.txt'), directoryFile('root/nested/component.mdx')] } })
    fireEvent.click(screen.getByRole('button', { name: /查看目录并确认|Review directory structure/ }))
    expect(api.createFolder).not.toHaveBeenCalled()
    expect(api.uploadDocument).not.toHaveBeenCalled()
    expect(screen.getByRole('list', { name: /待上传目录结构|Directory upload preview/ })).toBeTruthy()
    fireEvent.click(screen.getByRole('button', { name: /返回检查|Back to review/ }))
    expect(api.createFolder).not.toHaveBeenCalled()
    fireEvent.click(screen.getByRole('button', { name: /查看目录并确认|Review directory structure/ }))
    fireEvent.click(screen.getByRole('button', { name: /确认并开始上传|Confirm and upload/ }))
    await waitFor(() => expect(api.uploadDocument).toHaveBeenCalledTimes(2))
    expect(api.createFolder.mock.calls.map((call) => call[2])).toEqual([{ name: 'root', parentId: 9 }, { name: 'nested', parentId: 10 }])
    expect(api.uploadDocument.mock.calls.map((call) => call[2].folderId)).toEqual([10, 11])
  })

  it('retries only failed files and retains created folder ids across parent navigation updates', async () => {
    api.listFolders.mockResolvedValue([])
    api.createFolder.mockResolvedValue({ id: 10, name: 'root', parentId: 9 })
    api.uploadDocument.mockReset().mockResolvedValueOnce({ id: 100 }).mockRejectedValueOnce(new Error('offline')).mockResolvedValueOnce({ id: 101 })
    const client = new QueryClient()
    const props = { open: true, onOpenChange: vi.fn(), namespace: 'team', base: 'files', tree: [],
      initialFiles: [directoryFile('root/a.mdx'), directoryFile('root/b.mdx')] }
    const view = render(<QueryClientProvider client={client}><KnowledgeUploadDialog {...props} folderId={9} /></QueryClientProvider>)
    fireEvent.click(screen.getByRole('button', { name: /查看目录并确认|Review directory structure/ }))
    fireEvent.click(screen.getByRole('button', { name: /确认并开始上传|Confirm and upload/ }))
    await screen.findByRole('button', { name: /重试|Retry/ })
    view.rerender(<QueryClientProvider client={client}><KnowledgeUploadDialog {...props} folderId={20} /></QueryClientProvider>)
    fireEvent.click(screen.getByRole('button', { name: /重试|Retry/ }))
    await waitFor(() => expect(api.uploadDocument).toHaveBeenCalledTimes(3))
    expect(api.createFolder).toHaveBeenCalledTimes(1)
    expect(api.uploadDocument.mock.calls.map((call) => [call[2].file.name, call[2].folderId]))
      .toEqual([['a.mdx', 10], ['b.mdx', 10], ['b.mdx', 10]])
  })
})
