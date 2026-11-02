// @vitest-environment jsdom
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import i18n from '@/i18n/config'
import type { ArchivedKnowledgeDocument } from '@/api/knowledge-types'
import { KnowledgeArchivedDrawer } from './archived-drawer'

const api = vi.hoisted(() => ({
  listArchivedDocuments: vi.fn(),
  purgeDocument: vi.fn(),
  purgeArchivedDocuments: vi.fn(),
  getMyStorageQuota: vi.fn(),
}))
const toast = vi.hoisted(() => ({ success: vi.fn(), error: vi.fn() }))
vi.mock('@/api/client', () => ({ knowledgeApi: api }))
vi.mock('@/shared/lib/toast', () => ({ toast }))
vi.mock('@/features/auth/use-auth', () => ({ useAuth: () => ({ user: { userId: 'alice' }, isLoading: false }) }))

function archivedFile(id: number, patch: Partial<ArchivedKnowledgeDocument> = {}): ArchivedKnowledgeDocument {
  return {
    id,
    knowledgeBaseId: 1,
    folderId: null,
    title: `report-${id}.pdf`,
    owner: { id: 'alice', displayName: 'Alice' },
    updatedAt: '2026-11-01T08:00:00Z',
    versionCount: 2,
    storedBytes: 2 * 1024 * 1024,
    canPurge: true,
    ...patch,
  }
}

function page(items: ArchivedKnowledgeDocument[], total = items.length, pageNumber = 0) {
  return { items, total, page: pageNumber, size: 20 }
}

function renderDrawer() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false, gcTime: 0 } } })
  return render(
    <QueryClientProvider client={client}>
      <KnowledgeArchivedDrawer open onOpenChange={vi.fn()} namespace="team" base="handbook" />
    </QueryClientProvider>,
  )
}

beforeEach(async () => {
  await i18n.changeLanguage('zh')
  api.getMyStorageQuota.mockResolvedValue({ usedBytes: 50 * 1024 * 1024, skillBytes: 0, knowledgeBytes: 50 * 1024 * 1024, quotaBytes: 100 * 1024 * 1024, unlimited: false, exempt: false })
})
afterEach(() => { cleanup(); vi.clearAllMocks() })

describe('archived knowledge files drawer', () => {
  it('lists archived files with versions, size, owner and the storage quota', async () => {
    api.listArchivedDocuments.mockResolvedValue(page([archivedFile(1)]))
    renderDrawer()
    expect(await screen.findByText('report-1.pdf')).toBeTruthy()
    expect(screen.getByText(/2 个版本 · 2\.0 MB · Alice · 更新于/)).toBeTruthy()
    expect(await screen.findByText('我的存储：已用 50 MB / 100 MB')).toBeTruthy()
    expect(screen.getByRole('progressbar').getAttribute('aria-valuenow')).toBe('50')
    expect(api.listArchivedDocuments).toHaveBeenCalledWith('team', 'handbook', { page: 0, size: 20 })
  })

  it('shows an empty state without purge actions', async () => {
    api.listArchivedDocuments.mockResolvedValue(page([]))
    renderDrawer()
    expect(await screen.findByText('没有已归档的文件')).toBeTruthy()
    expect(screen.queryByRole('button', { name: /永久删除|清理全部/ })).toBeNull()
  })

  it('only offers deletion for files the server marks purgeable', async () => {
    api.listArchivedDocuments.mockResolvedValue(page([archivedFile(1), archivedFile(2, { canPurge: false, title: 'locked.docx' })]))
    renderDrawer()
    await screen.findByText('locked.docx')
    expect(screen.getByRole('button', { name: '永久删除 report-1.pdf' })).toBeTruthy()
    expect(screen.queryByRole('button', { name: '永久删除 locked.docx' })).toBeNull()
  })

  it('asks for confirmation, purges, then refreshes and reports the freed space', async () => {
    api.listArchivedDocuments.mockResolvedValueOnce(page([archivedFile(1)])).mockResolvedValue(page([]))
    api.purgeDocument.mockResolvedValue({ purgedDocuments: 1, freedBytes: 3 * 1024 * 1024, remainingArchived: null })
    renderDrawer()
    fireEvent.click(await screen.findByRole('button', { name: '永久删除 report-1.pdf' }))
    const dialog = screen.getByRole('dialog', { name: '永久删除「report-1.pdf」？' })
    expect(within(dialog).getByText(/永久删除，无法恢复，释放占用空间（2\.0 MB）/)).toBeTruthy()
    expect(api.purgeDocument).not.toHaveBeenCalled()
    fireEvent.click(within(dialog).getByRole('button', { name: '永久删除' }))
    await waitFor(() => expect(api.purgeDocument).toHaveBeenCalledWith(1))
    await waitFor(() => expect(toast.success).toHaveBeenCalledWith('已永久删除，释放 3.0 MB'))
    expect(await screen.findByText('没有已归档的文件')).toBeTruthy()
    expect(api.listArchivedDocuments).toHaveBeenCalledTimes(2)
  })

  it('cancelling the confirmation does not purge', async () => {
    api.listArchivedDocuments.mockResolvedValue(page([archivedFile(1)]))
    renderDrawer()
    fireEvent.click(await screen.findByRole('button', { name: '永久删除 report-1.pdf' }))
    fireEvent.click(within(screen.getByRole('dialog', { name: '永久删除「report-1.pdf」？' })).getByRole('button', { name: '取消' }))
    expect(api.purgeDocument).not.toHaveBeenCalled()
  })

  it('shows the server error message and refreshes the list when a purge fails', async () => {
    api.listArchivedDocuments.mockResolvedValue(page([archivedFile(1)]))
    api.purgeDocument.mockRejectedValue(Object.assign(new Error('只能永久删除已归档（已删除）的文件，请先删除该文件'), { status: 409 }))
    renderDrawer()
    fireEvent.click(await screen.findByRole('button', { name: '永久删除 report-1.pdf' }))
    fireEvent.click(within(screen.getByRole('dialog', { name: '永久删除「report-1.pdf」？' })).getByRole('button', { name: '永久删除' }))
    await waitFor(() => expect(toast.error).toHaveBeenCalledWith('只能永久删除已归档（已删除）的文件，请先删除该文件'))
    await waitFor(() => expect(api.listArchivedDocuments).toHaveBeenCalledTimes(2))
  })

  it('shows a load error from the server', async () => {
    api.listArchivedDocuments.mockRejectedValue(new Error('知识库不存在'))
    renderDrawer()
    expect((await screen.findByRole('alert')).textContent).toContain('知识库不存在')
  })

  it('pages through archived files', async () => {
    api.listArchivedDocuments.mockImplementation(async (_ns, _base, { page: pageNumber }) =>
      page([archivedFile(pageNumber === 0 ? 1 : 21)], 25, pageNumber))
    renderDrawer()
    await screen.findByText('report-1.pdf')
    fireEvent.click(screen.getByRole('button', { name: i18n.t('pagination.goToPage', { page: 2 }) }))
    expect(await screen.findByText('report-21.pdf')).toBeTruthy()
    expect(api.listArchivedDocuments).toHaveBeenLastCalledWith('team', 'handbook', { page: 1, size: 20 })
  })

  it('confirms a batch purge with the count and offers to continue while files remain', async () => {
    const many = Array.from({ length: 20 }, (_, index) => archivedFile(index + 1))
    api.listArchivedDocuments
      .mockResolvedValueOnce(page(many, 130))
      .mockResolvedValueOnce(page(many.slice(0, 20), 30))
      .mockResolvedValue(page([]))
    api.purgeArchivedDocuments
      .mockResolvedValueOnce({ purgedDocuments: 100, freedBytes: 200 * 1024 * 1024, remainingArchived: 30 })
      .mockResolvedValueOnce({ purgedDocuments: 30, freedBytes: 60 * 1024 * 1024, remainingArchived: 0 })
    renderDrawer()
    fireEvent.click(await screen.findByRole('button', { name: '清理全部（100 个）' }))
    const dialog = screen.getByRole('dialog', { name: '永久删除 100 个已归档文件？' })
    expect(within(dialog).getByText(/无法恢复，释放占用空间/)).toBeTruthy()
    expect(within(dialog).getByText('共 130 个，单次最多清理 100 个，完成后可继续清理。')).toBeTruthy()
    fireEvent.click(within(dialog).getByRole('button', { name: '永久删除' }))
    await waitFor(() => expect(toast.success).toHaveBeenCalledWith('已永久删除 100 个文件，释放 200 MB'))
    expect(await screen.findByText('还有 30 个已归档文件未清理。')).toBeTruthy()
    fireEvent.click(screen.getByRole('button', { name: '继续清理' }))
    fireEvent.click(within(screen.getByRole('dialog', { name: '永久删除 30 个已归档文件？' })).getByRole('button', { name: '永久删除' }))
    await waitFor(() => expect(api.purgeArchivedDocuments).toHaveBeenCalledTimes(2))
    expect(api.purgeArchivedDocuments).toHaveBeenCalledWith('team', 'handbook')
    expect(await screen.findByText('没有已归档的文件')).toBeTruthy()
    expect(screen.queryByText(/还有 \d+ 个已归档文件/)).toBeNull()
  })

  it('shows unlimited storage for exempt users', async () => {
    api.listArchivedDocuments.mockResolvedValue(page([]))
    api.getMyStorageQuota.mockResolvedValue({ usedBytes: 1024, skillBytes: 0, knowledgeBytes: 1024, quotaBytes: null, unlimited: true, exempt: true })
    renderDrawer()
    expect(await screen.findByText('我的存储：已用 1.0 KB（不限额）')).toBeTruthy()
  })
})
