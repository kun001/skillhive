import { fromMarkdown } from 'mdast-util-from-markdown'
import { visit } from 'unist-util-visit'
import { fileExtension, KNOWLEDGE_MAX_FILE_SIZE_BYTES, validateKnowledgeFile } from './file-types'
import type { UploadItem } from './upload-queue'

export interface MarkdownImageUpload { file: File; path: string; itemId: string }
export interface MarkdownUploadBundle { sourcePath: string; images: MarkdownImageUpload[] }
export interface UploadPlanEntry { item: UploadItem; bundle: MarkdownUploadBundle; error?: { key: string; path?: string } }

export function isMarkdown(file: File) { return ['md', 'markdown'].includes(fileExtension(file.name)) }
export function uploadPath(file: File) { return file.webkitRelativePath || file.name }

/** Resolve local image URLs against a logical package path, without touching remote URLs. */
export function resolveImagePath(sourcePath: string, url: string): string | undefined {
  if (!url || /^(?:[a-z][a-z\d+.-]*:|\/|#)/i.test(url)) return undefined
  let decoded: string
  try { decoded = decodeURIComponent(url.split(/[?#]/)[0]) } catch { return undefined }
  const parts = sourcePath.split('/').slice(0, -1)
  for (const part of decoded.replace(/\\/g, '/').split('/')) {
    if (!part || part === '.') continue
    if (part === '..') { if (!parts.length) return undefined; parts.pop() }
    else parts.push(part)
  }
  const path = parts.join('/')
  return path && !path.includes(':') && !Array.from(path).some((char) => char.charCodeAt(0) < 32 || char.charCodeAt(0) === 127) ? path : undefined
}

export function markdownImageUrls(content: string): string[] {
  const tree = fromMarkdown(content)
  const definitions = new Map<string, string>()
  visit(tree, 'definition', (node) => { if (!definitions.has(node.identifier)) definitions.set(node.identifier, node.url) })
  const urls = new Set<string>()
  visit(tree, (node) => {
    if (node.type === 'image') urls.add(node.url)
    if (node.type === 'imageReference') {
      const url = definitions.get(node.identifier)
      if (url) urls.add(url)
    }
  })
  return [...urls]
}

/** Images referenced by Markdown travel in the same request, rather than becoming separate documents. */
export async function planUploads(items: UploadItem[], existing?: { sourcePath: string; paths: string[] }): Promise<UploadPlanEntry[]> {
  const consumed = new Set<string>()
  const entries: UploadPlanEntry[] = []
  const candidates = items.filter((item) => ['png', 'jpg', 'jpeg', 'gif', 'webp'].includes(fileExtension(item.file.name)))
  for (const item of items) {
    let sourcePath = uploadPath(item.file)
    if (existing && !item.file.webkitRelativePath && isMarkdown(item.file)) {
      sourcePath = existing.sourcePath.slice(0, existing.sourcePath.lastIndexOf('/') + 1) + item.file.name
    }
    const entry: UploadPlanEntry = { item, bundle: { sourcePath, images: [] } }
    entries.push(entry)
    if (!isMarkdown(item.file) || validateKnowledgeFile(item.file)) continue
    try {
      const urls = markdownImageUrls(await item.file.text())
      const attached = new Set<string>()
      for (const url of urls) {
        if (/^(?:[a-z][a-z\d+.-]*:|\/|#)/i.test(url)) continue
        const path = resolveImagePath(sourcePath, url)
        if (!path) { entry.error ??= { key: 'knowledge.upload.imagePathInvalid', path: url }; continue }
        if (attached.has(path)) continue
        const exact = candidates.filter((candidate) => uploadPath(candidate.file) === path)
        // A regular file picker only exposes names. Never guess between duplicate names.
        const matches = exact.length ? exact : candidates.filter((candidate) => !candidate.file.webkitRelativePath && candidate.file.name === path.split('/').at(-1))
        if (!matches.length && existing?.paths.includes(path)) continue
        if (matches.length !== 1) {
          matches.forEach((candidate) => consumed.add(candidate.id))
          entry.error ??= { key: matches.length ? 'knowledge.upload.imageAmbiguous' : 'knowledge.upload.imageMissing', path: url }
          continue
        }
        const image = matches[0]
        consumed.add(image.id)
        attached.add(path)
        entry.bundle.images.push({ file: image.file, path, itemId: image.id })
        const invalid = validateKnowledgeFile(image.file)
        if (invalid) entry.error ??= { key: invalid, path: url }
      }
      if (entry.bundle.images.length > 100) entry.error ??= { key: 'knowledge.upload.imagesTooMany' }
      if (item.file.size + entry.bundle.images.reduce((size, image) => size + image.file.size, 0) > KNOWLEDGE_MAX_FILE_SIZE_BYTES) {
        entry.error ??= { key: 'knowledge.upload.bundleTooLarge' }
      }
    } catch { entry.error = { key: 'knowledge.upload.markdownReadFailed' } }
  }
  return entries.filter((entry) => !consumed.has(entry.item.id))
}
