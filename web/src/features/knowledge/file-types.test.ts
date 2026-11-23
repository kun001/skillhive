import { describe, expect, it } from 'vitest'
import {
  categoryForExtension,
  extensionsForCategory,
  fileExtension,
  formatFileSize,
  KNOWLEDGE_MAX_FILE_SIZE_BYTES,
  typeFilterAfterUpload,
  validateKnowledgeFile,
} from './file-types'

describe('knowledge file types', () => {
  it('reads extensions case-insensitively', () => {
    expect(fileExtension('员工手册.PDF')).toBe('pdf')
    expect(fileExtension('README')).toBe('')
    expect(fileExtension('.env')).toBe('')
  })

  it('maps categories to extensions and back', () => {
    expect(extensionsForCategory('word')).toEqual(['doc', 'docx', 'wps'])
    expect(extensionsForCategory('unknown')).toBeUndefined()
    expect(categoryForExtension('WPS')).toBe('word')
    expect(categoryForExtension('XLSX')).toBe('excel')
    expect(categoryForExtension('exe')).toBeUndefined()
  })

  it('validates type, emptiness and size before uploading', () => {
    expect(validateKnowledgeFile({ name: 'a.pdf', size: 10 })).toBeUndefined()
    expect(validateKnowledgeFile({ name: 'component.MDX', size: 10 })).toBeUndefined()
    expect(categoryForExtension('mdx')).toBe('text')
    expect(validateKnowledgeFile({ name: 'a.html', size: 10 })).toBeUndefined()
    expect(validateKnowledgeFile({ name: 'a.HTM', size: 10 })).toBeUndefined()
    expect(categoryForExtension('html')).toBe('html')
    expect(validateKnowledgeFile({ name: '数据库脚本.SQL', size: 10 })).toBeUndefined()
    expect(validateKnowledgeFile({ name: '实施方案.WPS', size: 10 })).toBeUndefined()
    expect(categoryForExtension('sql')).toBe('text')
    expect(validateKnowledgeFile({ name: 'a.svg', size: 10 })).toBe('knowledge.upload.errorType')
    expect(validateKnowledgeFile({ name: 'a.pdf', size: 0 })).toBe('knowledge.upload.errorEmpty')
    expect(validateKnowledgeFile({ name: 'a.pdf', size: KNOWLEDGE_MAX_FILE_SIZE_BYTES + 1 })).toBe('knowledge.upload.errorTooLarge')
  })

  it('formats sizes', () => {
    expect(formatFileSize(512)).toBe('512 B')
    expect(formatFileSize(1536)).toBe('1.5 KB')
    expect(formatFileSize(25 * 1024 * 1024)).toBe('25 MB')
  })

  it.each(['ZIP', 'rar', '7Z'])('enforces the 500 MiB limit for %s archives', (extension) => {
    const name = `archive.${extension}`
    const limit = 500 * 1024 * 1024
    expect(validateKnowledgeFile({ name, size: limit })).toBeUndefined()
    expect(validateKnowledgeFile({ name, size: limit + 1 })).toBe('knowledge.upload.errorArchiveTooLarge')
  })

  it('keeps All after uploads so existing files remain visible', () => {
    expect(typeFilterAfterUpload(['notes.md'])).toBeUndefined()
    expect(typeFilterAfterUpload(['a.docx', 'b.doc'])).toBeUndefined()
  })

  it('preserves compatible filters and widens incompatible filters to All', () => {
    expect(typeFilterAfterUpload(['notes.md', 'query.sql'], 'text')).toBe('text')
    expect(typeFilterAfterUpload(['a.docx', 'b.doc', 'c.WPS'], 'word')).toBe('word')
    expect(typeFilterAfterUpload(['query.sql'], 'html')).toBeUndefined()
    expect(typeFilterAfterUpload(['a.md', 'b.docx'], 'text')).toBeUndefined()
    expect(typeFilterAfterUpload(['unknown.bin'], 'word')).toBeUndefined()
    expect(typeFilterAfterUpload([], 'html')).toBe('html')
  })
})
