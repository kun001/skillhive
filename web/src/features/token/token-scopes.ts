/**
 * API token scope domains. Skill and knowledge access are granted separately: the skill
 * domain covers reading, publishing and deleting skills, while knowledge is read-only and
 * opt-in. Mirrors ApiTokenScopes on the server.
 */
export const SKILL_TOKEN_SCOPES = ['skill:read', 'skill:publish', 'skill:delete'] as const
export const KNOWLEDGE_TOKEN_SCOPES = ['knowledge:read'] as const

export type TokenScopeDomain = 'skill' | 'knowledge' | 'tokenManage'

export interface TokenScopeSelection {
  skill: boolean
  knowledge: boolean
}

export const DEFAULT_TOKEN_SCOPE_SELECTION: TokenScopeSelection = { skill: true, knowledge: false }

/** Scopes to request for a selection; empty when nothing is selected. */
export function scopesForSelection(selection: TokenScopeSelection): string[] {
  return [
    ...(selection.skill ? SKILL_TOKEN_SCOPES : []),
    ...(selection.knowledge ? KNOWLEDGE_TOKEN_SCOPES : []),
  ]
}

/** Domains a token's scopes reach, in display order. */
export function domainsForScopes(scopes: readonly string[] | undefined): TokenScopeDomain[] {
  const granted = new Set(scopes ?? [])
  const domains: TokenScopeDomain[] = []
  if (SKILL_TOKEN_SCOPES.some((scope) => granted.has(scope))) {
    domains.push('skill')
  }
  if (KNOWLEDGE_TOKEN_SCOPES.some((scope) => granted.has(scope))) {
    domains.push('knowledge')
  }
  if (granted.has('token:manage')) {
    domains.push('tokenManage')
  }
  return domains
}
