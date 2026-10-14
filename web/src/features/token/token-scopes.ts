/**
 * API and MCP token scope domains. Dedicated MCP credentials grant each operation independently;
 * ordinary API credentials retain their existing skill profiles. Mirrors server ApiTokenScopes.
 */
export const SKILL_READ_SCOPES = ['skill:read'] as const
export const SKILL_READ_WRITE_SCOPES = ['skill:read', 'skill:publish', 'skill:delete'] as const
export const KNOWLEDGE_TOKEN_SCOPES = ['knowledge:read'] as const

export type SkillAccess = 'none' | 'read' | 'readWrite'

export type TokenScopeDomain = 'skillReadWrite' | 'skillRead' | 'knowledge' | 'tokenManage' | 'skillUpload' | 'skillDelete' | 'knowledgeUpload' | 'knowledgeDelete'

export interface TokenScopeSelection {
  skill: SkillAccess
  knowledge: boolean
  skillUpload?: boolean
  skillDelete?: boolean
  knowledgeUpload?: boolean
  knowledgeDelete?: boolean
}

export const DEFAULT_TOKEN_SCOPE_SELECTION: TokenScopeSelection = { skill: 'read', knowledge: false }

/** Scopes to request for a selection; empty when nothing is selected. */
export function scopesForSelection(selection: TokenScopeSelection, mcp = false): string[] {
  if (mcp) return [selection.skill !== 'none' && 'skill:read', selection.knowledge && 'knowledge:read',
    selection.skillUpload && 'skill:upload', selection.skillDelete && 'skill:delete',
    selection.knowledgeUpload && 'knowledge:upload', selection.knowledgeDelete && 'knowledge:delete'].filter((value): value is string => typeof value === 'string')
  const skillScopes = selection.skill === 'readWrite'
    ? SKILL_READ_WRITE_SCOPES
    : selection.skill === 'read' ? SKILL_READ_SCOPES : []
  return [...skillScopes, ...(selection.knowledge ? KNOWLEDGE_TOKEN_SCOPES : [])]
}

/** Domains a token's scopes reach, in display order. */
export function domainsForScopes(scopes: readonly string[] | undefined): TokenScopeDomain[] {
  const granted = new Set(scopes ?? [])
  const domains: TokenScopeDomain[] = []
  if (granted.has('mcp:access')) {
    if (granted.has('skill:read')) domains.push('skillRead')
    if (granted.has('knowledge:read')) domains.push('knowledge')
    for (const [scope, label] of [['skill:upload', 'skillUpload'], ['skill:delete', 'skillDelete'],
      ['knowledge:upload', 'knowledgeUpload'], ['knowledge:delete', 'knowledgeDelete']] as const) {
      if (granted.has(scope)) domains.push(label)
    }
    return domains
  }
  if (granted.has('skill:publish') || granted.has('skill:delete')) {
    domains.push('skillReadWrite')
  } else if (granted.has('skill:read')) {
    domains.push('skillRead')
  }
  if (KNOWLEDGE_TOKEN_SCOPES.some((scope) => granted.has(scope))) {
    domains.push('knowledge')
  }
  if (granted.has('token:manage')) {
    domains.push('tokenManage')
  }
  return domains
}
