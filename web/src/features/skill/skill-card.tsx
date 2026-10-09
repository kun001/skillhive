import type { SkillSummary } from '@/api/types'
import { useTranslation } from 'react-i18next'
import { useAuth } from '@/features/auth/use-auth'
import { useStarredIdSet } from '@/features/social/use-star'
import { Card } from '@/shared/ui/card'
import { getHeadlineVersion } from '@/shared/lib/skill-lifecycle'
import { formatCompactCount } from '@/shared/lib/number-format'
import { formatRelativeTime } from '@/shared/lib/format-relative-time'
import { ArrowRight, Bookmark, ShieldCheck, User, Clock } from 'lucide-react'

interface SkillCardProps {
  skill: SkillSummary
  onClick?: () => void
  highlightStarred?: boolean
}

/**
 * Reusable card for displaying one skill in lists such as landing, namespace, search, and stars.
 */
export function SkillCard({ skill, onClick, highlightStarred = true }: SkillCardProps) {
  const { t, i18n } = useTranslation()
  const { isAuthenticated } = useAuth()
  // Batch highlight via shared ['skills','stars'] — never N× useStar per grid row.
  const { starredIds } = useStarredIdSet(highlightStarred && isAuthenticated)
  const showStarredHighlight = highlightStarred && isAuthenticated && starredIds.has(skill.id)
  const headlineVersion = getHeadlineVersion(skill)
  const isInteractive = typeof onClick === 'function'
  const complianceItems = skill.complianceSnapshot?.items?.filter((item) => item.standard || item.controlId) ?? []
  const downloadLabel = t('skillCard.downloads', { value: formatCompactCount(skill.downloadCount) })
  const starLabel = t('skillCard.stars', { count: skill.starCount })

  return (
    <Card
      className="group relative h-full min-h-[224px] cursor-pointer overflow-hidden rounded-3xl border border-border bg-card p-6 text-card-foreground transition-[box-shadow,border-color] duration-150 ease-out hover:border-foreground/20 hover:shadow-[0_12px_28px_rgb(48_37_30/0.07)] focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-ring/20"
      onClick={onClick}
      onKeyDown={(event) => {
        if (!isInteractive) {
          return
        }

        if (event.key === 'Enter' || event.key === ' ') {
          event.preventDefault()
          onClick()
        }
      }}
      role={isInteractive ? 'link' : undefined}
      tabIndex={isInteractive ? 0 : undefined}
    >
      <div className="flex h-full flex-col">
        <div className="mb-3 flex items-start justify-between">
          <span className="flex h-9 w-9 items-center justify-center rounded-xl bg-secondary text-base font-semibold text-foreground" aria-hidden="true">{skill.displayName.charAt(0).toUpperCase()}</span>
          <ArrowRight className="h-5 w-5 text-muted-foreground transition-colors group-hover:text-foreground" aria-hidden="true" />
        </div>
        <h3 className="font-sans text-lg font-semibold text-foreground [overflow-wrap:anywhere]">{skill.displayName}</h3>
        <p className="mb-2 text-xs text-muted-foreground">@{skill.namespace.replace(/^@/, '')}/{skill.slug}</p>

        {skill.summary && (
          <p
            className="skill-card-summary mb-4 text-sm leading-[1.45] text-muted-foreground [overflow-wrap:anywhere]"
            title={skill.summary}
          >
            {skill.summary}
          </p>
        )}

        {complianceItems.length > 0 ? (
          <div className="mb-4 flex flex-wrap gap-1.5">
            {complianceItems.slice(0, 2).map((item, index) => (
              <span
                key={`${item.standard ?? 'standard'}-${item.controlId ?? index}`}
                className="inline-flex items-center gap-1 rounded-full border border-emerald-500/20 bg-emerald-500/10 px-2 py-0.5 text-xs font-medium text-emerald-700 dark:text-emerald-300"
                title={item.title}
              >
                <ShieldCheck className="h-3 w-3" />
                {[item.standard, item.controlId].filter(Boolean).join(' · ')}
              </span>
            ))}
            {complianceItems.length > 2 ? (
              <span className="rounded-full bg-secondary px-2 py-0.5 text-xs text-secondary-foreground">
                +{complianceItems.length - 2}
              </span>
            ) : null}
          </div>
        ) : null}

        <div className="mt-auto flex items-center gap-4 text-xs text-muted-foreground">
          {headlineVersion && (
            <span className="font-mono text-xs text-muted-foreground">
              v{headlineVersion.version}
            </span>
          )}
          <span className="flex items-center gap-1 text-muted-foreground" title={downloadLabel} aria-label={downloadLabel}>
            <svg className="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M7 16a4 4 0 01-.88-7.903A5 5 0 1115.9 6L16 6a5 5 0 011 9.9M9 19l3 3m0 0l3-3m-3 3V10" />
            </svg>
            {formatCompactCount(skill.downloadCount)}
          </span>
          <span
            className={`flex items-center gap-1 ${showStarredHighlight ? 'font-semibold text-amber-600 dark:text-amber-400' : 'text-muted-foreground'}`}
            title={starLabel}
            aria-label={starLabel}
          >
            <Bookmark className={`w-3.5 h-3.5 ${showStarredHighlight ? 'fill-current' : ''}`} />
            {skill.starCount}
          </span>
        </div>
        {(skill.ownerDisplayName || skill.updatedAt) && (
          <div className="mt-2 pt-2 border-t border-border/50 flex items-center gap-3 text-xs text-muted-foreground">
            {skill.ownerDisplayName && (
              <span className="flex items-center gap-1">
                <User className="w-3 h-3" />
                {skill.ownerDisplayName}
              </span>
            )}
            {skill.updatedAt && (
              <span className="flex items-center gap-1">
                <Clock className="w-3 h-3" />
                {formatRelativeTime(skill.updatedAt, i18n.language)}
              </span>
            )}
          </div>
        )}
      </div>
    </Card>
  )
}
