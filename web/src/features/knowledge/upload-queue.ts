import { validateKnowledgeFile } from './file-types'

export type UploadStatus = 'waiting' | 'uploading' | 'done' | 'failed'

export interface UploadItem {
  id: string
  file: File
  status: UploadStatus
  progress: number
  /** i18n key for a local validation error, or a server message for a failed upload. */
  error?: string
  /** True when `error` is an i18n key rather than a server message. */
  errorIsKey?: boolean
}

let sequence = 0

export function createUploadItems(files: Iterable<File>): UploadItem[] {
  return Array.from(files, (file) => {
    const validationError = validateKnowledgeFile(file)
    sequence += 1
    return {
      id: `${Date.now()}-${sequence}`,
      file,
      status: validationError ? 'failed' : 'waiting',
      progress: 0,
      error: validationError,
      errorIsKey: validationError !== undefined,
    }
  })
}

/** Items that can be (re)sent: waiting ones, and failures caused by the server or network. */
export function isUploadable(item: UploadItem): boolean {
  return item.status === 'waiting' || (item.status === 'failed' && !item.errorIsKey)
}

export function summarizeUploads(items: UploadItem[]) {
  return {
    total: items.length,
    done: items.filter((item) => item.status === 'done').length,
    pending: items.filter((item) => item.status === 'waiting').length,
    uploading: items.some((item) => item.status === 'uploading'),
  }
}
