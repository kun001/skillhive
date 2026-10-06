import { Download, MoreHorizontal } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { knowledgeApi } from '@/api/client'
import type { KnowledgeDocument } from '@/api/knowledge-types'
import { Button, buttonVariants } from '@/shared/ui/button'
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuSeparator, DropdownMenuTrigger } from '@/shared/ui/dropdown-menu'

export interface KnowledgeDocumentActionHandlers {
  onOpen: (document: KnowledgeDocument) => void
  onNewVersion: (document: KnowledgeDocument) => void
  onEdit: (document: KnowledgeDocument) => void
  onDelete: (document: KnowledgeDocument) => void
}

interface DocumentActionsProps extends KnowledgeDocumentActionHandlers {
  document: KnowledgeDocument
  canContribute: boolean
}

export function KnowledgeDocumentActions({ document, canContribute, onOpen, onNewVersion, onEdit, onDelete }: DocumentActionsProps) {
  const { t } = useTranslation()
  return (
    <div className="flex items-center justify-end gap-1" onClick={(event) => event.stopPropagation()}>
      {document.canDownload ? <a
        href={knowledgeApi.contentUrl(document.id)}
        download
        title={t('knowledge.actions.download')}
        aria-label={t('knowledge.actions.download')}
        className={buttonVariants({ variant: 'ghost', size: 'icon', className: 'h-8 w-8' })}
      >
        <Download className="h-4 w-4" />
      </a> : null}
      <DropdownMenu modal={false}>
        <DropdownMenuTrigger asChild>
          <Button variant="ghost" size="icon" className="h-8 w-8" aria-label={t('knowledge.actions.more')}>
            <MoreHorizontal className="h-4 w-4" />
          </Button>
        </DropdownMenuTrigger>
        <DropdownMenuContent align="end">
          <DropdownMenuItem onSelect={() => onOpen(document)}>{t('knowledge.actions.open')}</DropdownMenuItem>
          {canContribute ? (
            <DropdownMenuItem onSelect={() => onNewVersion(document)}>{t('knowledge.actions.newVersion')}</DropdownMenuItem>
          ) : null}
          {document.canManage ? (
            <>
              <DropdownMenuItem onSelect={() => onEdit(document)}>{t('knowledge.actions.edit')}</DropdownMenuItem>
              <DropdownMenuSeparator />
              <DropdownMenuItem className="text-destructive focus:text-destructive" onSelect={() => onDelete(document)}>
                {t('knowledge.actions.delete')}
              </DropdownMenuItem>
            </>
          ) : null}
        </DropdownMenuContent>
      </DropdownMenu>
    </div>
  )
}
