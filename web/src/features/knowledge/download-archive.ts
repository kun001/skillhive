import { knowledgeApi } from '@/api/client'
import type { KnowledgeDocument } from '@/api/knowledge-types'

function uniqueFileName(file: KnowledgeDocument, names: Set<string>): string {
  // Control characters and path separators are invalid in archive filenames.
  // eslint-disable-next-line no-control-regex
  const cleaned = file.fileName.replace(/[<>:"/\\|?*\x00-\x1f]/g, '_').trim().replace(/\.+$/, '')
  const original = cleaned || `file-${file.id}`
  const extensionStart = original.lastIndexOf('.')
  const stem = extensionStart > 0 ? original.slice(0, extensionStart) : original
  const extension = extensionStart > 0 ? original.slice(extensionStart) : ''
  let name = original
  for (let suffix = 2; names.has(name.toLowerCase()); suffix += 1) {
    name = `${stem} (${suffix})${extension}`
  }
  names.add(name.toLowerCase())
  return name
}

export async function downloadKnowledgeArchive(files: KnowledgeDocument[], archiveName: string) {
  const { default: JSZip } = await import('jszip')
  const zip = new JSZip()
  const names = new Set<string>()

  for (const file of files) {
    const url = knowledgeApi.contentUrl(file.id, { version: file.currentVersion })
    const response = await fetch(url, { credentials: 'include', redirect: 'error' })
    if (!response.ok) throw new Error(`File download failed: ${response.status}`)
    zip.file(uniqueFileName(file, names), await response.arrayBuffer())
  }

  const archive = await zip.generateAsync({ type: 'blob', streamFiles: true })
  const url = URL.createObjectURL(archive)
  const link = document.createElement('a')
  link.href = url
  link.download = archiveName
  link.click()
  window.setTimeout(() => URL.revokeObjectURL(url), 60_000)
}
