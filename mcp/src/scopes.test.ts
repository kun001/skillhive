import { describe, expect, it } from 'vitest'
import { checkTokenScopes, TokenScopeError } from './scopes.js'

describe('checkTokenScopes', () => {
  it('maps read scopes to read access', () => {
    expect(checkTokenScopes('api_token', ['skill:read'])).toEqual({ skills: true, knowledge: false })
    expect(checkTokenScopes('api_token', ['knowledge:read'])).toEqual({ skills: false, knowledge: true })
    expect(checkTokenScopes('api_token', ['knowledge:read', 'skill:read'])).toEqual({ skills: true, knowledge: true })
  })

  it('accepts explicitly scoped MCP mutations but refuses unknown or legacy elevated scopes', () => {
    expect(checkTokenScopes('api_token', ['mcp:access', 'knowledge:upload'])).toEqual({
      skills: false, knowledge: false, skillUpload: false, skillDelete: false, knowledgeUpload: true, knowledgeDelete: false,
    })
    expect(() => checkTokenScopes('api_token', ['mcp:access', 'knowledge:update'])).toThrow(TokenScopeError)
    expect(() => checkTokenScopes('api_token', ['mcp:access', 'skill:publish'])).toThrow(TokenScopeError)
    expect(() => checkTokenScopes('api_token', ['knowledge:upload'])).toThrow(TokenScopeError)
  })

  it('refuses tokens with write scopes', () => {
    expect(() => checkTokenScopes('api_token', ['skill:read', 'skill:publish', 'skill:delete']))
      .toThrow(/skill:publish, skill:delete/)
    expect(() => checkTokenScopes('api_token', ['knowledge:read', 'token:manage'])).toThrow(TokenScopeError)
  })

  it('refuses tokens that can read nothing', () => {
    expect(() => checkTokenScopes('api_token', [])).toThrow(/nothing to read/)
  })

  it('fails closed when the server does not report token scopes', () => {
    expect(() => checkTokenScopes('api_token', undefined)).toThrow(/did not report/)
    expect(() => checkTokenScopes('github', null)).toThrow(TokenScopeError)
  })
})
