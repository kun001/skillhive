import { ChevronRight, Folder, FolderOpen, FolderPlus, Library, MoreHorizontal } from 'lucide-react'
import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import type { KnowledgeFolder } from '@/api/knowledge-types'
import { cn } from '@/shared/lib/utils'
import { Button } from '@/shared/ui/button'
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuSeparator, DropdownMenuTrigger } from '@/shared/ui/dropdown-menu'
import type { KnowledgeFolderNode } from './folder-tree'

interface FolderTreeNavProps {
  tree: KnowledgeFolderNode[]
  ancestry: KnowledgeFolder[]
  selectedFolderId: number | undefined
  totalCount: number
  canContribute: boolean
  onSelect: (folderId: number | undefined) => void
  onCreate: (parentId: number | undefined) => void
  onEdit: (folder: KnowledgeFolder) => void
  onDelete: (folder: KnowledgeFolder) => void
}

/** Yuque-style directory tree: "All files" plus nested folders with per-folder file counts. */
export function KnowledgeFolderTreeNav({
  tree,
  ancestry,
  selectedFolderId,
  totalCount,
  canContribute,
  onSelect,
  onCreate,
  onEdit,
  onDelete,
}: FolderTreeNavProps) {
  const { t } = useTranslation()
  const [expanded, setExpanded] = useState<Set<number>>(new Set())

  // Keep the selected folder visible when it is chosen from a link or the breadcrumb.
  useEffect(() => {
    if (ancestry.length > 1) {
      setExpanded((current) => {
        const next = new Set(current)
        ancestry.slice(0, -1).forEach((folder) => next.add(folder.id))
        return next.size === current.size ? current : next
      })
    }
  }, [ancestry])

  const toggle = (folderId: number) =>
    setExpanded((current) => {
      const next = new Set(current)
      if (next.has(folderId)) next.delete(folderId)
      else next.add(folderId)
      return next
    })

  const renderNode = (node: KnowledgeFolderNode) => {
    const { folder } = node
    const selected = folder.id === selectedFolderId
    const isOpen = expanded.has(folder.id)
    const hasChildren = node.children.length > 0
    const FolderIcon = selected || isOpen ? FolderOpen : Folder
    return (
      <li key={folder.id}>
        <div
          className={cn(
            'group flex items-center gap-1 rounded-lg pr-1 text-sm transition-colors',
            selected ? 'bg-primary/10 text-primary' : 'text-foreground/80 hover:bg-accent/70',
          )}
          style={{ paddingLeft: `${node.depth * 14 + 4}px` }}
        >
          <button
            type="button"
            className={cn('flex h-6 w-5 shrink-0 items-center justify-center rounded text-muted-foreground', !hasChildren && 'invisible')}
            aria-label={t(isOpen ? 'knowledge.tree.collapse' : 'knowledge.tree.expand')}
            onClick={() => toggle(folder.id)}
          >
            <ChevronRight className={cn('h-3.5 w-3.5 transition-transform', isOpen && 'rotate-90')} />
          </button>
          <button
            type="button"
            className="flex min-w-0 flex-1 items-center gap-2 py-1.5 text-left"
            aria-current={selected ? 'page' : undefined}
            onClick={() => onSelect(folder.id)}
          >
            <FolderIcon className="h-4 w-4 shrink-0" aria-hidden />
            <span className="truncate">{folder.name}</span>
          </button>
          <span className="text-xs tabular-nums text-muted-foreground group-hover:hidden">{node.totalCount || ''}</span>
          {canContribute ? (
            <DropdownMenu modal={false}>
              <DropdownMenuTrigger asChild>
                <button
                  type="button"
                  className="hidden h-6 w-6 items-center justify-center rounded text-muted-foreground hover:bg-background group-hover:flex data-[state=open]:flex"
                  aria-label={t('knowledge.tree.actions')}
                >
                  <MoreHorizontal className="h-4 w-4" />
                </button>
              </DropdownMenuTrigger>
              <DropdownMenuContent align="start">
                <DropdownMenuItem onSelect={() => onCreate(folder.id)}>{t('knowledge.tree.newSubfolder')}</DropdownMenuItem>
                {folder.canManage ? (
                  <>
                    <DropdownMenuItem onSelect={() => onEdit(folder)}>{t('knowledge.tree.edit')}</DropdownMenuItem>
                    <DropdownMenuSeparator />
                    <DropdownMenuItem className="text-destructive focus:text-destructive" onSelect={() => onDelete(folder)}>
                      {t('knowledge.tree.delete')}
                    </DropdownMenuItem>
                  </>
                ) : null}
              </DropdownMenuContent>
            </DropdownMenu>
          ) : null}
        </div>
        {hasChildren && isOpen ? <ul>{node.children.map(renderNode)}</ul> : null}
      </li>
    )
  }

  return (
    <nav aria-label={t('knowledge.tree.folders')} className="space-y-3">
      <button
        type="button"
        onClick={() => onSelect(undefined)}
        aria-current={selectedFolderId === undefined ? 'page' : undefined}
        className={cn(
          'flex w-full items-center gap-2 rounded-lg px-2 py-2 text-sm font-medium transition-colors',
          selectedFolderId === undefined ? 'bg-primary/10 text-primary' : 'text-foreground/80 hover:bg-accent/70',
        )}
      >
        <Library className="h-4 w-4" aria-hidden />
        <span className="flex-1 text-left">{t('knowledge.tree.allFiles')}</span>
        <span className="text-xs tabular-nums text-muted-foreground">{totalCount}</span>
      </button>

      <div className="border-t border-border/50 pt-3">
        <div className="mb-1 flex items-center justify-between px-2">
          <span className="text-xs font-semibold uppercase tracking-wider text-muted-foreground">{t('knowledge.tree.folders')}</span>
          {canContribute ? (
            <Button variant="ghost" size="icon" className="h-7 w-7" aria-label={t('knowledge.tree.newFolder')} title={t('knowledge.tree.newFolder')} onClick={() => onCreate(undefined)}>
              <FolderPlus className="h-4 w-4" />
            </Button>
          ) : null}
        </div>
        <ul className="space-y-0.5">{tree.map(renderNode)}</ul>
      </div>
    </nav>
  )
}
