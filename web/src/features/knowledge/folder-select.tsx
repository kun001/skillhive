import { useTranslation } from 'react-i18next'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/shared/ui/select'
import type { KnowledgeFolderNode } from './folder-tree'
import { flattenFolderTree } from './folder-tree'

const ROOT_VALUE = '__root__'

interface FolderSelectProps {
  tree: KnowledgeFolderNode[]
  value: number | undefined
  onChange: (folderId: number | undefined) => void
  /** Folders that may not be chosen, e.g. a folder and its descendants when moving it. */
  disabledIds?: Set<number>
  id?: string
  ariaLabel?: string
  disabled?: boolean
}

export function FolderSelect({ tree, value, onChange, disabledIds, id, ariaLabel, disabled }: FolderSelectProps) {
  const { t } = useTranslation()
  return (
    <Select
      disabled={disabled}
      value={value === undefined ? ROOT_VALUE : String(value)}
      onValueChange={(next) => onChange(next === ROOT_VALUE ? undefined : Number(next))}
    >
      <SelectTrigger id={id} aria-label={ariaLabel}>
        <SelectValue />
      </SelectTrigger>
      <SelectContent>
        <SelectItem value={ROOT_VALUE}>{t('knowledge.folderDialog.root')}</SelectItem>
        {flattenFolderTree(tree).map((node) => (
          <SelectItem key={node.folder.id} value={String(node.folder.id)} disabled={disabledIds?.has(node.folder.id)}>
            <span style={{ paddingLeft: `${node.depth * 14}px` }}>{node.folder.name}</span>
          </SelectItem>
        ))}
      </SelectContent>
    </Select>
  )
}
