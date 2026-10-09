import { describe, expect, it } from 'vitest'
import { getSkillSummaryDescription } from './skill-summary-description'

describe('localized list descriptions', () => {
  const skill = { summary: 'Source description', functionDescription: { zh: '生成功能描述', en: 'Generated function description' } }
  it('uses the selected language instead of the source description', () => {
    expect(getSkillSummaryDescription(skill, 'zh-CN')).toBe('生成功能描述')
    expect(getSkillSummaryDescription(skill, 'en')).toBe('Generated function description')
  })
  it('keeps the source when generation or that language is missing', () => {
    expect(getSkillSummaryDescription({ summary: 'Source' }, 'zh')).toBe('Source')
    expect(getSkillSummaryDescription({ summary: 'Source', functionDescription: { zh: ' ', en: 'English' } }, 'zh')).toBe('Source')
    expect(getSkillSummaryDescription({}, 'en')).toBeUndefined()
  })
})
