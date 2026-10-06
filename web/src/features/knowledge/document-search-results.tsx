import { Link } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import type { KnowledgeDocumentSearchHit } from '@/api/knowledge-types'
import { formatLocalDateTime } from '@/shared/lib/date-time'
import { KnowledgeFileIcon } from './file-icon'
import { formatFileSize } from './file-types'

interface KnowledgeDocumentSearchResultsProps {
  hits: KnowledgeDocumentSearchHit[]
  /** Prefixes each location with the team space; off when results are already limited to one space. */
  showNamespace?: boolean
}

/** Files found across knowledge bases; each row says where the file lives and opens its detail page. */
export function KnowledgeDocumentSearchResults({ hits, showNamespace = true }: KnowledgeDocumentSearchResultsProps) {
  const { i18n } = useTranslation()
  return (
    <ul className="divide-y divide-border/40 overflow-hidden rounded-xl border border-border/60 bg-card">
      {hits.map((hit) => {
        const { document } = hit
        const location = showNamespace
          ? `${hit.namespaceDisplayName} / ${hit.knowledgeBaseDisplayName}`
          : hit.knowledgeBaseDisplayName
        return (
          <li key={document.id}>
            <Link
              to="/knowledge/$namespace/$base/$documentId"
              params={{ namespace: hit.namespace, base: hit.knowledgeBaseSlug, documentId: String(document.id) }}
              className="flex min-w-0 items-center gap-3 px-4 py-3 transition-colors hover:bg-secondary/30 focus-visible:bg-secondary/30 focus-visible:outline-none"
            >
              <KnowledgeFileIcon extension={document.fileExtension} />
              <div className="min-w-0 flex-1">
                <p className="truncate font-medium text-foreground" title={document.title}>{document.title}</p>
                <p className="truncate text-xs text-muted-foreground">
                  {location}
                  {document.description ? ` · ${document.description}` : ''}
                </p>
              </div>
              <div className="hidden shrink-0 text-right text-xs text-muted-foreground sm:block">
                <p>{formatLocalDateTime(document.updatedAt, i18n.language, { dateStyle: 'medium' })}</p>
                <p className="uppercase">{document.fileExtension} · {formatFileSize(document.sizeBytes)}</p>
              </div>
            </Link>
          </li>
        )
      })}
    </ul>
  )
}
