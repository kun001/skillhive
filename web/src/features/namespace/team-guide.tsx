import { useState } from 'react'
import { Pencil } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { MarkdownRenderer } from '@/features/skill/markdown-renderer'
import { EmptyState } from '@/shared/components/empty-state'
import { useNamespaceGuide, useUpdateNamespaceGuide } from '@/shared/hooks/use-namespace-queries'
import { formatLocalDateTime } from '@/shared/lib/date-time'
import { toast } from '@/shared/lib/toast'
import { Button } from '@/shared/ui/button'
import { Textarea } from '@/shared/ui/textarea'

/** Mirrors the server limit in NamespaceGuide.MAX_LENGTH. */
export const TEAM_GUIDE_MAX_LENGTH = 20_000

/**
 * The team guide tab: members read it rendered as Markdown; owners and admins edit it with a
 * live preview. Agents read the same text through the MCP get_team_guide tool.
 */
export function TeamGuidePanel({ namespace }: { namespace: string }) {
  const { t, i18n } = useTranslation()
  const guide = useNamespaceGuide(namespace)
  const update = useUpdateNamespaceGuide(namespace)
  const [draft, setDraft] = useState<string | null>(null)

  if (guide.isLoading) {
    return <div className="h-64 animate-shimmer rounded-xl" />
  }
  if (guide.isError || !guide.data) {
    return <EmptyState title={t('namespace.guideLoadFailed')} />
  }

  const { content, canEdit, updatedBy, updatedAt } = guide.data
  const editing = draft !== null
  const save = async () => {
    try {
      await update.mutateAsync(draft ?? '')
      toast.success(t('namespace.guideSaved'))
      setDraft(null)
    } catch {
      toast.error(t('namespace.guideSaveFailed'))
    }
  }

  if (editing) {
    return (
      <div className="space-y-4">
        <div className="grid grid-cols-1 gap-4 lg:grid-cols-2">
          <Textarea
            value={draft}
            onChange={(event) => setDraft(event.target.value)}
            placeholder={t('namespace.guidePlaceholder')}
            aria-label={t('namespace.tabGuide')}
            maxLength={TEAM_GUIDE_MAX_LENGTH}
            className="min-h-[28rem] font-mono text-[13px] leading-6"
            autoFocus
          />
          <div className="min-h-[28rem] overflow-auto rounded-lg border border-border/60 bg-card p-5" aria-label={t('namespace.guidePreview')}>
            <p className="mb-3 text-xs font-medium uppercase tracking-wide text-muted-foreground">{t('namespace.guidePreview')}</p>
            {draft.trim() ? <MarkdownRenderer content={draft} /> : <p className="text-sm text-muted-foreground">{t('namespace.guideEmptyTitle')}</p>}
          </div>
        </div>
        <div className="flex flex-wrap items-center justify-between gap-3">
          <p className="text-xs text-muted-foreground">
            {t('namespace.guideCharCount', { count: draft.length, max: TEAM_GUIDE_MAX_LENGTH })} · {t('namespace.guideAgentHint')}
          </p>
          <div className="flex gap-2">
            <Button variant="outline" onClick={() => setDraft(null)} disabled={update.isPending}>{t('namespace.guideCancel')}</Button>
            <Button onClick={save} disabled={update.isPending}>{t('namespace.guideSave')}</Button>
          </div>
        </div>
      </div>
    )
  }

  if (!content.trim()) {
    return (
      <div className="rounded-xl border border-dashed border-border/70 py-6">
        <EmptyState
          title={t('namespace.guideEmptyTitle')}
          description={canEdit ? t('namespace.guideEmptyDescription') : t('namespace.guideEmptyReadonly')}
          action={canEdit ? <Button onClick={() => setDraft('')}>{t('namespace.guideWrite')}</Button> : undefined}
        />
      </div>
    )
  }

  return (
    <article className="rounded-xl border border-border/60 bg-card">
      <header className="flex flex-wrap items-center justify-between gap-3 border-b border-border/50 px-5 py-3">
        <p className="text-xs text-muted-foreground">
          {updatedBy && updatedAt
            ? t('namespace.guideUpdated', { name: updatedBy, time: formatLocalDateTime(updatedAt, i18n.language, { dateStyle: 'medium', timeStyle: 'short' }) })
            : null}
          {' '}{t('namespace.guideAgentHint')}
        </p>
        {canEdit ? (
          <Button variant="outline" size="sm" onClick={() => setDraft(content)}>
            <Pencil className="mr-1.5 h-3.5 w-3.5" aria-hidden />
            {t('namespace.guideEdit')}
          </Button>
        ) : null}
      </header>
      <div className="p-5 sm:p-6">
        <MarkdownRenderer content={content} />
      </div>
    </article>
  )
}
