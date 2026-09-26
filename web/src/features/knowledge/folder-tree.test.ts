import { describe, expect, it } from 'vitest'
import type { KnowledgeFolder } from '@/api/knowledge-types'
import { buildFolderTree, descendantIds, flattenFolderTree, folderAncestry } from './folder-tree'

function folder(id: number, name: string, parentId: number | null, documentCount = 0): KnowledgeFolder {
  return { id, name, parentId: parentId ?? undefined, documentCount, canManage: true }
}

const folders = [
  folder(1, '休假', null, 1),
  folder(2, '年假', 1, 2),
  folder(3, '病假', 1, 0),
  folder(4, '报销', null, 3),
]

describe('buildFolderTree', () => {
  it('nests folders, sorts siblings and totals descendant counts', () => {
    const tree = buildFolderTree(folders)
    expect(tree.map((node) => node.folder.name)).toEqual(['报销', '休假'].sort((a, b) => a.localeCompare(b)))
    const leave = tree.find((node) => node.folder.id === 1)!
    expect(leave.totalCount).toBe(3)
    expect(leave.children.map((node) => node.depth)).toEqual([1, 1])
  })

  it('treats folders with unknown parents as roots and survives cycles', () => {
    const tree = buildFolderTree([folder(5, 'orphan', 99), folder(6, 'a', 7), folder(7, 'b', 6)])
    expect(tree.map((node) => node.folder.id)).toEqual([5])
  })
})

describe('folder helpers', () => {
  it('returns the path from the root to a folder', () => {
    expect(folderAncestry(folders, 2).map((item) => item.name)).toEqual(['休假', '年假'])
    expect(folderAncestry(folders, undefined)).toEqual([])
  })

  it('flattens depth-first and lists descendants for move validation', () => {
    const tree = buildFolderTree(folders)
    expect(flattenFolderTree(tree)).toHaveLength(4)
    expect([...descendantIds(tree, 1)].sort()).toEqual([1, 2, 3])
    expect([...descendantIds(tree, 4)]).toEqual([4])
  })
})
