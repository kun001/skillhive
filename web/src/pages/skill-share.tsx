import { skipToken, useQuery } from '@tanstack/react-query'
import { useParams } from '@tanstack/react-router'
import { Download } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { skillShareApi } from '@/api/client'
import { MarkdownRenderer } from '@/features/skill/markdown-renderer'
import { EmptyState } from '@/shared/components/empty-state'
import { formatLocalDateTime } from '@/shared/lib/date-time'
import { buttonVariants } from '@/shared/ui/button'

export function SkillSharePage() {
  const { shareId } = useParams({ from: '/skill-share/$shareId' })
  const { t, i18n } = useTranslation()
  const share = useQuery({
    queryKey: ['shared-skill', shareId],
    queryFn: ({ signal }) => skillShareApi.detail(shareId, signal),
    retry: false,
    gcTime: 0,
  })
  const version = share.data?.version
  const documentation = useQuery({
    queryKey: ['shared-skill-documentation', shareId, version],
    queryFn: version ? ({ signal }) => skillShareApi.documentation(shareId, version, signal) : skipToken,
    retry: false,
    gcTime: 0,
  })

  if (share.isPending) return <p role="status">{t('knowledge.preview.loading')}</p>
  if (share.isError || !version) return <EmptyState title={t('skillDetail.share.unavailable')} />

  const { displayName, summary, expiresAt } = share.data
  return (
    <main className="mx-auto max-w-4xl space-y-6">
      <header className="flex flex-wrap items-start justify-between gap-4">
        <div className="min-w-0 space-y-2">
          <div className="flex flex-wrap items-baseline gap-3">
            <h1 className="break-words font-heading text-3xl font-semibold">{displayName}</h1>
            <span className="text-sm text-muted-foreground">v{version}</span>
          </div>
          {summary ? <p className="text-muted-foreground">{summary}</p> : null}
          {expiresAt ? (
            <p className="text-sm text-muted-foreground">
              {t('knowledge.share.validUntil', { time: formatLocalDateTime(expiresAt, i18n.language, { dateStyle: 'medium', timeStyle: 'short' }) })}
            </p>
          ) : null}
        </div>
        <a className={buttonVariants()} href={skillShareApi.downloadUrl(shareId)} download rel="noreferrer">
          <Download className="mr-2 h-4 w-4" aria-hidden />
          {t('skillDetail.share.download')}
        </a>
      </header>
      <section className="rounded-xl border border-border bg-card p-5 sm:p-8">
        {documentation.isPending ? <p role="status">{t('knowledge.preview.loading')}</p> : null}
        {documentation.isError ? <p>{t('skillDetail.share.previewUnavailable')}</p> : null}
        {documentation.isSuccess ? <MarkdownRenderer content={documentation.data} /> : null}
      </section>
    </main>
  )
}
