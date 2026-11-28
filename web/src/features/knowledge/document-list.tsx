import { Folder } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import type { KnowledgeDocument, KnowledgeFolder } from '@/api/knowledge-types'
import { formatLocalDateTime } from '@/shared/lib/date-time'
import { cn } from '@/shared/lib/utils'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/shared/ui/table'
import { KnowledgeDocumentActions, type KnowledgeDocumentActionHandlers } from './document-actions'
import { KnowledgeFileIcon } from './file-icon'
import { formatFileSize } from './file-types'

interface DocumentListProps extends KnowledgeDocumentActionHandlers {
  folders: KnowledgeFolder[]
  documents: KnowledgeDocument[]
  canContribute: boolean
  onOpenFolder: (folderId: number) => void
  selection?: {
    ids: ReadonlySet<number>
    selectableIds: ReadonlySet<number>
    onToggle: (documentId: number) => void
    pageState: 'none' | 'some' | 'all'
    onTogglePage: () => void
  }
}

const checkboxClassName = 'h-4 w-4 shrink-0 cursor-pointer align-middle accent-primary'

function DocumentCheckbox({ document, selection }: {
  document: KnowledgeDocument
  selection: NonNullable<DocumentListProps['selection']>
}) {
  const { t } = useTranslation()
  return (
    <input
      type="checkbox"
      aria-label={t('knowledge.bulk.selectFile', { title: document.title })}
      checked={selection.ids.has(document.id)}
      onChange={() => selection.onToggle(document.id)}
      onClick={(event) => event.stopPropagation()}
      onKeyDown={(event) => event.stopPropagation()}
      className={checkboxClassName}
    />
  )
}

function FolderEntry({ folder, onOpen, className }: {
  folder: KnowledgeFolder
  onOpen: (folderId: number) => void
  className?: string
}) {
  return (
    <button
      type="button"
      onClick={() => onOpen(folder.id)}
      className={cn('flex w-full min-w-0 items-center gap-3 text-left font-medium text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-ring', className)}
    >
      <span className="inline-flex h-8 w-8 shrink-0 items-center justify-center rounded-lg bg-primary/10 text-primary" aria-hidden>
        <Folder className="h-4 w-4" />
      </span>
      <span className="truncate" title={folder.name}>{folder.name}</span>
    </button>
  )
}

/**
 * File table. The fixed layout lets the name column take the remaining width and truncate long
 * names instead of widening the table; secondary columns collapse into the name cell on narrow screens.
 */
export function KnowledgeDocumentTable({ folders, documents, canContribute, onOpenFolder, selection, ...handlers }: DocumentListProps) {
  const { t, i18n } = useTranslation()
  return (
    <div className="overflow-hidden rounded-xl border border-border/60 bg-card">
      <Table className="table-fixed">
        <TableHeader>
          <TableRow>
            {selection ? (
              <TableHead className="w-11 px-3">
                <input
                  type="checkbox"
                  aria-label={t('knowledge.bulk.selectPage')}
                  title={t(selection.pageState === 'all' ? 'knowledge.bulk.clearSelection' : 'knowledge.bulk.selectPage')}
                  checked={selection.pageState === 'all'}
                  ref={(input) => { if (input) input.indeterminate = selection.pageState === 'some' }}
                  onChange={selection.onTogglePage}
                  className={checkboxClassName}
                />
              </TableHead>
            ) : null}
            <TableHead>{t('knowledge.table.name')}</TableHead>
            <TableHead className="hidden w-24 lg:table-cell">{t('knowledge.table.size')}</TableHead>
            <TableHead className="hidden w-32 xl:table-cell">{t('knowledge.table.owner')}</TableHead>
            <TableHead className="hidden w-40 md:table-cell">{t('knowledge.table.updated')}</TableHead>
            <TableHead className="hidden w-16 xl:table-cell">{t('knowledge.table.version')}</TableHead>
            <TableHead className="w-[5.5rem] text-right"><span className="sr-only">{t('knowledge.table.actions')}</span></TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {folders.map((folder) => (
            <TableRow key={`folder-${folder.id}`}>
              {selection ? <TableCell /> : null}
              <TableCell className="p-0">
                <FolderEntry folder={folder} onOpen={onOpenFolder} className="cursor-pointer p-4" />
              </TableCell>
              <TableCell className="hidden lg:table-cell" />
              <TableCell className="hidden xl:table-cell" />
              <TableCell className="hidden md:table-cell" />
              <TableCell className="hidden xl:table-cell" />
              <TableCell />
            </TableRow>
          ))}
          {documents.map((document) => {
            const updated = formatLocalDateTime(document.updatedAt, i18n.language, { dateStyle: 'short', timeStyle: 'short' })
            return (
            <TableRow key={document.id} className="cursor-pointer" onClick={() => handlers.onOpen(document)}>
              {selection ? (
                <TableCell className="px-3" onClick={(event) => event.stopPropagation()}>
                  {selection.selectableIds.has(document.id) ? <DocumentCheckbox document={document} selection={selection} /> : null}
                </TableCell>
              ) : null}
              <TableCell>
                <div className="flex min-w-0 items-center gap-3">
                  <KnowledgeFileIcon extension={document.fileExtension} />
                  <div className="min-w-0 flex-1">
                    <p className="truncate font-medium text-foreground" title={document.title}>{document.title}</p>
                    <p className="truncate text-xs text-muted-foreground">{document.description || document.fileName}</p>
                    <p className="truncate text-xs text-muted-foreground md:hidden">
                      {formatFileSize(document.sizeBytes)} · {updated}
                    </p>
                  </div>
                </div>
              </TableCell>
              <TableCell className="hidden text-sm text-muted-foreground lg:table-cell">{formatFileSize(document.sizeBytes)}</TableCell>
              <TableCell className="hidden truncate text-sm text-muted-foreground xl:table-cell">{document.owner.displayName}</TableCell>
              <TableCell className="hidden text-sm text-muted-foreground md:table-cell">{updated}</TableCell>
              <TableCell className="hidden text-sm text-muted-foreground xl:table-cell">v{document.currentVersion}</TableCell>
              <TableCell>
                <KnowledgeDocumentActions document={document} canContribute={canContribute} {...handlers} />
              </TableCell>
            </TableRow>
            )
          })}
        </TableBody>
      </Table>
    </div>
  )
}

export function KnowledgeDocumentGrid({ folders, documents, canContribute, onOpenFolder, selection, ...handlers }: DocumentListProps) {
  const { i18n } = useTranslation()
  return (
    <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-3">
      {folders.map((folder) => (
        <FolderEntry
          key={`folder-${folder.id}`}
          folder={folder}
          onOpen={onOpenFolder}
          className="cursor-pointer self-start rounded-xl border border-border/60 bg-card p-4 transition-colors hover:border-primary/30 hover:bg-accent/50"
        />
      ))}
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
            {selection && selection.selectableIds.has(document.id) ? <DocumentCheckbox document={document} selection={selection} /> : null}
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
