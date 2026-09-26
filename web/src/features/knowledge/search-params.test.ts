import { describe, expect, it } from 'vitest'
import { updatedFromForRange, validateKnowledgeBaseSearch } from './search-params'

describe('validateKnowledgeBaseSearch', () => {
  it('keeps valid values and drops defaults', () => {
    expect(
      validateKnowledgeBaseSearch({ folder: '3', q: '  年假 ', type: 'pdf', owner: 'me', time: '30', sort: 'title', page: '2', view: 'grid' }),
    ).toEqual({ folder: 3, q: '年假', type: 'pdf', owner: 'me', time: '30', sort: 'title', page: 2, view: 'grid' })
    expect(validateKnowledgeBaseSearch({ sort: 'updated', view: 'list' })).toEqual({
      folder: undefined, q: undefined, type: undefined, owner: undefined, time: undefined, sort: undefined, page: undefined, view: undefined,
    })
  })

  it('rejects malformed values', () => {
    const search = validateKnowledgeBaseSearch({ folder: '-1', type: 'exe', owner: 'bob', time: '5', sort: 'size', page: '1.5' })
    expect(search.folder).toBeUndefined()
    expect(search.type).toBeUndefined()
    expect(search.owner).toBeUndefined()
    expect(search.time).toBeUndefined()
    expect(search.sort).toBeUndefined()
    expect(search.page).toBeUndefined()
  })
})

describe('updatedFromForRange', () => {
  it('starts the window at local midnight N days ago', () => {
    const now = new Date(2026, 8, 26, 15, 30)
    expect(updatedFromForRange('7', now)).toBe(new Date(2026, 8, 19, 0, 0, 0, 0).toISOString())
    expect(updatedFromForRange(undefined, now)).toBeUndefined()
  })
})
