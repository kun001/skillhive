import { useId, useRef, useState, type CSSProperties, type KeyboardEvent, type PointerEvent, type ReactNode } from 'react'
import { useTranslation } from 'react-i18next'

const MIN_WIDTH = 192
const DEFAULT_WIDTH = 240
const MAX_WIDTH = 448
const KEYBOARD_STEP = 16

function clampWidth(width: number) {
  return Math.min(MAX_WIDTH, Math.max(MIN_WIDTH, width))
}

export function KnowledgeFolderSidebar({ children }: { children: ReactNode }) {
  const { t } = useTranslation()
  const panelId = useId()
  const [width, setWidth] = useState(DEFAULT_WIDTH)
  const drag = useRef<{ pointerId: number; startX: number; startWidth: number } | null>(null)

  const startResize = (event: PointerEvent<HTMLDivElement>) => {
    if (event.button !== 0 || drag.current) return
    event.preventDefault()
    event.currentTarget.setPointerCapture(event.pointerId)
    drag.current = { pointerId: event.pointerId, startX: event.clientX, startWidth: width }
  }

  const moveResize = (event: PointerEvent<HTMLDivElement>) => {
    const start = drag.current
    if (start?.pointerId === event.pointerId) {
      setWidth(clampWidth(start.startWidth + event.clientX - start.startX))
    }
  }

  const resizeWithKeyboard = (event: KeyboardEvent<HTMLDivElement>) => {
    switch (event.key) {
      case 'ArrowLeft': setWidth((previous) => clampWidth(previous - KEYBOARD_STEP)); break
      case 'ArrowRight': setWidth((previous) => clampWidth(previous + KEYBOARD_STEP)); break
      case 'Home': setWidth(MIN_WIDTH); break
      case 'End': setWidth(MAX_WIDTH); break
      default: return
    }
    event.preventDefault()
  }

  return (
    <aside className="relative min-w-0 lg:sticky lg:top-24 lg:w-[var(--folder-sidebar-width)] lg:self-start"
      style={{ '--folder-sidebar-width': `${width}px` } as CSSProperties}>
      <div id={panelId} className="lg:max-h-[calc(100vh-7rem)] lg:overflow-y-auto">
        {children}
      </div>
      <div
        role="separator"
        aria-orientation="vertical"
        aria-label={t('knowledge.tree.resizeSidebar')}
        aria-controls={panelId}
        aria-valuemin={MIN_WIDTH}
        aria-valuemax={MAX_WIDTH}
        aria-valuenow={width}
        tabIndex={0}
        className="group absolute inset-y-0 -right-6 hidden w-6 touch-none cursor-col-resize select-none justify-center rounded-sm outline-none focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-ring lg:flex"
        onPointerDown={startResize}
        onPointerMove={moveResize}
        onLostPointerCapture={() => { drag.current = null }}
        onKeyDown={resizeWithKeyboard}
      >
        <span aria-hidden className="pointer-events-none w-px bg-border/70 group-hover:w-0.5 group-hover:bg-primary/50 group-active:bg-primary group-focus-visible:bg-primary" />
      </div>
    </aside>
  )
}
