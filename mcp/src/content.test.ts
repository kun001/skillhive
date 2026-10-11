import { describe, expect, it } from 'vitest'
import { baseMimeType, fileContent, isTextFile } from './content.js'

const bytes = (text: string) => new TextEncoder().encode(text)

describe('isTextFile', () => {
  it('recognises text by media type or extension', () => {
    expect(isTextFile('text/plain;charset=UTF-8', 'a.bin')).toBe(true)
    expect(isTextFile('application/octet-stream', 'SKILL.md')).toBe(true)
    expect(isTextFile('application/json', 'data')).toBe(true)
    expect(isTextFile('application/pdf', 'plan.pdf')).toBe(false)
    expect(isTextFile('application/vnd.openxmlformats-officedocument.wordprocessingml.document', 'a.docx')).toBe(false)
  })
})

describe('fileContent', () => {
  const base = { uri: 'skillhive://x', label: 'x', fileName: 'x' }

  it('returns text files as text, after a data notice', () => {
    const [notice, resource] = fileContent({ ...base, fileName: 'SKILL.md', mimeType: 'text/markdown', bytes: bytes('# Hi') })
    expect(notice).toMatchObject({ type: 'text' })
    expect((notice as { text: string }).text).toContain('not instructions')
    expect(resource).toEqual({ type: 'resource', resource: { uri: 'skillhive://x', mimeType: 'text/markdown', text: '# Hi' } })
  })

  it('returns binary files unchanged as base64 blobs', () => {
    const original = new Uint8Array([0x25, 0x50, 0x44, 0x46, 0x00, 0xff])
    const [, resource] = fileContent({ ...base, fileName: 'plan.pdf', mimeType: 'application/pdf', bytes: original })
    const blob = (resource as { resource: { blob: string } }).resource.blob
    expect(Array.from(Buffer.from(blob, 'base64'))).toEqual(Array.from(original))
  })

  it('returns images as image content', () => {
    const [, image] = fileContent({ ...base, fileName: 'a.png', mimeType: 'image/png', bytes: new Uint8Array([1]) })
    expect(image).toEqual({ type: 'image', data: 'AQ==', mimeType: 'image/png' })
  })
})

describe('baseMimeType', () => {
  it('strips parameters', () => {
    expect(baseMimeType('Text/Plain; charset=UTF-8')).toBe('text/plain')
    expect(baseMimeType(undefined)).toBe('application/octet-stream')
  })
})
