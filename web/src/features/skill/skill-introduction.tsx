import { useTranslation } from 'react-i18next'
import type { components } from '@/api/generated/schema'

export interface IntroductionCopy {
  functionDescription: string
  usageInstructions: string
}

type GeneratedIntroduction = components['schemas']['SkillIntroductionResponse']

export function getSkillIntroduction(introduction: GeneratedIntroduction | undefined, language: string): IntroductionCopy | undefined {
  if (introduction?.status !== 'COMPLETED') return undefined
  const copy = language.startsWith('zh') ? introduction.zh : introduction.en
  if (!copy?.functionDescription?.trim() || !copy.usageInstructions?.trim()) return undefined
  return { functionDescription: copy.functionDescription, usageInstructions: copy.usageInstructions }
}

export function SkillIntroduction({ copy }: { copy: IntroductionCopy }) {
  const { t } = useTranslation()
  return (
    <dl className="pt-1 text-base leading-7">
      <div>
        <dt className="mb-1 font-semibold text-foreground">{t('skillDetail.usageInstructions')}</dt>
        <dd className="space-y-3 text-muted-foreground">
          {copy.usageInstructions.trim().split(/\n\s*\n/).map((paragraph, index) => (
            <p key={index} className="whitespace-pre-wrap">{paragraph}</p>
          ))}
        </dd>
      </div>
    </dl>
  )
}
