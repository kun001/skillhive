import { describe, expect, it } from 'vitest'
import { createUploadItems, isUploadable, summarizeUploads } from './upload-queue'

function file(name: string, size: number): File {
  return new File([new Uint8Array(size)], name)
}

describe('upload queue', () => {
  it('marks locally invalid files as failed with an i18n key', () => {
    const [ok, bad] = createUploadItems([file('a.pdf', 4), file('b.exe', 4)])
    expect(ok.status).toBe('waiting')
    expect(bad).toMatchObject({ status: 'failed', error: 'knowledge.upload.errorType', errorIsKey: true })
    expect(isUploadable(ok)).toBe(true)
    expect(isUploadable(bad)).toBe(false)
  })

  it('allows retrying server failures and summarizes progress', () => {
    const [item] = createUploadItems([file('a.pdf', 4)])
    const failed = { ...item, status: 'failed' as const, error: 'boom', errorIsKey: false }
    expect(isUploadable(failed)).toBe(true)
    expect(summarizeUploads([failed, { ...item, id: 'x', status: 'done' }])).toEqual({ total: 2, done: 1, pending: 0, uploading: false })
  })
})
