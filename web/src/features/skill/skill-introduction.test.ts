import { describe, expect, it } from 'vitest'
import { getSkillIntroduction } from './skill-introduction'

describe('generated skill introductions', () => {
  const introduction = {
    status: 'COMPLETED',
    zh: { functionDescription: '功能', usageInstructions: '使用方法' },
    en: { functionDescription: 'Function', usageInstructions: 'Usage' },
  }
  it('uses the selected language for generated copy', () => {
    expect(getSkillIntroduction(introduction, 'zh-CN')?.functionDescription).toBe('功能')
    expect(getSkillIntroduction(introduction, 'en-US')?.functionDescription).toBe('Function')
  })
  it('retains the original description until a valid result is available', () => {
    expect(getSkillIntroduction(undefined, 'zh')).toBeUndefined()
    expect(getSkillIntroduction({ ...introduction, status: 'RUNNING' }, 'zh')).toBeUndefined()
    expect(getSkillIntroduction({ ...introduction, status: 'FAILED' }, 'zh')).toBeUndefined()
    expect(getSkillIntroduction({ ...introduction, zh: {} }, 'zh')).toBeUndefined()
  })
})
