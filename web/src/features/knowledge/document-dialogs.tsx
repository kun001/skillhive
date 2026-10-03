import { useEffect, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { knowledgeApi } from '@/api/client'
import type { KnowledgeDocument } from '@/api/knowledge-types'
import { toast } from '@/shared/lib/toast'
import { Button } from '@/shared/ui/button'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/shared/ui/dialog'
import { Input } from '@/shared/ui/input'
import { Label } from '@/shared/ui/label'
import { Textarea } from '@/shared/ui/textarea'
import { knowledgeErrorMessage } from './errors'
import { KnowledgeFileIcon } from './file-icon'
import { FolderSelect } from './folder-select'
import type { KnowledgeFolderNode } from './folder-tree'
import { KNOWLEDGE_ACCEPTED_EXTENSIONS, fileExtension, formatFileSize, validateKnowledgeFile } from './file-types'
import { useInvalidateKnowledge, useUpdateKnowledgeDocument } from './use-knowledge-queries'
import { isMarkdown, planUploads } from './markdown-images'
import { createUploadItems } from './upload-queue'

interface EditDocumentDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  document: KnowledgeDocument
  tree: KnowledgeFolderNode[]
}

export function EditKnowledgeDocumentDialog({ open, onOpenChange, document, tree }: EditDocumentDialogProps) {
  const { t } = useTranslation()
  const updateDocument = useUpdateKnowledgeDocument()
  const [title, setTitle] = useState(document.title)
  const [description, setDescription] = useState(document.description ?? '')
  const [folderId, setFolderId] = useState<number | undefined>(document.folderId ?? undefined)

  useEffect(() => {
    if (open) {
      setTitle(document.title)
      setDescription(document.description ?? '')
      setFolderId(document.folderId ?? undefined)
    }
  }, [open, document])

  const submit = async () => {
    try {
      await updateDocument.mutateAsync({
        documentId: document.id,
        request: { title: title.trim(), description: description.trim() || undefined, folderId },
      })
      toast.success(t('knowledge.editDialog.saved'))
      onOpenChange(false)
    } catch (error) {
      toast.error(knowledgeErrorMessage(error))
    }
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="w-[min(calc(100vw-2rem),32rem)]">
        <DialogHeader>
          <DialogTitle>{t('knowledge.editDialog.title')}</DialogTitle>
        </DialogHeader>
        <form
          className="space-y-4"
          onSubmit={(event) => {
            event.preventDefault()
            if (title.trim() && !updateDocument.isPending) void submit()
          }}
        >
          <div className="space-y-2">
            <Label htmlFor="kb-doc-title">{t('knowledge.editDialog.fileTitle')}</Label>
            <Input id="kb-doc-title" value={title} maxLength={256} onChange={(event) => setTitle(event.target.value)} />
          </div>
          <div className="space-y-2">
            <Label htmlFor="kb-doc-description">{t('knowledge.editDialog.description')}</Label>
            <Textarea
              id="kb-doc-description"
              value={description}
              maxLength={2000}
              rows={4}
              placeholder={t('knowledge.editDialog.descriptionPlaceholder')}
              onChange={(event) => setDescription(event.target.value)}
            />
          </div>
          <div className="space-y-2">
            <Label htmlFor="kb-doc-folder">{t('knowledge.editDialog.folder')}</Label>
            <FolderSelect id="kb-doc-folder" tree={tree} value={folderId} onChange={setFolderId} />
          </div>
          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => onOpenChange(false)}>
              {t('dialog.cancel')}
            </Button>
            <Button type="submit" disabled={!title.trim() || updateDocument.isPending}>
              {t('knowledge.editDialog.save')}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}

interface NewVersionDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  document: KnowledgeDocument
}

export function NewKnowledgeVersionDialog({ open, onOpenChange, document }: NewVersionDialogProps) {
  const { t } = useTranslation()
  const invalidate = useInvalidateKnowledge()
  const inputRef = useRef<HTMLInputElement>(null)
  const directoryRef = useRef<HTMLInputElement>(null)
  const [files, setFiles] = useState<File[]>([])
  const [changeNote, setChangeNote] = useState('')
  const [progress, setProgress] = useState<number | null>(null)

  useEffect(() => {
    if (open) {
      setFiles([])
      setChangeNote('')
      setProgress(null)
    }
  }, [open])

  const file = files.find(isMarkdown) ?? files[0]
  const fileError = files.map(validateKnowledgeFile).find(Boolean)
  const uploading = progress !== null

  const submit = async () => {
    if (!file) return
    setProgress(0)
    try {
      const existing = document.previewKind === 'MARKDOWN' ? await knowledgeApi.listImages(document.id, document.currentVersion) : undefined
      const plan = await planUploads(createUploadItems(files), existing ? { sourcePath: existing.sourcePath, paths: existing.images.map((image) => image.path) } : undefined)
      if (plan.length !== 1) throw new Error(t('knowledge.upload.versionSingleFile'))
      const entry = plan[0]
      if (entry.error) throw new Error(t(entry.error.key, { path: entry.error.path }))
      await knowledgeApi.uploadVersion(document.id, { file: entry.item.file, changeNote, ...entry.bundle }, { onProgress: setProgress })
      await invalidate()
      toast.success(t('knowledge.versionDialog.uploaded'))
      onOpenChange(false)
    } catch (error) {
      toast.error(knowledgeErrorMessage(error))
      setProgress(null)
    }
  }

  return (
    <Dialog open={open} onOpenChange={(next) => !uploading && onOpenChange(next)}>
      <DialogContent className="w-[min(calc(100vw-2rem),32rem)]">
        <DialogHeader>
          <DialogTitle>{t('knowledge.versionDialog.title')}</DialogTitle>
          <DialogDescription>{t('knowledge.versionDialog.description')}</DialogDescription>
        </DialogHeader>
        <div className="space-y-4">
          <input
            ref={inputRef}
            type="file"
            multiple
            className="hidden"
            accept={KNOWLEDGE_ACCEPTED_EXTENSIONS.map((extension) => `.${extension}`).join(',')}
            onChange={(event) => { setFiles(Array.from(event.target.files ?? [])); event.target.value = '' }}
          />
          <input type="file" multiple className="hidden" ref={(node) => {
            directoryRef.current = node
            node?.setAttribute('webkitdirectory', '')
          }} onChange={(event) => { setFiles(Array.from(event.target.files ?? [])); event.target.value = '' }} />
          <button
            type="button"
            disabled={uploading}
            onClick={() => inputRef.current?.click()}
            className="flex w-full items-center gap-3 rounded-xl border border-dashed border-border/80 bg-secondary/30 p-4 text-left transition-colors hover:border-primary/50 hover:bg-secondary/50"
          >
            {file ? (
              <>
                <KnowledgeFileIcon extension={fileExtension(file.name)} size="md" />
                <span className="min-w-0 flex-1">
                  <span className="block truncate text-sm font-medium text-foreground">{file.name}</span>
                  <span className={fileError ? 'text-xs text-destructive' : 'text-xs text-muted-foreground'}>
                    {fileError ? t(fileError) : formatFileSize(file.size)}
                  </span>
                </span>
              </>
            ) : (
              <span className="text-sm text-muted-foreground">{t('knowledge.versionDialog.file')}</span>
            )}
          </button>
          <Button type="button" variant="outline" disabled={uploading} onClick={() => directoryRef.current?.click()}>{t('knowledge.upload.chooseFolder')}</Button>
          <p className="text-xs leading-5 text-muted-foreground">{t('knowledge.upload.markdownHint')}</p>
          {files.length > 1 ? <p className="text-xs text-muted-foreground">{t('knowledge.upload.selectedFiles', { count: files.length })}</p> : null}
          <div className="space-y-2">
            <Label htmlFor="kb-version-note">{t('knowledge.versionDialog.changeNote')}</Label>
            <Input
              id="kb-version-note"
              value={changeNote}
              maxLength={512}
              disabled={uploading}
              placeholder={t('knowledge.versionDialog.changeNotePlaceholder')}
              onChange={(event) => setChangeNote(event.target.value)}
            />
          </div>
          {uploading ? (
            <div className="h-1.5 overflow-hidden rounded-full bg-secondary">
              <div className="h-full bg-primary transition-[width]" style={{ width: `${Math.round((progress ?? 0) * 100)}%` }} />
            </div>
          ) : null}
        </div>
        <DialogFooter>
          <Button type="button" variant="outline" disabled={uploading} onClick={() => onOpenChange(false)}>
            {t('dialog.cancel')}
          </Button>
          <Button type="button" disabled={!file || !!fileError || uploading} onClick={() => void submit()}>
            {t('knowledge.versionDialog.submit')}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
