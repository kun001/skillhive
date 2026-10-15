import { useMemo, useRef, useState, type CSSProperties } from 'react'
import { Link } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import { ArrowRight, ArrowUpRight, Check, Copy, FileText, FolderOpen, GitBranch, History, KeyRound, Lock, Minus, RefreshCw, ScanSearch, Server, ShieldCheck, UserCheck } from 'lucide-react'
import type { SkillSummary } from '@/api/types'
import { getSkillSummaryDescription } from '@/features/skill/skill-summary-description'
import { AgentConsole } from '@/features/landing/agent-console'
import { ContextFlow, HiveMark } from '@/features/landing/context-flow'
import { usePrefersReducedMotion, useScrollReveal } from '@/features/landing/use-landing-motion'
import type { hiveLandingZh } from '@/i18n/hive-landing'
import { withBasePath } from '@/shared/lib/base-path'
import { useCopyToClipboard } from '@/shared/lib/clipboard'
import { useSearchSkills } from '@/shared/hooks/use-skill-queries'
import './landing.css'

type LandingCopy = typeof hiveLandingZh

const SOURCE_URL = 'https://github.com/kun001/skillhive'
const GOVERNANCE_ICONS = { scope: KeyRound, recheck: RefreshCw, noBypass: UserCheck, audit: History, safeWrite: Lock, review: ScanSearch } as const

const revealDelay = (ms: number) => ({ '--d': `${ms}ms` }) as CSSProperties

function useLandingCopy() {
  const { t } = useTranslation()
  return t('hiveLanding', { returnObjects: true }) as unknown as LandingCopy
}

function HexField({ id }: { id: string }) {
  return <svg className="hv-hex" aria-hidden="true">
    <defs>
      <pattern id={id} width="56" height="97" patternUnits="userSpaceOnUse" patternTransform="scale(.9)">
        <path d="M28 0 56 16.2v32.4L28 64.8 0 48.6V16.2ZM28 64.8v32.4" fill="none" stroke="currentColor" strokeWidth="1" />
      </pattern>
    </defs>
    <rect width="100%" height="100%" fill={`url(#${id})`} />
  </svg>
}

function SectionHead({ eyebrow, title, description, align = 'start' }: { eyebrow: string; title: string; description?: string; align?: 'start' | 'center' }) {
  return <div className={`hv-head is-${align}`} data-reveal>
    <p className="hv-eyebrow">{eyebrow}</p>
    <h2>{title}</h2>
    {description ? <p className="hv-lead">{description}</p> : null}
  </div>
}

function SkillPreviewCard({ skill, copy }: { skill: SkillSummary; copy: LandingCopy['library'] }) {
  const { t, i18n } = useTranslation()
  const namespace = skill.namespace.replace(/^@/, '')
  const version = skill.publishedVersion?.version ?? skill.headlineVersion?.version

  return <Link to="/space/$namespace/$slug" params={{ namespace, slug: skill.slug }} className="hv-skill" aria-label={`${skill.displayName} · @${namespace}/${skill.slug}`}>
    <span className="hv-skill-top">
      <span className="hv-skill-glyph" aria-hidden="true">{skill.displayName.charAt(0).toUpperCase()}</span>
      <ArrowUpRight size={18} aria-hidden="true" className="hv-skill-arrow" />
    </span>
    <strong>{skill.displayName}</strong>
    <code>@{namespace}/{skill.slug}</code>
    <span className="hv-skill-desc">{getSkillSummaryDescription(skill, i18n.resolvedLanguage || i18n.language) || copy.noDescription}</span>
    <span className="hv-skill-meta">
      <span>{version ? `v${version}` : copy.versionPending}</span>
      {skill.downloadCount > 0 ? <span>{t('hiveLanding.library.downloads', { count: skill.downloadCount })}</span> : null}
    </span>
  </Link>
}

function SkillLibrary({ copy }: { copy: LandingCopy['library'] }) {
  const [sort, setSort] = useState<'downloads' | 'newest'>('downloads')
  const popular = useSearchSkills({ sort: 'downloads', size: 6 })
  const recent = useSearchSkills({ sort: 'newest', size: 6 })
  const active = sort === 'downloads' ? popular : recent
  const items = active.data?.items ?? []

  return <>
    <div className="hv-library-bar" data-reveal>
      <div className="hv-segment" role="tablist" aria-label={copy.sortLabel}>
        <button type="button" role="tab" aria-selected={sort === 'downloads'} onClick={() => setSort('downloads')}>{copy.popular}</button>
        <button type="button" role="tab" aria-selected={sort === 'newest'} onClick={() => setSort('newest')}>{copy.recent}</button>
      </div>
      <Link to="/skills" search={{ q: '', sort, page: 0, view: 'list' }} className="hv-text-link">{copy.viewAll} <ArrowRight size={16} aria-hidden="true" /></Link>
    </div>
    {active.isLoading ? <p className="hv-status" role="status">{copy.loading}</p>
      : active.isError ? <p className="hv-status" role="alert">{copy.loadError}</p>
        : items.length ? <div className="hv-skill-grid">{items.map((skill) => <SkillPreviewCard key={skill.id} skill={skill} copy={copy} />)}</div>
          : <p className="hv-status is-empty">{copy.empty}</p>}
  </>
}

function McpConfig({ copy }: { copy: LandingCopy['connect'] }) {
  const [copied, copyText] = useCopyToClipboard()
  const endpoint = useMemo(() => typeof window === 'undefined' ? '/api/v1/mcp' : new URL(withBasePath('/api/v1/mcp'), window.location.origin).toString(), [])
  const config = JSON.stringify({ mcpServers: { skillhive: { type: 'http', url: endpoint, headers: { Authorization: `Bearer ${copy.tokenPlaceholder}` } } } }, null, 2)

  return <div className="hv-code" data-reveal>
    <div className="hv-code-bar">
      <span className="hv-console-dots" aria-hidden="true"><i /><i /><i /></span>
      <span>{copy.codeTitle}</span>
      <button type="button" onClick={() => { void copyText(config) }} aria-live="polite">
        {copied ? <Check size={14} aria-hidden="true" /> : <Copy size={14} aria-hidden="true" />}{copied ? copy.copied : copy.copy}
      </button>
    </div>
    <pre><code>
      <span className="t-p">{'{'}</span>{'\n'}
      {'  '}<span className="t-k">"mcpServers"</span>: <span className="t-p">{'{'}</span>{'\n'}
      {'    '}<span className="t-k">"skillhive"</span>: <span className="t-p">{'{'}</span>{'\n'}
      {'      '}<span className="t-k">"type"</span>: <span className="t-s">"http"</span>,{'\n'}
      {'      '}<span className="t-k">"url"</span>: <span className="t-s">"{endpoint}"</span>,{'\n'}
      {'      '}<span className="t-k">"headers"</span>: <span className="t-p">{'{'}</span>{'\n'}
      {'        '}<span className="t-k">"Authorization"</span>: <span className="t-s">"Bearer <mark>{copy.tokenPlaceholder}</mark>"</span>{'\n'}
      {'      '}<span className="t-p">{'}'}</span>{'\n'}
      {'    '}<span className="t-p">{'}'}</span>{'\n'}
      {'  '}<span className="t-p">{'}'}</span>{'\n'}
      <span className="t-p">{'}'}</span>
    </code></pre>
  </div>
}

function LayerVisual({ kind, copy }: { kind: 'skills' | 'knowledge' | 'guardrails'; copy: LandingCopy['layers'] }) {
  if (kind === 'skills') {
    const sample = copy.skillSample
    return <div className="hv-layer-visual is-skill" aria-hidden="true">
      <div className="hv-file-tab"><FileText size={13} />{sample.file}<span className="hv-chip is-ok"><Check size={11} strokeWidth={3} />{sample.badges.scanned}</span><span className="hv-chip is-ok"><Check size={11} strokeWidth={3} />{sample.badges.reviewed}</span></div>
      <pre><span className="t-p">---</span>{'\n'}<span className="t-k">name</span>: <span className="t-s">{sample.name}</span>{'\n'}<span className="t-k">description</span>: <span className="t-s">{sample.description}</span>{'\n'}<span className="t-p">---</span>{'\n'}<span className="t-h">{sample.heading}</span>{'\n'}{sample.body.join('\n')}</pre>
    </div>
  }
  if (kind === 'knowledge') {
    const sample = copy.knowledgeSample
    return <div className="hv-layer-visual is-tree" aria-hidden="true">
      <div className="hv-tree-root"><FolderOpen size={14} />{sample.root}</div>
      <ul>{sample.files.map((file) => <li key={file.name}><FileText size={13} /><span>{file.name}</span><em>{file.meta}</em></li>)}</ul>
    </div>
  }
  const sample = copy.guardrailSample
  const grants = { skills: [true, false, false], knowledge: [true, false, false] }
  return <div className="hv-layer-visual is-token" aria-hidden="true">
    <div className="hv-token-head"><KeyRound size={14} />{sample.token}</div>
    <table>
      <thead><tr><th /><th>{sample.columns.read}</th><th>{sample.columns.upload}</th><th>{sample.columns.delete}</th></tr></thead>
      <tbody>{(['skills', 'knowledge'] as const).map((row) => <tr key={row}><th>{sample.rows[row]}</th>{grants[row].map((granted, index) => <td key={index}><span className={granted ? 'hv-grant is-on' : 'hv-grant'} title={granted ? sample.granted : sample.notGranted}>{granted ? <Check size={12} strokeWidth={3} /> : <Minus size={12} />}</span></td>)}</tr>)}</tbody>
    </table>
    <div className="hv-token-scope">{sample.scope}</div>
  </div>
}

export function LandingPage() {
  const copy = useLandingCopy()
  const rootRef = useRef<HTMLDivElement>(null)
  const reducedMotion = usePrefersReducedMotion()
  useScrollReveal(rootRef, reducedMotion)

  const scenarios = [copy.console.scenarios.report, copy.console.scenarios.incident, copy.console.scenarios.onboarding, copy.console.scenarios.writeBack]
  const layerKeys = ['skills', 'knowledge', 'guardrails'] as const
  const governanceKeys = ['scope', 'recheck', 'noBypass', 'audit', 'safeWrite', 'review'] as const
  const compareKeys = ['drive', 'rag', 'hive'] as const

  return <div className="hv-landing" ref={rootRef}>
    {/* Hero: an inset dark stage carrying the positioning and a live agent transcript. */}
    <section className="hv-hero" id="hero">
      <div className="hv-stage">
        <HexField id="hv-hex-hero" />
        <div className="hv-glow" aria-hidden="true" />
        <div className="hv-container hv-hero-grid">
          <div className="hv-hero-copy">
            <p className="hv-hero-eyebrow"><HiveMark className="hv-eyebrow-mark" />{copy.hero.eyebrow}</p>
            <h1><span>{copy.hero.titleFirst}</span><span className="hv-hero-accent">{copy.hero.titleSecond}</span></h1>
            <p className="hv-hero-lead">{copy.hero.description}</p>
            <div className="hv-actions">
              <Link to="/dashboard/tokens" className="hv-btn is-primary">{copy.hero.primaryCta}<ArrowRight size={17} aria-hidden="true" /></Link>
              <Link to="/skills" search={{ q: '', sort: 'all', page: 0, view: 'list' }} className="hv-btn is-ghost">{copy.hero.secondaryCta}</Link>
            </div>
            <ul className="hv-badges">
              <li><Server size={14} aria-hidden="true" />{copy.hero.badges.selfHosted}</li>
              <li><GitBranch size={14} aria-hidden="true" />{copy.hero.badges.openSource}</li>
              <li><ShieldCheck size={14} aria-hidden="true" />{copy.hero.badges.mcp}</li>
            </ul>
          </div>
          <AgentConsole
            label={copy.console.label}
            windowTitle={copy.console.windowTitle}
            illustrative={copy.console.illustrative}
            scenarioLabel={copy.console.scenarioLabel}
            scenarios={scenarios}
            reducedMotion={reducedMotion}
          />
        </div>
        <div className="hv-container hv-works">
          <span>{copy.hero.worksWith}</span>
          <ul>{copy.hero.clients.map((client) => <li key={client}>{client}</li>)}</ul>
        </div>
      </div>
    </section>

    <section className="hv-section hv-problem" id="problem">
      <div className="hv-container hv-problem-grid">
        <SectionHead eyebrow={copy.problem.eyebrow} title={copy.problem.title} description={copy.problem.description} />
        <ol className="hv-problem-list">
          {(['paste', 'scattered', 'risk'] as const).map((key, index) => <li key={key} data-reveal style={revealDelay(index * 90)}>
            <span className="hv-index">{String(index + 1).padStart(2, '0')}</span>
            <div><h3>{copy.problem.items[key].title}</h3><p>{copy.problem.items[key].description}</p></div>
          </li>)}
        </ol>
      </div>
    </section>

    <section className="hv-section hv-layers" id="layers">
      <div className="hv-container">
        <SectionHead eyebrow={copy.layers.eyebrow} title={copy.layers.title} description={copy.layers.description} align="center" />
        <div data-reveal>
          <ContextFlow label={copy.layers.flowLabel} {...copy.layers.flow} reducedMotion={reducedMotion} />
        </div>
        <div className="hv-layer-grid">
          {layerKeys.map((key, index) => {
            const item = copy.layers.items[key]
            return <article key={key} className="hv-layer" data-reveal style={revealDelay(index * 110)}>
              <LayerVisual kind={key} copy={copy.layers} />
              <p className="hv-kicker">{item.kicker}</p>
              <h3>{item.title}</h3>
              <p>{item.description}</p>
              <ul className="hv-points">{item.points.map((point) => <li key={point}><Check size={13} strokeWidth={2.6} aria-hidden="true" />{point}</li>)}</ul>
            </article>
          })}
        </div>
      </div>
    </section>

    <section className="hv-section hv-governance" id="governance">
      <div className="hv-container hv-governance-grid">
        <div>
          <SectionHead eyebrow={copy.governance.eyebrow} title={copy.governance.title} description={copy.governance.description} />
          <ul className="hv-guard-list">
            {governanceKeys.map((key, index) => {
              const Icon = GOVERNANCE_ICONS[key]
              return <li key={key} data-reveal style={revealDelay((index % 2) * 80)}>
                <span className="hv-guard-icon"><Icon size={17} strokeWidth={1.8} aria-hidden="true" /></span>
                <h3>{copy.governance.items[key].title}</h3>
                <p>{copy.governance.items[key].description}</p>
              </li>
            })}
          </ul>
        </div>
        <div className="hv-audit" data-reveal aria-hidden="true">
          <div className="hv-audit-head"><span className="hv-live-dot" />{copy.governance.auditTitle}<em>{copy.governance.auditLive}</em></div>
          <div className="hv-audit-window">
            <ol className="hv-audit-track">
              {[...copy.governance.audit, ...copy.governance.audit].map((row, index) => <li key={`${row.time}-${index}`}>
                <time>{row.time}</time>
                <code>{row.action}</code>
                <span className="hv-audit-target">{row.target}</span>
                <span className="hv-audit-token"><KeyRound size={11} />{row.token}</span>
              </li>)}
            </ol>
          </div>
        </div>
      </div>
    </section>

    <section className="hv-section hv-compare" id="compare">
      <div className="hv-container">
        <SectionHead eyebrow={copy.compare.eyebrow} title={copy.compare.title} align="center" />
        <div className="hv-compare-grid">
          {compareKeys.map((key, index) => {
            const column = copy.compare.columns[key]
            const featured = key === 'hive'
            return <article key={key} className={`hv-compare-card${featured ? ' is-featured' : ''}`} data-reveal style={revealDelay(index * 110)}>
              {featured ? <HiveMark className="hv-compare-mark" /> : null}
              <h3>{column.name}</h3>
              <p className="hv-compare-tag">{column.tag}</p>
              <ul>{column.points.map((point) => <li key={point}>{featured ? <Check size={14} strokeWidth={2.6} aria-hidden="true" /> : <Minus size={14} aria-hidden="true" />}{point}</li>)}</ul>
            </article>
          })}
        </div>
        <p className="hv-compare-note" data-reveal>{copy.compare.note}</p>
      </div>
    </section>

    <section className="hv-section hv-library" id="skill-library">
      <div className="hv-container">
        <SectionHead eyebrow={copy.library.eyebrow} title={copy.library.title} />
        <SkillLibrary copy={copy.library} />
      </div>
    </section>

    <section className="hv-section hv-connect" id="connect">
      <div className="hv-container hv-connect-grid">
        <div>
          <SectionHead eyebrow={copy.connect.eyebrow} title={copy.connect.title} />
          <ol className="hv-steps">
            {(['token', 'config', 'use'] as const).map((key, index) => <li key={key} data-reveal style={revealDelay(index * 90)}>
              <span className="hv-step-num">{index + 1}</span>
              <div><h3>{copy.connect.steps[key].title}</h3><p>{copy.connect.steps[key].description}</p></div>
            </li>)}
          </ol>
          <Link to="/dashboard/tokens" className="hv-btn is-dark">{copy.connect.cta}<ArrowRight size={17} aria-hidden="true" /></Link>
        </div>
        <div>
          <McpConfig copy={copy.connect} />
          <p className="hv-connect-note">{copy.connect.note}</p>
        </div>
      </div>
    </section>

    <section className="hv-closing" id="open-source">
      <div className="hv-stage is-closing" data-reveal>
        <HexField id="hv-hex-closing" />
        <div className="hv-glow is-center" aria-hidden="true" />
        <div className="hv-closing-inner">
          <HiveMark className="hv-closing-mark" />
          <h2>{copy.closing.title}</h2>
          <p>{copy.closing.description}</p>
          <div className="hv-actions is-center">
            <Link to="/dashboard/tokens" className="hv-btn is-primary">{copy.closing.connect}<ArrowRight size={17} aria-hidden="true" /></Link>
            <a href={SOURCE_URL} target="_blank" rel="noreferrer" className="hv-btn is-ghost"><GitBranch size={16} aria-hidden="true" />{copy.closing.source}</a>
          </div>
        </div>
      </div>
    </section>
  </div>
}
