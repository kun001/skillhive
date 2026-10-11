import { describe, expect, it } from 'vitest'
import { DEFAULT_TOKEN_SCOPE_SELECTION, domainsForScopes, scopesForSelection } from './token-scopes'

describe('scopesForSelection', () => {
  it('requests read-write skill scopes by default, without knowledge', () => {
    expect(scopesForSelection(DEFAULT_TOKEN_SCOPE_SELECTION)).toEqual(['skill:read', 'skill:publish', 'skill:delete'])
  })

  it('requests only skill:read for read-only skill access', () => {
    expect(scopesForSelection({ skill: 'read', knowledge: false })).toEqual(['skill:read'])
    expect(scopesForSelection({ skill: 'read', knowledge: true })).toEqual(['skill:read', 'knowledge:read'])
  })

  it('adds knowledge:read only when knowledge is selected', () => {
    expect(scopesForSelection({ skill: 'none', knowledge: true })).toEqual(['knowledge:read'])
    expect(scopesForSelection({ skill: 'readWrite', knowledge: true })).toEqual([
      'skill:read', 'skill:publish', 'skill:delete', 'knowledge:read',
    ])
  })

  it('returns nothing when no domain is selected', () => {
    expect(scopesForSelection({ skill: 'none', knowledge: false })).toEqual([])
  })
})

describe('domainsForScopes', () => {
  it('maps scopes to the domains they reach', () => {
    expect(domainsForScopes(['skill:read', 'skill:publish', 'skill:delete'])).toEqual(['skillReadWrite'])
    expect(domainsForScopes(['skill:read'])).toEqual(['skillRead'])
    expect(domainsForScopes(['knowledge:read'])).toEqual(['knowledge'])
    expect(domainsForScopes(['knowledge:read', 'skill:read', 'token:manage'])).toEqual(['skillRead', 'knowledge', 'tokenManage'])
  })

  it('handles missing scopes', () => {
    expect(domainsForScopes(undefined)).toEqual([])
    expect(domainsForScopes([])).toEqual([])
  })
})
