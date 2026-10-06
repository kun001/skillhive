import { BookOpen, Users } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import type { KnowledgeBase } from '@/api/knowledge-types'
import { formatLocalDateTime } from '@/shared/lib/date-time'

interface KnowledgeBaseCardProps {
  base: KnowledgeBase
  onOpen: (base: KnowledgeBase) => void
  /** Shows which team space owns the knowledge base; off where the space is already obvious. */
  showNamespace?: boolean
}

export function KnowledgeBaseCard({ base, onOpen, showNamespace = true }: KnowledgeBaseCardProps) {
  const { t, i18n } = useTranslation()
  return (
    <button
      type="button"
      onClick={() => onOpen(base)}
      className="group flex min-w-0 flex-col rounded-xl border border-border/60 bg-card p-5 text-left transition-all hover:-translate-y-0.5 hover:border-primary/30 hover:shadow-md focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
    >
      <div className="flex items-center gap-3">
        <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-primary/10 text-primary">
          <BookOpen className="h-5 w-5" aria-hidden />
        </span>
        <span className="min-w-0 flex-1 truncate font-semibold text-foreground">{base.displayName}</span>
      </div>

      <p className="mt-3 line-clamp-2 min-h-[2.5rem] text-sm text-muted-foreground">{base.description || ' '}</p>
      <div className="mt-4 flex flex-wrap items-center gap-x-2 gap-y-1.5 text-xs text-muted-foreground">
        {showNamespace ? (
          <span
            className="inline-flex max-w-full items-center gap-1 rounded-full bg-secondary px-2 py-0.5 font-medium text-secondary-foreground"
            title={`@${base.namespace}`}
          >
            <Users className="h-3 w-3 shrink-0" aria-hidden />
            <span className="truncate">{base.namespaceDisplayName}</span>
          </span>
        ) : null}
        <span>
          {t('knowledge.fileCount', { count: base.documentCount })}
          {' · '}
          {t('knowledge.updatedAt', { time: formatLocalDateTime(base.updatedAt, i18n.language, { dateStyle: 'medium' }) })}
        </span>
      </div>
    </button>
  )
}
