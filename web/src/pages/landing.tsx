import { getSkillSummaryDescription } from '@/features/skill/skill-summary-description'
import { useState } from 'react'
import { Link } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import { ArrowRight, BookOpen, Download, Eye, FileText, FolderOpen, History, Layers3, Search, ShieldCheck } from 'lucide-react'
import type { SkillSummary } from '@/api/types'
import { useSearchSkills } from '@/shared/hooks/use-skill-queries'
import './landing.css'

function SkillPreviewCard({ skill }: { skill: SkillSummary }) {
  const { t, i18n } = useTranslation()
  const version = skill.publishedVersion?.version ?? skill.headlineVersion?.version

  return <Link to="/space/$namespace/$slug" params={{ namespace: skill.namespace.replace(/^@/, ''), slug: skill.slug }} className="hive-skill-card" aria-label={`${skill.displayName} · @${skill.namespace.replace(/^@/, '')}/${skill.slug}`}>
    <span className="hive-skill-card-top"><span className="hive-skill-icon" aria-hidden="true">{skill.displayName.charAt(0).toUpperCase()}</span><ArrowRight size={18} aria-hidden="true" /></span>
    <span className="hive-skill-card-main"><strong>{skill.displayName}</strong><small>@{skill.namespace.replace(/^@/, '')}/{skill.slug}</small><span className="hive-skill-description">{getSkillSummaryDescription(skill, i18n.resolvedLanguage || i18n.language) || t('hiveLanding.skillNoDescription')}</span></span>
    <span className="hive-skill-meta"><span>{version ? `v${version}` : t('hiveLanding.versionPending')}</span><span>{t('hiveLanding.downloads', { count: skill.downloadCount })}</span></span>
  </Link>
}

export function LandingPage() {
  const { t } = useTranslation()
  const [sort, setSort] = useState<'downloads' | 'newest'>('downloads')
  const popular = useSearchSkills({ sort: 'downloads', size: 6 })
  const recent = useSearchSkills({ sort: 'newest', size: 6 })
  const active = sort === 'downloads' ? popular : recent

  return <div className="hive-landing">
    <section className="hive-section hive-hero" id="hero"><div className="hive-container hive-hero-inner">
      <p className="hive-eyebrow">{t('hiveLanding.heroEyebrow')}</p>
      <h1><span>{t('hiveLanding.heroTitleFirst')}</span><span>{t('hiveLanding.heroTitleSecond')}</span></h1>
      <p className="hive-lead">{t('hiveLanding.heroDescription')}</p>
    </div></section>

    <section className="hive-section hive-capabilities" id="capabilities"><div className="hive-container">
      <div className="hive-section-head"><p className="hive-eyebrow">{t('hiveLanding.capabilityEyebrow')}</p><h2>{t('hiveLanding.capabilityTitle')}</h2></div>
      <div className="hive-feature-grid">{([{ key: 'knowledge', icon: BookOpen }, { key: 'skills', icon: Layers3 }, { key: 'governance', icon: ShieldCheck }] as const).map(({ key, icon: Icon }) => <article className="hive-feature" key={key}><span className="hive-feature-mark"><Icon size={19} strokeWidth={1.6} aria-hidden="true" /></span><h3>{t(`hiveLanding.features.${key}.title`)}</h3><p>{t(`hiveLanding.features.${key}.description`)}</p></article>)}</div>
    </div></section>

    <section className="hive-section hive-knowledge" id="knowledge-base"><div className="hive-container">
      <div className="hive-section-head hive-knowledge-heading">
        <p className="hive-eyebrow">Knowledge Base / {t('hiveLanding.knowledgeLabel')}</p>
        <h2>{t('hiveLanding.knowledgeTitle')}</h2>
        <p>{t('hiveLanding.knowledgeSubtitle')}</p>
      </div>
      <div className="hive-knowledge-showcase">
        <div className="hive-knowledge-copy">
          <p className="hive-knowledge-kicker">{t('hiveLanding.knowledgeKicker')}</p>
          <h3>{t('hiveLanding.knowledgeShowcaseTitle')}</h3>
          <p className="hive-knowledge-description">{t('hiveLanding.knowledgeDescription')}</p>
          <div className="hive-knowledge-benefits">
            {([{ key: 'organize', icon: FolderOpen }, { key: 'preview', icon: Eye }, { key: 'version', icon: History }] as const).map(({ key, icon: Icon }) => (
              <div className="hive-knowledge-benefit" key={key}>
                <span className="hive-knowledge-benefit-icon"><Icon size={18} strokeWidth={1.7} aria-hidden="true" /></span>
                <div><strong>{t(`hiveLanding.knowledgeBenefits.${key}.title`)}</strong><span>{t(`hiveLanding.knowledgeBenefits.${key}.description`)}</span></div>
              </div>
            ))}
          </div>
          <Link to="/knowledge" className="hive-knowledge-cta">{t('hiveLanding.knowledgeCta')} <ArrowRight size={17} aria-hidden="true" /></Link>
        </div>
        <div className="hive-knowledge-window" aria-label={t('hiveLanding.exampleStructure')}>
          <div className="hive-knowledge-window-head"><strong>{t('hiveLanding.knowledgePreview.title')}</strong><span>{t('hiveLanding.exampleStructure')}</span></div>
          <div className="hive-knowledge-window-body">
            <div className="hive-knowledge-path"><FolderOpen size={15} aria-hidden="true" />{t('hiveLanding.knowledgePreview.path')}</div>
            <div className="hive-knowledge-search"><Search size={15} aria-hidden="true" />{t('hiveLanding.knowledgePreview.search')}</div>
            <div className="hive-knowledge-list-head"><span>{t('hiveLanding.knowledgePreview.files')}</span><span>{t('hiveLanding.knowledgePreview.type')}</span></div>
            <div className="hive-knowledge-list">
              {(['guide', 'review', 'faq'] as const).map((key) => (
                <div className="hive-knowledge-item" key={key}>
                  <span className="hive-knowledge-icon"><FileText size={17} strokeWidth={1.7} aria-hidden="true" /></span>
                  <span className="hive-knowledge-item-copy"><strong>{t(`hiveLanding.knowledge.${key}`)}</strong><small>{t(`hiveLanding.knowledge.${key}Detail`)}</small></span>
                  <span className="hive-knowledge-file-type">{t('hiveLanding.knowledgePreview.document')}</span>
                </div>
              ))}
            </div>
            <div className="hive-knowledge-window-foot"><span><Eye size={14} aria-hidden="true" />{t('hiveLanding.knowledgePreview.preview')}</span><span><Download size={14} aria-hidden="true" />{t('hiveLanding.knowledgePreview.download')}</span><span><History size={14} aria-hidden="true" />{t('hiveLanding.knowledgePreview.history')}</span></div>
          </div>
        </div>
      </div>
    </div></section>

    <section className="hive-section hive-library" id="skill-library"><div className="hive-container">
      <div className="hive-section-head"><p className="hive-eyebrow">Skill Library / {t('hiveLanding.skillLibraryLabel')}</p><h2>{t('hiveLanding.libraryTitle')}</h2></div>
      <div className="hive-library-toolbar"><div className="hive-tabs" role="tablist" aria-label={t('hiveLanding.sortLabel')}><button type="button" role="tab" aria-selected={sort === 'downloads'} onClick={() => setSort('downloads')}>{t('hiveLanding.popular')}</button><button type="button" role="tab" aria-selected={sort === 'newest'} onClick={() => setSort('newest')}>{t('hiveLanding.recent')}</button></div><Link to="/skills" search={{ q: '', sort, page: 0, view: 'list' }} className="hive-text-link">{t('hiveLanding.viewAll')} <ArrowRight size={16} aria-hidden="true" /></Link></div>
      {active.isLoading ? <p className="hive-result-status" role="status">{t('hiveLanding.loading')}</p> : active.isError ? <p className="hive-result-status" role="alert">{t('hiveLanding.loadError')}</p> : active.data?.items.length ? <><p className="hive-result-status">{t('hiveLanding.showing', { count: active.data.items.length })}</p><div className="hive-skill-grid">{active.data.items.map((skill) => <SkillPreviewCard key={skill.id} skill={skill} />)}</div></> : <p className="hive-empty">{t('hiveLanding.skillEmpty')}</p>}
    </div></section>

    <section className="hive-section hive-workflow"><div className="hive-container hive-workflow-inner"><div><p className="hive-eyebrow">{t('hiveLanding.workflowEyebrow')}</p><h2>{t('hiveLanding.workflowTitle')}</h2><p className="hive-lead">{t('hiveLanding.workflowDescription')}</p></div><div className="hive-process-list">{(['upload', 'review', 'download'] as const).map((key, index) => <article className="hive-process-item" key={key}><span>{String(index + 1).padStart(2, '0')}</span><div><h3>{t(`hiveLanding.workflow.${key}.title`)}</h3><p>{t(`hiveLanding.workflow.${key}.description`)}</p></div></article>)}</div></div></section>

    <section className="hive-section hive-open-source" id="open-source"><div className="hive-container"><div className="hive-cta-panel"><h2>{t('hiveLanding.openSourceTitle')}</h2><p>{t('hiveLanding.openSourceDescription')}</p><a href="https://github.com/kun001/skillhive" target="_blank" rel="noreferrer" className="hive-button hive-button-outline">{t('hiveLanding.viewSource')} <ArrowRight size={16} aria-hidden="true" /></a></div></div></section>
  </div>
}
