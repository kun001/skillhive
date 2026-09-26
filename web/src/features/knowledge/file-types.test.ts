import { describe, expect, it } from 'vitest'
import {
  categoryForExtension,
  extensionsForCategory,
  fileExtension,
  formatFileSize,
  KNOWLEDGE_MAX_FILE_SIZE_BYTES,
  validateKnowledgeFile,
} from './file-types'

describe('knowledge file types', () => {
  it('reads extensions case-insensitively', () => {
    expect(fileExtension('员工手册.PDF')).toBe('pdf')
    expect(fileExtension('README')).toBe('')
    expect(fileExtension('.env')).toBe('')
  })

  it('maps categories to extensions and back', () => {
    expect(extensionsForCategory('word')).toEqual(['doc', 'docx'])
    expect(extensionsForCategory('unknown')).toBeUndefined()
    expect(categoryForExtension('XLSX')).toBe('excel')
    expect(categoryForExtension('exe')).toBeUndefined()
  })

  it('validates type, emptiness and size before uploading', () => {
    expect(validateKnowledgeFile({ name: 'a.pdf', size: 10 })).toBeUndefined()
    expect(validateKnowledgeFile({ name: 'a.html', size: 10 })).toBe('knowledge.upload.errorType')
    expect(validateKnowledgeFile({ name: 'a.pdf', size: 0 })).toBe('knowledge.upload.errorEmpty')
    expect(validateKnowledgeFile({ name: 'a.pdf', size: KNOWLEDGE_MAX_FILE_SIZE_BYTES + 1 })).toBe('knowledge.upload.errorTooLarge')
  })

  it('formats sizes', () => {
    expect(formatFileSize(512)).toBe('512 B')
    expect(formatFileSize(1536)).toBe('1.5 KB')
    expect(formatFileSize(25 * 1024 * 1024)).toBe('25 MB')
  })
})
