import type { KnowledgeFolder } from '@/api/knowledge-types'

export interface FolderTreeItem {
  id: number
  name: string
  parentId?: number | null
  documentCount?: number
  canManage?: boolean
  entryOnly?: boolean
}

export interface KnowledgeFolderNode<Folder extends FolderTreeItem = KnowledgeFolder> {
  folder: Folder
  children: KnowledgeFolderNode<Folder>[]
  /** Files in this folder and every descendant. */
  totalCount: number
  depth: number
}

/** Builds a sorted folder tree from the flat list the API returns. */
export function buildFolderTree<Folder extends FolderTreeItem>(folders: readonly Folder[]): KnowledgeFolderNode<Folder>[] {
  const childrenByParent = new Map<number | null, Folder[]>()
  const ids = new Set(folders.map((folder) => folder.id))
  for (const folder of folders) {
    const parentId = folder.parentId != null && ids.has(folder.parentId) ? folder.parentId : null
    const siblings = childrenByParent.get(parentId) ?? []
    siblings.push(folder)
    childrenByParent.set(parentId, siblings)
  }

  const build = (parentId: number | null, depth: number, seen: Set<number>): KnowledgeFolderNode<Folder>[] =>
    (childrenByParent.get(parentId) ?? [])
      .filter((folder) => !seen.has(folder.id))
      .sort((left, right) => left.name.localeCompare(right.name))
      .map((folder) => {
        const children = build(folder.id, depth + 1, new Set(seen).add(folder.id))
        const totalCount = (folder.documentCount ?? 0) + children.reduce((sum, child) => sum + child.totalCount, 0)
        return { folder, children, totalCount, depth }
      })

  return build(null, 0, new Set())
}

/** Folder ids from the root down to (and including) the given folder. */
export function folderAncestry<Folder extends FolderTreeItem>(folders: readonly Folder[], folderId: number | undefined): Folder[] {
  if (folderId === undefined) {
    return []
  }
  const byId = new Map(folders.map((folder) => [folder.id, folder]))
  const path: Folder[] = []
  let current = byId.get(folderId)
  while (current && path.length <= folders.length) {
    path.unshift(current)
    current = current.parentId != null ? byId.get(current.parentId) : undefined
  }
  return path
}

/** Flattens the tree depth-first, e.g. for folder pickers. */
export function flattenFolderTree(nodes: KnowledgeFolderNode[]): KnowledgeFolderNode[] {
  return nodes.flatMap((node) => [node, ...flattenFolderTree(node.children)])
}

/** Ids of a folder and all its descendants; a folder cannot be moved into any of them. */
export function descendantIds(nodes: KnowledgeFolderNode[], folderId: number): Set<number> {
  const target = flattenFolderTree(nodes).find((node) => node.folder.id === folderId)
  return new Set(target ? flattenFolderTree([target]).map((node) => node.folder.id) : [])
}

/** Visitor sharing traverses only public folders; inherited root folders stay private. */
export function publicShareFolderIds(nodes: KnowledgeFolderNode[]): ReadonlySet<number> {
  const ids = new Set<number>()
  const pending = nodes.filter(({ folder }) => folder.shareVisibility === 'PUBLIC')
  while (pending.length > 0) {
    const { folder, children } = pending.pop()!
    ids.add(folder.id)
    pending.push(...children.filter(({ folder: child }) =>
      child.shareVisibility == null || child.shareVisibility === 'INHERIT' || child.shareVisibility === 'PUBLIC'))
  }
  return ids
}
