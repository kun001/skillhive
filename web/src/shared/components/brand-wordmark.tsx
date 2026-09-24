import { withBasePath } from '@/shared/lib/base-path'

/** Cropped wordmark from the supplied SkillHive brand reference. */
export function BrandWordmark() {
  return <svg className="hive-wordmark" viewBox="185 230 1180 315" preserveAspectRatio="xMidYMid meet" role="img" aria-label="SkillHive">
    <image href={withBasePath('/skillhive-logo-reference.png')} width="1536" height="1024" />
  </svg>
}
