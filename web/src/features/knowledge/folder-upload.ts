import type { KnowledgeFolder } from '@/api/knowledge-types'
import { uploadPath } from './markdown-images'

/** Only logical paths are used. Never silently flatten or normalize an unsafe directory. */
export function folderPaths(files: File[]): string[] {
  const folders = new Set<string>()
  const paths = new Set<string>()
  for (const file of files) {
    const path = uploadPath(file)
    const parts = path.split('/')
    if (paths.has(path) || path.length > 1024 || parts.some((part) =>
      !part.trim() || part !== part.trim() || part === '.' || part === '..' || /[\\:\p{Cc}]/u.test(part))) {
      throw new Error('knowledge.upload.folderPathInvalid')
    }
    paths.add(path)
    for (let i = 1; i < parts.length; i++) {
      if (parts[i - 1].length > 128) throw new Error('knowledge.upload.folderPathInvalid')
      folders.add(parts.slice(0, i).join('/'))
    }
  }
  return [...folders].sort((a, b) => a.split('/').length - b.split('/').length || a.localeCompare(b))
}

/** Creates a fresh directory tree; the caller keeps the map across failed-file retries. */
export async function createUploadFolders(
  paths: string[], targetFolder: number | undefined, existing: KnowledgeFolder[], created: Map<string, number>,
  create: (name: string, parentId: number | undefined) => Promise<KnowledgeFolder>,
): Promise<void> {
  // A collision is an error, never an implicit merge with somebody else's existing directory.
  for (const path of paths.filter((path) => !path.includes('/'))) {
    if (!created.has(path) && existing.some((folder) => folder.name === path && (folder.parentId ?? undefined) === targetFolder)) {
      throw new Error('knowledge.upload.folderExists')
    }
  }
  for (const path of paths) {
    if (created.has(path)) continue
    const index = path.lastIndexOf('/')
    const parentId = index < 0 ? targetFolder : created.get(path.slice(0, index))
    if (index >= 0 && parentId === undefined) throw new Error('knowledge.upload.folderPathInvalid')
    const folder = await create(path.slice(index + 1), parentId)
    created.set(path, folder.id)
  }
}
