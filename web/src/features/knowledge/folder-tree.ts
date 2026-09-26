import type { KnowledgeFolder } from '@/api/knowledge-types'

export interface KnowledgeFolderNode {
  folder: KnowledgeFolder
  children: KnowledgeFolderNode[]
  /** Files in this folder and every descendant. */
  totalCount: number
  depth: number
}

/** Builds a sorted folder tree from the flat list the API returns. */
export function buildFolderTree(folders: KnowledgeFolder[]): KnowledgeFolderNode[] {
  const childrenByParent = new Map<number | null, KnowledgeFolder[]>()
  const ids = new Set(folders.map((folder) => folder.id))
  for (const folder of folders) {
    const parentId = folder.parentId != null && ids.has(folder.parentId) ? folder.parentId : null
    const siblings = childrenByParent.get(parentId) ?? []
    siblings.push(folder)
    childrenByParent.set(parentId, siblings)
  }

  const build = (parentId: number | null, depth: number, seen: Set<number>): KnowledgeFolderNode[] =>
    (childrenByParent.get(parentId) ?? [])
      .filter((folder) => !seen.has(folder.id))
      .sort((left, right) => left.name.localeCompare(right.name))
      .map((folder) => {
        const children = build(folder.id, depth + 1, new Set(seen).add(folder.id))
        const totalCount = folder.documentCount + children.reduce((sum, child) => sum + child.totalCount, 0)
        return { folder, children, totalCount, depth }
      })

  return build(null, 0, new Set())
}

/** Folder ids from the root down to (and including) the given folder. */
export function folderAncestry(folders: KnowledgeFolder[], folderId: number | undefined): KnowledgeFolder[] {
  if (folderId === undefined) {
    return []
  }
  const byId = new Map(folders.map((folder) => [folder.id, folder]))
  const path: KnowledgeFolder[] = []
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
