import { useEffect, useRef, useState } from 'react'
import { Check, CornerDownRight } from 'lucide-react'

export interface ConsoleScenario {
  tab: string
  prompt: string
  steps: { tool: string; detail: string }[]
  result: string
}

interface AgentConsoleProps {
  label: string
  windowTitle: string
  illustrative: string
  scenarioLabel: string
  scenarios: ConsoleScenario[]
  reducedMotion: boolean
}

const TYPE_DELAY = 420
const CHAR_MS = 30
const STEP_GAP = 360
const STEP_MS = 640
const HOLD_MS = 3800

/** Milestones of one scenario, in milliseconds from its start. */
export function getScenarioTimeline(scenario: ConsoleScenario) {
  const typed = TYPE_DELAY + scenario.prompt.length * CHAR_MS
  const stepsStart = typed + STEP_GAP
  const result = stepsStart + scenario.steps.length * STEP_MS
  return { typed, stepsStart, result, total: result + HOLD_MS }
}

/**
 * A scripted agent transcript showing MCP calls against SkillHive. It plays on a single
 * clock that pauses while hovered, focused or off screen, and renders fully static
 * when the viewer prefers reduced motion.
 */
export function AgentConsole({ label, windowTitle, illustrative, scenarioLabel, scenarios, reducedMotion }: AgentConsoleProps) {
  const [index, setIndex] = useState(0)
  const [typed, setTyped] = useState(0)
  const [revealed, setRevealed] = useState(0)
  const rootRef = useRef<HTMLDivElement>(null)
  const barRef = useRef<HTMLSpanElement>(null)
  const pausedRef = useRef(false)
  const inViewRef = useRef(true)
  // Translated copy may be rebuilt on every render, so the clock reads it through a ref.
  const scenariosRef = useRef(scenarios)
  scenariosRef.current = scenarios
  const scenario = scenarios[index] ?? scenarios[0]

  useEffect(() => {
    const root = rootRef.current
    if (!root || typeof IntersectionObserver === 'undefined') return
    const observer = new IntersectionObserver(([entry]) => { inViewRef.current = entry?.isIntersecting ?? true })
    observer.observe(root)
    return () => observer.disconnect()
  }, [])

  useEffect(() => {
    const scenario = scenariosRef.current[index]
    if (!scenario) return
    if (reducedMotion) {
      setTyped(scenario.prompt.length)
      setRevealed(scenario.steps.length + 1)
      return
    }

    const timeline = getScenarioTimeline(scenario)
    let elapsed = 0
    let last = performance.now()
    let frame = 0
    setTyped(0)
    setRevealed(0)

    const tick = (now: number) => {
      if (!pausedRef.current && inViewRef.current) elapsed += Math.min(now - last, 100)
      last = now

      const chars = Math.max(0, Math.min(scenario.prompt.length, Math.floor((elapsed - TYPE_DELAY) / CHAR_MS)))
      const steps = elapsed < timeline.stepsStart ? 0 : elapsed >= timeline.result ? scenario.steps.length + 1 : Math.floor((elapsed - timeline.stepsStart) / STEP_MS) + 1
      setTyped((value) => (value === chars ? value : chars))
      setRevealed((value) => (value === steps ? value : steps))
      if (barRef.current) barRef.current.style.transform = `scaleX(${Math.min(1, elapsed / timeline.total)})`

      if (elapsed >= timeline.total) {
        setIndex((value) => (value + 1) % scenariosRef.current.length)
        return
      }
      frame = requestAnimationFrame(tick)
    }

    frame = requestAnimationFrame(tick)
    return () => cancelAnimationFrame(frame)
  }, [index, reducedMotion])

  if (!scenario) return null
  const typing = !reducedMotion && typed < scenario.prompt.length

  return <div
    ref={rootRef}
    className="hv-console"
    role="group"
    aria-label={label}
    onMouseEnter={() => { pausedRef.current = true }}
    onMouseLeave={() => { pausedRef.current = false }}
    onFocus={() => { pausedRef.current = true }}
    onBlur={(event) => { if (!event.currentTarget.contains(event.relatedTarget as Node | null)) pausedRef.current = false }}
  >
    <div className="hv-console-bar">
      <span className="hv-console-dots" aria-hidden="true"><i /><i /><i /></span>
      <span className="hv-console-title"><span className="hv-live-dot" aria-hidden="true" />{windowTitle}</span>
      <span className="hv-console-badge">{illustrative}</span>
    </div>

    <div className="hv-console-tabs" role="tablist" aria-label={scenarioLabel}>
      {scenarios.map((item, itemIndex) => <button
        key={item.tab}
        type="button"
        role="tab"
        aria-selected={itemIndex === index}
        onClick={() => setIndex(itemIndex)}
      >
        {item.tab}
        {itemIndex === index && !reducedMotion ? <span ref={barRef} className="hv-tab-progress" aria-hidden="true" /> : null}
      </button>)}
    </div>

    <div className="hv-console-body" role="tabpanel">
      <p className="hv-console-prompt">
        <span className="hv-prompt-mark" aria-hidden="true">›</span>
        <span className="sr-only">{scenario.prompt}</span>
        <span aria-hidden="true">{scenario.prompt.slice(0, typed)}{typing || typed === 0 ? <span className="hv-cursor" /> : null}</span>
      </p>
      <ol className="hv-console-steps">
        {scenario.steps.map((step, stepIndex) => {
          const state = stepIndex + 1 < revealed ? 'done' : stepIndex + 1 === revealed ? 'running' : 'idle'
          return <li key={`${scenario.tab}-${stepIndex}`} className={`hv-step is-${state}`}>
            <span className="hv-step-mark" aria-hidden="true">{state === 'done' ? <Check size={12} strokeWidth={3} /> : null}</span>
            <code><span>skillhive.</span>{step.tool}</code>
            <span className="hv-step-detail">{step.detail}</span>
          </li>
        })}
      </ol>
      <p className={`hv-console-result${revealed > scenario.steps.length ? ' is-shown' : ''}`}>
        <CornerDownRight size={15} aria-hidden="true" />
        <span>{scenario.result}</span>
      </p>
    </div>
  </div>
}
