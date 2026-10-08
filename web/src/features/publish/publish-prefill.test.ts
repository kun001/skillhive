import { describe, expect, it } from 'vitest'
import { normalizePublishPrefill } from './publish-prefill'

describe('normalizePublishPrefill', () => {
  it('allows public publishing only in the global space', () => {
    expect(normalizePublishPrefill({ namespace: 'global', visibility: 'PUBLIC' }).visibility).toBe('PUBLIC')
    expect(normalizePublishPrefill({ namespace: 'team-a', visibility: 'PUBLIC' }).visibility).toBe('NAMESPACE_ONLY')
  })
  it('keeps namespace and normalizes visibility for valid route search params', () => {
    expect(normalizePublishPrefill({
      namespace: 'team-ai',
      visibility: 'private',
      resubmitSkill: 'agent-helper',
      resubmitVersion: '1.2.0',
    })).toEqual({
      namespace: 'team-ai',
      visibility: 'PRIVATE',
      resubmitSkill: 'agent-helper',
      resubmitVersion: '1.2.0',
    })
  })

  it('falls back to NAMESPACE_ONLY when visibility is missing or invalid', () => {
    expect(normalizePublishPrefill({
      namespace: 'team-ai',
      visibility: 'internal',
    })).toEqual({
      namespace: 'team-ai',
      visibility: 'NAMESPACE_ONLY',
      resubmitSkill: '',
      resubmitVersion: '',
    })
  })

  it('trims namespace input from search params', () => {
    expect(normalizePublishPrefill({
      namespace: '  team-ml  ',
    })).toEqual({
      namespace: 'team-ml',
      visibility: 'NAMESPACE_ONLY',
      resubmitSkill: '',
      resubmitVersion: '',
    })
  })
})
