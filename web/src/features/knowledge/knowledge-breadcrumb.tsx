import { Link } from '@tanstack/react-router'
import { ChevronRight } from 'lucide-react'
import { Fragment, type ReactNode } from 'react'

export interface KnowledgeCrumb {
  label: ReactNode
  /** Router path plus optional search; the last crumb is rendered as plain text. */
  to?: string
  search?: Record<string, unknown>
  params?: Record<string, string>
}

export function KnowledgeBreadcrumb({ items }: { items: KnowledgeCrumb[] }) {
  return (
    <nav aria-label="breadcrumb" className="flex min-w-0 flex-wrap items-center gap-1 text-sm text-muted-foreground">
      {items.map((item, index) => {
        const last = index === items.length - 1
        return (
          <Fragment key={index}>
            {index > 0 ? <ChevronRight className="h-3.5 w-3.5 shrink-0" aria-hidden /> : null}
            {last || !item.to ? (
              <span className={last ? 'truncate font-medium text-foreground' : 'truncate'} aria-current={last ? 'page' : undefined}>
                {item.label}
              </span>
            ) : (
              <Link to={item.to} params={item.params as never} search={item.search as never} className="truncate hover:text-foreground">
                {item.label}
              </Link>
            )}
          </Fragment>
        )
      })}
    </nav>
  )
}
