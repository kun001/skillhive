import { useState, type FormEvent } from 'react'
import { Link, useNavigate } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import { ArrowRight, BookOpen, Layers3, Search, ShieldCheck } from 'lucide-react'
import type { SkillSummary } from '@/api/types'
import { useSearchSkills } from '@/shared/hooks/use-skill-queries'
import { normalizeSearchQuery } from '@/shared/lib/search-query'
import './landing.css'

function SkillPreviewCard({ skill }: { skill: SkillSummary }) {
  const { t } = useTranslation()
  const version = skill.publishedVersion?.version ?? skill.headlineVersion?.version

  return <Link to="/space/$namespace/$slug" params={{ namespace: skill.namespace.replace(/^@/, ''), slug: skill.slug }} className="hive-skill-card" aria-label={`${skill.displayName} · @${skill.namespace.replace(/^@/, '')}/${skill.slug}`}>
    <span className="hive-skill-card-top"><span className="hive-skill-icon" aria-hidden="true">{skill.displayName.charAt(0).toUpperCase()}</span><ArrowRight size={18} aria-hidden="true" /></span>
    <span className="hive-skill-card-main"><strong>{skill.displayName}</strong><small>@{skill.namespace.replace(/^@/, '')}/{skill.slug}</small><span className="hive-skill-description">{skill.summary || t('hiveLanding.skillNoDescription')}</span></span>
    <span className="hive-skill-meta"><span>{version ? `v${version}` : t('hiveLanding.versionPending')}</span><span>{t('hiveLanding.downloads', { count: skill.downloadCount })}</span></span>
  </Link>
}

export function LandingPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const [query, setQuery] = useState('')
  const [sort, setSort] = useState<'downloads' | 'newest'>('downloads')
  const popular = useSearchSkills({ sort: 'downloads', size: 6 })
  const recent = useSearchSkills({ sort: 'newest', size: 6 })
  const active = sort === 'downloads' ? popular : recent
  const submitSearch = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    const normalized = normalizeSearchQuery(query)
    void navigate({ to: '/search', search: { q: normalized, sort: 'relevance', page: 0, starredOnly: false } })
  }

  return <div className="hive-landing">
    <section className="hive-section hive-hero" id="hero"><div className="hive-container hive-hero-inner">
      <p className="hive-eyebrow">{t('hiveLanding.heroEyebrow')}</p>
      <h1><span>{t('hiveLanding.heroTitleFirst')}</span><span>{t('hiveLanding.heroTitleSecond')}</span></h1>
      <p className="hive-lead">{t('hiveLanding.heroDescription')}</p>
      {popular.data && <p className="hive-trust">{t('hiveLanding.skillTotal', { count: popular.data.total })}</p>}
      <form className="hive-search" role="search" onSubmit={submitSearch}>
        <div className="hive-search-scope"><span>Skill</span></div>
        <label className="hive-search-input" htmlFor="hive-search-input"><Search size={19} aria-hidden="true" /><input id="hive-search-input" type="search" value={query} onChange={(event) => setQuery(event.target.value)} placeholder={t('hiveLanding.searchPlaceholder')} aria-label={t('hiveLanding.searchSkill')} /></label>
        <button className="hive-button hive-button-primary" type="submit">{t('hiveLanding.search')}</button>
      </form>
    </div></section>

    <section className="hive-section hive-capabilities" id="capabilities"><div className="hive-container">
      <div className="hive-section-head"><p className="hive-eyebrow">{t('hiveLanding.capabilityEyebrow')}</p><h2>{t('hiveLanding.capabilityTitle')}</h2></div>
      <div className="hive-feature-grid">{([{ key: 'knowledge', icon: BookOpen }, { key: 'skills', icon: Layers3 }, { key: 'governance', icon: ShieldCheck }] as const).map(({ key, icon: Icon }) => <article className="hive-feature" key={key}><span className="hive-feature-mark"><Icon size={19} strokeWidth={1.6} aria-hidden="true" /></span><h3>{t(`hiveLanding.features.${key}.title`)}</h3><p>{t(`hiveLanding.features.${key}.description`)}</p></article>)}</div>
    </div></section>

    <section className="hive-section hive-knowledge" id="knowledge-base"><div className="hive-container">
      <div className="hive-section-head"><p className="hive-eyebrow">Knowledge Base / {t('hiveLanding.knowledgeLabel')}</p><h2>{t('hiveLanding.knowledgeTitle')}</h2></div>
      <Link to="/knowledge" className="hive-text-link">{t('hiveLanding.knowledgeLabel')} <ArrowRight size={16} aria-hidden="true" /></Link>
    </div></section>

    <section className="hive-section hive-library" id="skill-library"><div className="hive-container">
      <div className="hive-section-head"><p className="hive-eyebrow">Skill Library / {t('hiveLanding.skillLibraryLabel')}</p><h2>{t('hiveLanding.libraryTitle')}</h2></div>
      <div className="hive-library-toolbar"><div className="hive-tabs" role="tablist" aria-label={t('hiveLanding.sortLabel')}><button type="button" role="tab" aria-selected={sort === 'downloads'} onClick={() => setSort('downloads')}>{t('hiveLanding.popular')}</button><button type="button" role="tab" aria-selected={sort === 'newest'} onClick={() => setSort('newest')}>{t('hiveLanding.recent')}</button></div><Link to="/search" search={{ q: '', sort, page: 0, starredOnly: false }} className="hive-text-link">{t('hiveLanding.viewAll')} <ArrowRight size={16} aria-hidden="true" /></Link></div>
      {active.isLoading ? <p className="hive-result-status" role="status">{t('hiveLanding.loading')}</p> : active.isError ? <p className="hive-result-status" role="alert">{t('hiveLanding.loadError')}</p> : active.data?.items.length ? <><p className="hive-result-status">{t('hiveLanding.showing', { count: active.data.items.length })}</p><div className="hive-skill-grid">{active.data.items.map((skill) => <SkillPreviewCard key={skill.id} skill={skill} />)}</div></> : <p className="hive-empty">{t('hiveLanding.skillEmpty')}</p>}
    </div></section>

    <section className="hive-section hive-workflow"><div className="hive-container hive-workflow-inner"><div><p className="hive-eyebrow">{t('hiveLanding.workflowEyebrow')}</p><h2>{t('hiveLanding.workflowTitle')}</h2><p className="hive-lead">{t('hiveLanding.workflowDescription')}</p></div><div className="hive-process-list">{(['upload', 'review', 'download'] as const).map((key, index) => <article className="hive-process-item" key={key}><span>{String(index + 1).padStart(2, '0')}</span><div><h3>{t(`hiveLanding.workflow.${key}.title`)}</h3><p>{t(`hiveLanding.workflow.${key}.description`)}</p></div></article>)}</div></div></section>

    <section className="hive-section hive-open-source" id="open-source"><div className="hive-container"><div className="hive-cta-panel"><h2>{t('hiveLanding.openSourceTitle')}</h2><p>{t('hiveLanding.openSourceDescription')}</p><a href="https://github.com/iflytek/skillhub" target="_blank" rel="noreferrer" className="hive-button hive-button-outline">{t('hiveLanding.viewSource')} <ArrowRight size={16} aria-hidden="true" /></a></div></div></section>
  </div>
}
