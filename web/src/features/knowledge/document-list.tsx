import { useTranslation } from 'react-i18next'
import type { KnowledgeDocument } from '@/api/knowledge-types'
import { formatLocalDateTime } from '@/shared/lib/date-time'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/shared/ui/table'
import { KnowledgeDocumentActions, type KnowledgeDocumentActionHandlers } from './document-actions'
import { KnowledgeFileIcon } from './file-icon'
import { formatFileSize } from './file-types'

interface DocumentListProps extends KnowledgeDocumentActionHandlers {
  documents: KnowledgeDocument[]
  canContribute: boolean
}

export function KnowledgeDocumentTable({ documents, canContribute, ...handlers }: DocumentListProps) {
  const { t, i18n } = useTranslation()
  return (
    <div className="overflow-hidden rounded-xl border border-border/60 bg-card">
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead className="min-w-[16rem]">{t('knowledge.table.name')}</TableHead>
            <TableHead className="hidden w-24 md:table-cell">{t('knowledge.table.size')}</TableHead>
            <TableHead className="hidden w-32 lg:table-cell">{t('knowledge.table.owner')}</TableHead>
            <TableHead className="hidden w-44 sm:table-cell">{t('knowledge.table.updated')}</TableHead>
            <TableHead className="hidden w-16 md:table-cell">{t('knowledge.table.version')}</TableHead>
            <TableHead className="w-24 text-right"><span className="sr-only">{t('knowledge.table.actions')}</span></TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {documents.map((document) => (
            <TableRow key={document.id} className="cursor-pointer" onClick={() => handlers.onOpen(document)}>
              <TableCell>
                <div className="flex min-w-0 items-center gap-3">
                  <KnowledgeFileIcon extension={document.fileExtension} />
                  <div className="min-w-0">
                    <p className="truncate font-medium text-foreground">{document.title}</p>
                    <p className="truncate text-xs text-muted-foreground">{document.description || document.fileName}</p>
                  </div>
                </div>
              </TableCell>
              <TableCell className="hidden text-sm text-muted-foreground md:table-cell">{formatFileSize(document.sizeBytes)}</TableCell>
              <TableCell className="hidden truncate text-sm text-muted-foreground lg:table-cell">{document.owner.displayName}</TableCell>
              <TableCell className="hidden text-sm text-muted-foreground sm:table-cell">
                {formatLocalDateTime(document.updatedAt, i18n.language, { dateStyle: 'short', timeStyle: 'short' })}
              </TableCell>
              <TableCell className="hidden text-sm text-muted-foreground md:table-cell">v{document.currentVersion}</TableCell>
              <TableCell>
                <KnowledgeDocumentActions document={document} canContribute={canContribute} {...handlers} />
              </TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </div>
  )
}

export function KnowledgeDocumentGrid({ documents, canContribute, ...handlers }: DocumentListProps) {
  const { i18n } = useTranslation()
  return (
    <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-3">
      {documents.map((document) => (
        <div
          key={document.id}
          role="link"
          tabIndex={0}
          onClick={() => handlers.onOpen(document)}
          onKeyDown={(event) => {
            if (event.key === 'Enter') handlers.onOpen(document)
          }}
          className="group flex cursor-pointer flex-col rounded-xl border border-border/60 bg-card p-4 transition-all hover:-translate-y-0.5 hover:border-primary/30 hover:shadow-md focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
        >
          <div className="flex items-start gap-3">
            <KnowledgeFileIcon extension={document.fileExtension} size="md" />
            <div className="min-w-0 flex-1">
              <p className="truncate font-semibold text-foreground">{document.title}</p>
              <p className="truncate text-xs text-muted-foreground">{document.fileName}</p>
            </div>
            <KnowledgeDocumentActions document={document} canContribute={canContribute} {...handlers} />
          </div>
          <p className="mt-3 line-clamp-3 min-h-[3.75rem] text-sm leading-5 text-muted-foreground">{document.description || ' '}</p>
          <div className="mt-3 flex items-center justify-between border-t border-border/50 pt-3 text-xs text-muted-foreground">
            <span>{formatLocalDateTime(document.updatedAt, i18n.language, { dateStyle: 'short', timeStyle: 'short' })}</span>
            <span className="font-medium uppercase tracking-wide">
              {document.fileExtension} · {formatFileSize(document.sizeBytes)}
            </span>
          </div>
        </div>
      ))}
    </div>
  )
}
