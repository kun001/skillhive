import type { LucideIcon } from 'lucide-react'
import { File, FileArchive, FileImage, FileSpreadsheet, FileText, Presentation } from 'lucide-react'

/** Filter categories shown in the type dropdown; each maps to the extensions the backend accepts. */
export const KNOWLEDGE_FILE_CATEGORIES = [
  { id: 'pdf', extensions: ['pdf'] },
  { id: 'word', extensions: ['doc', 'docx'] },
  { id: 'excel', extensions: ['xls', 'xlsx', 'csv'] },
  { id: 'ppt', extensions: ['ppt', 'pptx'] },
  { id: 'image', extensions: ['png', 'jpg', 'jpeg', 'gif', 'webp'] },
  { id: 'text', extensions: ['md', 'markdown', 'txt', 'json'] },
  { id: 'archive', extensions: ['zip', 'rar', '7z'] },
] as const

export type KnowledgeFileCategoryId = (typeof KNOWLEDGE_FILE_CATEGORIES)[number]['id']

export const KNOWLEDGE_ACCEPTED_EXTENSIONS: string[] = KNOWLEDGE_FILE_CATEGORIES.flatMap((category) => [...category.extensions])

/** Must match KnowledgeFilePolicy.MAX_FILE_SIZE_BYTES on the server. */
export const KNOWLEDGE_MAX_FILE_SIZE_BYTES = 100 * 1024 * 1024

export function extensionsForCategory(categoryId: string | undefined): string[] | undefined {
  const category = KNOWLEDGE_FILE_CATEGORIES.find((candidate) => candidate.id === categoryId)
  return category ? [...category.extensions] : undefined
}

export function fileExtension(fileName: string): string {
  const dot = fileName.lastIndexOf('.')
  return dot > 0 && dot < fileName.length - 1 ? fileName.slice(dot + 1).toLowerCase() : ''
}

export function categoryForExtension(extension: string): KnowledgeFileCategoryId | undefined {
  const normalized = extension.toLowerCase()
  return KNOWLEDGE_FILE_CATEGORIES.find((category) => (category.extensions as readonly string[]).includes(normalized))?.id
}

const CATEGORY_ICONS: Record<KnowledgeFileCategoryId, LucideIcon> = {
  pdf: FileText,
  word: FileText,
  excel: FileSpreadsheet,
  ppt: Presentation,
  image: FileImage,
  text: FileText,
  archive: FileArchive,
}

const CATEGORY_TONES: Record<KnowledgeFileCategoryId, string> = {
  pdf: 'text-red-600 bg-red-500/10 dark:text-red-300',
  word: 'text-blue-600 bg-blue-500/10 dark:text-blue-300',
  excel: 'text-emerald-600 bg-emerald-500/10 dark:text-emerald-300',
  ppt: 'text-orange-600 bg-orange-500/10 dark:text-orange-300',
  image: 'text-violet-600 bg-violet-500/10 dark:text-violet-300',
  text: 'text-stone-600 bg-stone-500/10 dark:text-stone-300',
  archive: 'text-amber-700 bg-amber-500/10 dark:text-amber-300',
}

export function fileIconFor(extension: string): { Icon: LucideIcon; tone: string } {
  const category = categoryForExtension(extension)
  return category
    ? { Icon: CATEGORY_ICONS[category], tone: CATEGORY_TONES[category] }
    : { Icon: File, tone: 'text-muted-foreground bg-muted' }
}

/** Returns an i18n key describing why a file cannot be uploaded, or undefined when it can. */
export function validateKnowledgeFile(file: { name: string; size: number }): string | undefined {
  if (file.size <= 0) {
    return 'knowledge.upload.errorEmpty'
  }
  if (file.size > KNOWLEDGE_MAX_FILE_SIZE_BYTES) {
    return 'knowledge.upload.errorTooLarge'
  }
  if (!KNOWLEDGE_ACCEPTED_EXTENSIONS.includes(fileExtension(file.name))) {
    return 'knowledge.upload.errorType'
  }
  return undefined
}

export function formatFileSize(bytes: number): string {
  if (bytes < 1024) {
    return `${bytes} B`
  }
  const units = ['KB', 'MB', 'GB']
  let value = bytes / 1024
  let unit = 0
  while (value >= 1024 && unit < units.length - 1) {
    value /= 1024
    unit += 1
  }
  return `${value >= 10 ? value.toFixed(0) : value.toFixed(1)} ${units[unit]}`
}
