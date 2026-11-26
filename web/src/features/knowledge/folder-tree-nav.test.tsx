// @vitest-environment jsdom
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { KnowledgeFolder } from '@/api/knowledge-types'
import { PORTAL_ROOT_ID } from '@/shared/lib/portal-container'
import { KnowledgeFolderSidebar } from './folder-sidebar'
import { buildFolderTree } from './folder-tree'
import { KnowledgeFolderTreeNav } from './folder-tree-nav'

vi.mock('react-i18next', () => ({ useTranslation: () => ({ t: (key: string) => key }) }))

function folder(id: number, name: string, parentId?: number): KnowledgeFolder {
  return { id, name, parentId, documentCount: 0, canManage: true }
}

const nestedName = 'A nested folder with a long name that may be truncated in the sidebar'
const folders = [folder(1, 'Parent'), folder(2, 'Child', 1), folder(3, nestedName, 2)]

function mount() {
  return render(
    <div>
      <aside data-testid="sidebar" style={{ position: 'sticky', overflow: 'auto', height: 80, width: 160 }}>
        <KnowledgeFolderTreeNav
          tree={buildFolderTree(folders)}
          ancestry={folders}
          selectedFolderId={3}
          totalCount={3}
          canContribute
          onSelect={() => undefined}
          onCreate={() => undefined}
          onEdit={() => undefined}
          onDelete={() => undefined}
        />
      </aside>
      <section data-testid="files">files</section>
    </div>,
  )
}

beforeEach(() => {
  const portal = document.createElement('div')
  portal.id = PORTAL_ROOT_ID
  document.body.append(portal)
})
afterEach(() => {
  cleanup()
  document.getElementById(PORTAL_ROOT_ID)?.remove()
})

it('exposes the full name of a nested folder on hover', () => {
  mount()
  expect(screen.getByRole('button', { name: nestedName }).getAttribute('title')).toBe(nestedName)
})

it('resizes the sidebar with the keyboard within the announced limits', () => {
  render(<KnowledgeFolderSidebar>Folders</KnowledgeFolderSidebar>)
  const handle = screen.getByRole('separator', { name: 'knowledge.tree.resizeSidebar' })
  const width = () => Number(handle.getAttribute('aria-valuenow'))
  const original = width()
  fireEvent.keyDown(handle, { key: 'ArrowRight' })
  expect(width()).toBeGreaterThan(original)
  fireEvent.keyDown(handle, { key: 'ArrowLeft' })
  expect(width()).toBe(original)
  fireEvent.keyDown(handle, { key: 'Home' })
  fireEvent.keyDown(handle, { key: 'ArrowLeft' })
  expect(width()).toBe(Number(handle.getAttribute('aria-valuemin')))
  fireEvent.keyDown(handle, { key: 'End' })
  fireEvent.keyDown(handle, { key: 'ArrowRight' })
  expect(width()).toBe(Number(handle.getAttribute('aria-valuemax')))
})

it('opens the selected visitor branch and keeps locked folders as read-only entries', () => {
  const visible = [
    { id: 1, name: 'Public folders', entryOnly: false },
    { id: 2, name: 'Shared child', parentId: 1, entryOnly: false },
    { id: 3, name: 'Locked folder', entryOnly: true },
  ]
  const onSelect = vi.fn()
  render(
    <KnowledgeFolderTreeNav
      tree={buildFolderTree(visible)}
      ancestry={[visible[0]]}
      selectedFolderId={1}
      expandSelected
      onSelect={onSelect}
    />,
  )

  expect(screen.getByRole('button', { name: 'Public folders' }).getAttribute('aria-current')).toBe('page')
  expect(screen.getByRole('button', { name: 'Shared child' })).toBeTruthy()
  fireEvent.click(screen.getByRole('button', { name: 'knowledge.tree.collapse' }))
  expect(screen.queryByRole('button', { name: 'Shared child' })).toBeNull()
  fireEvent.click(screen.getByRole('button', { name: /Locked folder/ }))
  expect(onSelect).toHaveBeenCalledWith(3)
  expect(screen.queryByRole('button', { name: 'knowledge.tree.newFolder' })).toBeNull()
})

describe('knowledge folder actions menu', () => {
  it('ports the nested folder menu out of the scrolling sidebar', async () => {
    mount()
    await waitFor(() => expect(screen.getAllByRole('button', { name: 'knowledge.tree.actions' })).toHaveLength(3))
    const nested = screen.getAllByRole('button', { name: 'knowledge.tree.actions' })[2]
    fireEvent.pointerDown(nested, { button: 0, ctrlKey: false })
    const portal = document.getElementById(PORTAL_ROOT_ID)
    await waitFor(() => expect(portal?.querySelector('[role="menu"]')).toBeTruthy())
    const menu = portal?.querySelector('[role="menu"]') ?? null
    const sidebar = screen.getByTestId('sidebar')
    expect(menu).toBeTruthy()
    expect(sidebar.contains(menu)).toBe(false)
    expect(portal?.contains(menu)).toBe(true)
    expect(screen.getByTestId('files').contains(menu)).toBe(false)
    expect(menu?.className).toContain('z-[60]')
    expect(portal?.textContent).toContain('knowledge.tree.newSubfolder')
    expect(portal?.textContent).toContain('knowledge.tree.edit')
    expect(portal?.textContent).toContain('knowledge.tree.delete')
  })

  it('still opens inside the tree when the shared portal root is absent', async () => {
    document.getElementById(PORTAL_ROOT_ID)?.remove()
    mount()
    await waitFor(() => expect(screen.getAllByRole('button', { name: 'knowledge.tree.actions' })).toHaveLength(3))
    fireEvent.pointerDown(screen.getAllByRole('button', { name: 'knowledge.tree.actions' })[0], { button: 0, ctrlKey: false })
    await waitFor(() => expect(screen.getByTestId('sidebar').textContent).toContain('knowledge.tree.newSubfolder'))
    expect(document.getElementById(PORTAL_ROOT_ID)?.textContent ?? '').not.toContain('knowledge.tree.newSubfolder')
  })
})
