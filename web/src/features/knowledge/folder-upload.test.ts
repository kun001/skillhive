// @vitest-environment jsdom
import { describe, expect, it, vi } from 'vitest'
import type { KnowledgeFolder } from '@/api/knowledge-types'
import { createUploadFolders, folderPaths } from './folder-upload'
import { createUploadItems } from './upload-queue'
import { planUploads } from './markdown-images'

function file(path: string, content = 'file') {
  const result = new File([content], path.split('/').at(-1)!)
  Object.defineProperty(result, 'webkitRelativePath', { value: path })
  Object.defineProperty(result, 'text', { value: async () => content })
  return result
}
function folder(id: number, name: string, parentId?: number): KnowledgeFolder {
  return { id, name, parentId, documentCount: 0, canManage: true }
}

describe('directory upload', () => {
  it('preserves the top directory and nested branches in parent-first order', () => {
    expect(folderPaths([file('资料/制度/员工.mdx'), file('资料/说明.txt'), file('资料/图片/a.png')]))
      .toEqual(['资料', '资料/制度', '资料/图片'])
  })
  it('rejects traversal and duplicate file paths instead of flattening them', () => {
    for (const path of ['a/../x.txt', '/a.txt', 'a\\b/x.txt', 'a//x.txt', ' a /x.txt']) {
      expect(() => folderPaths([file(path)])).toThrow('folderPathInvalid')
    }
    expect(() => folderPaths([file('a/x.txt'), file('a/x.txt')])).toThrow('folderPathInvalid')
  })
  it('keeps created parents for retry without merging an existing root', async () => {
    const created = new Map<string, number>()
    const create = vi.fn().mockResolvedValueOnce(folder(1, 'root', 9)).mockRejectedValueOnce(new Error('offline'))
      .mockResolvedValueOnce(folder(2, 'child', 1))
    await expect(createUploadFolders(['root', 'root/child'], 9, [], created, create)).rejects.toThrow('offline')
    await createUploadFolders(['root', 'root/child'], 9, [folder(1, 'root', 9)], created, create)
    expect(create.mock.calls).toEqual([['root', 9], ['child', 1], ['child', 1]])
    const conflict = vi.fn()
    await expect(createUploadFolders(['root'], 9, [folder(3, 'root', 9)], new Map(), conflict)).rejects.toThrow('folderExists')
    expect(conflict).not.toHaveBeenCalled()
  })
  it('keeps every directory image as a file and bundles only referenced images', async () => {
    const items = createUploadItems([file('root/a.md', '![photo](images/a.png)'), file('root/images/a.png'), file('root/images/unused.png')])
    const plan = await planUploads(items, undefined, true)
    expect(plan).toHaveLength(3)
    expect(plan[0].bundle.images.map((image) => image.path)).toEqual(['root/images/a.png'])
    expect(plan[0].error).toBeUndefined()
  })
})
