import type { CSSProperties } from 'react'
import { Bot, Code2, FolderOpen, Layers3, Plug, ShieldCheck, Terminal, Users } from 'lucide-react'

interface ContextFlowProps {
  label: string
  teamTitle: string
  agentTitle: string
  sources: { skills: string; knowledge: string; members: string }
  hubTitle: string
  hubSubtitle: string
  gate: string
  agents: string[]
  reducedMotion: boolean
}

// Shared coordinate space for the SVG connectors and the absolutely positioned nodes.
const VIEW_HEIGHT = 460
const HUB_Y = 230
const SOURCE_Y = [120, 230, 340]
const AGENT_Y = [65, 175, 285, 395]
const AGENT_ICONS = [Terminal, Code2, Bot, Plug]

const sourcePath = (y: number) => `M240 ${y} C 320 ${y}, 330 ${HUB_Y}, 410 ${HUB_Y}`
const agentPath = (y: number) => `M590 ${HUB_Y} C 670 ${HUB_Y}, 680 ${y}, 760 ${y}`
const nodeTop = (y: number) => ({ '--y': `${(y / VIEW_HEIGHT) * 100}%` }) as CSSProperties

/** The SkillHive mark: two team pillars joined by the copper bridge. */
export function HiveMark({ className }: { className?: string }) {
  return <svg className={className} viewBox="0 0 64 64" aria-hidden="true">
    <rect x="7" y="8" width="17" height="48" rx="5" fill="currentColor" />
    <rect x="40" y="8" width="17" height="48" rx="5" fill="currentColor" />
    <rect x="22" y="27" width="20" height="10" rx="4" className="hv-mark-bridge" />
  </svg>
}

export function ContextFlow({ label, teamTitle, agentTitle, sources, hubTitle, hubSubtitle, gate, agents, reducedMotion }: ContextFlowProps) {
  const sourceItems = [
    { key: 'skills', icon: Layers3, text: sources.skills },
    { key: 'knowledge', icon: FolderOpen, text: sources.knowledge },
    { key: 'members', icon: Users, text: sources.members },
  ]
  const paths = [...SOURCE_Y.map(sourcePath), ...AGENT_Y.map(agentPath)]

  return <figure className="hv-flow" aria-label={label}>
    <div className="hv-flow-heads" aria-hidden="true"><span>{teamTitle}</span><span>{agentTitle}</span></div>
    <div className="hv-flow-stage">
      <svg className="hv-flow-wires" viewBox={`0 0 1000 ${VIEW_HEIGHT}`} aria-hidden="true">
        <defs>
          <linearGradient id="hv-wire" x1="0" x2="1" y1="0" y2="0">
            <stop offset="0%" stopColor="var(--hv-wire-start)" />
            <stop offset="50%" stopColor="var(--hv-copper)" />
            <stop offset="100%" stopColor="var(--hv-wire-start)" />
          </linearGradient>
          <radialGradient id="hv-packet">
            <stop offset="0%" stopColor="var(--hv-copper-bright)" stopOpacity="1" />
            <stop offset="100%" stopColor="var(--hv-copper-bright)" stopOpacity="0" />
          </radialGradient>
        </defs>
        {paths.map((d, index) => <path key={d} id={`hv-wire-${index}`} d={d} className="hv-wire" />)}
        {reducedMotion ? null : paths.map((d, index) => <g key={`packet-${d}`} className="hv-packet">
          <circle r="11" fill="url(#hv-packet)" />
          <circle r="3.2" className="hv-packet-core" />
          <animateMotion dur="3.4s" repeatCount="indefinite" begin={`${(index % 7) * 0.48}s`} calcMode="spline" keyTimes="0;1" keyPoints="0;1" keySplines="0.45 0 0.25 1" path={d} />
        </g>)}
      </svg>

      <ul className="hv-flow-col is-sources">
        {sourceItems.map(({ key, icon: Icon, text }, index) => <li key={key} className="hv-flow-node" style={nodeTop(SOURCE_Y[index])}>
          <span className="hv-flow-icon"><Icon size={16} strokeWidth={1.8} aria-hidden="true" /></span>{text}
        </li>)}
      </ul>

      <div className="hv-flow-hub" style={nodeTop(HUB_Y)}>
        <span className="hv-hub-ring" aria-hidden="true" />
        <span className="hv-hub-ring is-delayed" aria-hidden="true" />
        <div className="hv-hub-core">
          <HiveMark className="hv-hub-mark" />
          <strong>{hubTitle}</strong>
          <small>{hubSubtitle}</small>
        </div>
        <span className="hv-hub-gate"><ShieldCheck size={13} strokeWidth={2} aria-hidden="true" />{gate}</span>
      </div>

      <ul className="hv-flow-col is-agents">
        {agents.map((agent, index) => {
          const Icon = AGENT_ICONS[index % AGENT_ICONS.length]
          return <li key={agent} className="hv-flow-node" style={nodeTop(AGENT_Y[index] ?? HUB_Y)}>
            <span className="hv-flow-icon"><Icon size={16} strokeWidth={1.8} aria-hidden="true" /></span>{agent}
          </li>
        })}
      </ul>
    </div>
  </figure>
}
