import type { SkillSummary } from '@/api/types'

/** Reuse generated function copy; keep the source summary for versions without it. */
export function getSkillSummaryDescription(
  skill: Pick<SkillSummary, 'summary' | 'functionDescription'>,
  language: string,
): string | undefined {
  const description = language.startsWith('zh') ? skill.functionDescription?.zh : skill.functionDescription?.en
  return description?.trim() || skill.summary || undefined
}
