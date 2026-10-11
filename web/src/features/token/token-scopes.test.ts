import { describe, expect, it } from 'vitest'
import { DEFAULT_TOKEN_SCOPE_SELECTION, domainsForScopes, scopesForSelection } from './token-scopes'

describe('scopesForSelection', () => {
  it('requests only skill scopes by default', () => {
    expect(scopesForSelection(DEFAULT_TOKEN_SCOPE_SELECTION)).toEqual(['skill:read', 'skill:publish', 'skill:delete'])
  })

  it('adds knowledge:read only when knowledge is selected', () => {
    expect(scopesForSelection({ skill: false, knowledge: true })).toEqual(['knowledge:read'])
    expect(scopesForSelection({ skill: true, knowledge: true })).toEqual([
      'skill:read', 'skill:publish', 'skill:delete', 'knowledge:read',
    ])
  })

  it('returns nothing when no domain is selected', () => {
    expect(scopesForSelection({ skill: false, knowledge: false })).toEqual([])
  })
})

describe('domainsForScopes', () => {
  it('maps scopes to the domains they reach', () => {
    expect(domainsForScopes(['skill:read', 'skill:publish', 'skill:delete'])).toEqual(['skill'])
    expect(domainsForScopes(['knowledge:read'])).toEqual(['knowledge'])
    expect(domainsForScopes(['knowledge:read', 'skill:read', 'token:manage'])).toEqual(['skill', 'knowledge', 'tokenManage'])
  })

  it('handles missing scopes', () => {
    expect(domainsForScopes(undefined)).toEqual([])
    expect(domainsForScopes([])).toEqual([])
  })
})
