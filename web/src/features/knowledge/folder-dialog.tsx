import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import type { KnowledgeFolder } from '@/api/knowledge-types'
import { toast } from '@/shared/lib/toast'
import { Button } from '@/shared/ui/button'
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle } from '@/shared/ui/dialog'
import { Input } from '@/shared/ui/input'
import { Label } from '@/shared/ui/label'
import { knowledgeErrorMessage } from './errors'
import { FolderSelect } from './folder-select'
import { descendantIds, type KnowledgeFolderNode } from './folder-tree'
import { useCreateKnowledgeFolder, useUpdateKnowledgeFolder } from './use-knowledge-queries'

interface FolderDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  namespace: string
  base: string
  tree: KnowledgeFolderNode[]
  /** Folder being edited; omit to create a folder. */
  folder?: KnowledgeFolder
  /** Initial parent when creating. */
  parentId?: number
  onSaved?: (folder: KnowledgeFolder) => void
}

export function KnowledgeFolderDialog({ open, onOpenChange, namespace, base, tree, folder, parentId, onSaved }: FolderDialogProps) {
  const { t } = useTranslation()
  const createFolder = useCreateKnowledgeFolder(namespace, base)
  const updateFolder = useUpdateKnowledgeFolder(namespace, base)
  const [name, setName] = useState('')
  const [parent, setParent] = useState<number | undefined>(undefined)

  useEffect(() => {
    if (open) {
      setName(folder?.name ?? '')
      setParent(folder ? folder.parentId ?? undefined : parentId)
    }
  }, [open, folder, parentId])

  const pending = createFolder.isPending || updateFolder.isPending

  const submit = async () => {
    try {
      const request = { name: name.trim(), parentId: parent }
      const saved = folder
        ? await updateFolder.mutateAsync({ folderId: folder.id, request })
        : await createFolder.mutateAsync(request)
      toast.success(t(folder ? 'knowledge.folderDialog.saved' : 'knowledge.folderDialog.created'))
      onOpenChange(false)
      onSaved?.(saved as KnowledgeFolder)
    } catch (error) {
      toast.error(knowledgeErrorMessage(error))
    }
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{t(folder ? 'knowledge.folderDialog.editTitle' : 'knowledge.folderDialog.createTitle')}</DialogTitle>
        </DialogHeader>
        <form
          className="space-y-4"
          onSubmit={(event) => {
            event.preventDefault()
            if (name.trim() && !pending) void submit()
          }}
        >
          <div className="space-y-2">
            <Label htmlFor="kb-folder-name">{t('knowledge.folderDialog.name')}</Label>
            <Input
              id="kb-folder-name"
              value={name}
              maxLength={128}
              autoFocus
              placeholder={t('knowledge.folderDialog.namePlaceholder')}
              onChange={(event) => setName(event.target.value)}
            />
          </div>
          <div className="space-y-2">
            <Label htmlFor="kb-folder-parent">{t('knowledge.folderDialog.parent')}</Label>
            <FolderSelect
              id="kb-folder-parent"
              tree={tree}
              value={parent}
              onChange={setParent}
              disabledIds={folder ? descendantIds(tree, folder.id) : undefined}
            />
          </div>
          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => onOpenChange(false)}>
              {t('dialog.cancel')}
            </Button>
            <Button type="submit" disabled={!name.trim() || pending}>
              {t(folder ? 'knowledge.folderDialog.save' : 'knowledge.folderDialog.create')}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
