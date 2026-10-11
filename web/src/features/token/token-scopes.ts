/**
 * API token scope domains. Skill and knowledge access are granted separately: skill access is
 * either read-only or read-write (publish and delete too), while knowledge is read-only and
 * opt-in. Mirrors ApiTokenScopes on the server. Read-only tokens are what the MCP server accepts.
 */
export const SKILL_READ_SCOPES = ['skill:read'] as const
export const SKILL_READ_WRITE_SCOPES = ['skill:read', 'skill:publish', 'skill:delete'] as const
export const KNOWLEDGE_TOKEN_SCOPES = ['knowledge:read'] as const

export type SkillAccess = 'none' | 'read' | 'readWrite'

export type TokenScopeDomain = 'skillReadWrite' | 'skillRead' | 'knowledge' | 'tokenManage'

export interface TokenScopeSelection {
  skill: SkillAccess
  knowledge: boolean
}

export const DEFAULT_TOKEN_SCOPE_SELECTION: TokenScopeSelection = { skill: 'readWrite', knowledge: false }

/** Scopes to request for a selection; empty when nothing is selected. */
export function scopesForSelection(selection: TokenScopeSelection): string[] {
  const skillScopes = selection.skill === 'readWrite'
    ? SKILL_READ_WRITE_SCOPES
    : selection.skill === 'read' ? SKILL_READ_SCOPES : []
  return [...skillScopes, ...(selection.knowledge ? KNOWLEDGE_TOKEN_SCOPES : [])]
}

/** Domains a token's scopes reach, in display order. */
export function domainsForScopes(scopes: readonly string[] | undefined): TokenScopeDomain[] {
  const granted = new Set(scopes ?? [])
  const domains: TokenScopeDomain[] = []
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
