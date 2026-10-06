import { useTranslation } from 'react-i18next'
import { ArrowUpRight, History } from 'lucide-react'
import { releases } from '@/features/changelog/releases'

export function ChangelogPage() {
  const { t, i18n } = useTranslation()
  const language = (i18n.resolvedLanguage ?? i18n.language).startsWith('zh') ? 'zh' : 'en'
  const dateFormat = new Intl.DateTimeFormat(language === 'zh' ? 'zh-CN' : 'en-US', { dateStyle: 'long', timeZone: 'UTC' })

  return (
    <div className="mx-auto w-full max-w-[1040px]">
      <header className="mb-10 max-w-2xl">
        <p className="mb-3 flex items-center gap-2 text-sm font-medium text-primary"><History className="h-4 w-4" aria-hidden="true" />{t('changelog.eyebrow')}</p>
        <h1 className="font-heading text-3xl font-semibold tracking-tight sm:text-4xl">{t('changelog.title')}</h1>
      </header>

      <div className="space-y-10">
        {releases.map((release) => (
          <article key={release.id} id={release.id} aria-labelledby={`${release.id}-title`} className="grid scroll-mt-28 gap-4 md:grid-cols-[190px_1fr] md:gap-8">
            <div className="flex flex-wrap items-center gap-3 text-sm md:block md:pt-6">
              <time dateTime={release.date} className="font-medium text-foreground">
                {release.dateEnd ? dateFormat.formatRange(new Date(`${release.date}T00:00:00Z`), new Date(`${release.dateEnd}T00:00:00Z`)) : dateFormat.format(new Date(`${release.date}T00:00:00Z`))}
              </time>
            </div>
            <div className="min-w-0 rounded-2xl border border-border bg-card p-5 sm:p-7">
              <div className="mb-4 flex flex-wrap items-center gap-2">
                <span className="break-all rounded-md bg-secondary px-2.5 py-1 font-mono text-xs text-secondary-foreground">{release.version}</span>
              </div>
              <h2 id={`${release.id}-title`} className="text-xl font-semibold tracking-tight sm:text-2xl">
                <a href={`#${release.id}`} className="group inline-flex items-start gap-2 rounded-sm hover:text-primary focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring">
                  {release.title[language]}<ArrowUpRight className="mt-1 h-4 w-4 shrink-0 text-muted-foreground group-hover:text-primary" aria-hidden="true" />
                </a>
              </h2>
              <p className="mt-3 text-sm leading-7 text-muted-foreground">{release.summary[language]}</p>
              <div className="mt-6 border-t border-border pt-5">
                <ul className="list-disc space-y-2 pl-5 text-sm leading-7 text-foreground marker:text-muted-foreground">
                  {release.changes.map((change) => <li key={change.en}>{change[language]}</li>)}
                </ul>
              </div>
            </div>
          </article>
        ))}
        {releases.length === 0 && <p className="py-12 text-center text-muted-foreground">{t('changelog.empty')}</p>}
      </div>
    </div>
  )
}
