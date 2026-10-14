/** Scopes that let a token change data. The MCP server is read-only and refuses tokens holding any of them. */
export const WRITE_SCOPES = ['skill:publish', 'skill:delete', 'token:manage'] as const

export interface ReadAccess {
  skills: boolean
  knowledge: boolean
  skillUpload?: boolean
  skillDelete?: boolean
  knowledgeUpload?: boolean
  knowledgeDelete?: boolean
}

export class TokenScopeError extends Error {}

/**
 * Checks what the token behind /api/v1/auth/me may do. Fails closed: a token with write scopes,
 * or a server that does not report token scopes, stops the MCP server from starting.
 */
export function checkTokenScopes(oauthProvider: string | undefined, tokenScopes: string[] | null | undefined): ReadAccess {
  if (oauthProvider !== 'api_token' || !Array.isArray(tokenScopes)) {
    throw new TokenScopeError(
      'SkillHive did not report the token scopes; upgrade SkillHive to a version that supports the MCP server')
  }
  const dedicated = tokenScopes.includes('mcp:access')
  const allowed = dedicated
    ? ['mcp:access', 'skill:read', 'skill:upload', 'skill:delete', 'knowledge:read', 'knowledge:upload', 'knowledge:delete']
    : ['skill:read', 'knowledge:read']
  const writeScopes = tokenScopes.filter((scope) => !allowed.includes(scope))
  if (writeScopes.length > 0) {
    throw new TokenScopeError(
      `The token has write scopes (${writeScopes.join(', ')}). The MCP server is read-only: create a token `
      + 'with only "Skills (read)" and/or "Knowledge (read-only)" access and use it instead')
  }
  const access: ReadAccess = { skills: tokenScopes.includes('skill:read'), knowledge: tokenScopes.includes('knowledge:read') }
  if (dedicated) Object.assign(access, { skillUpload: tokenScopes.includes('skill:upload'), skillDelete: tokenScopes.includes('skill:delete'),
    knowledgeUpload: tokenScopes.includes('knowledge:upload'), knowledgeDelete: tokenScopes.includes('knowledge:delete') })
  if (!Object.values(access).some(Boolean)) {
    throw new TokenScopeError('The token has neither skill:read nor knowledge:read, so there is nothing to read')
  }
  return access
}
