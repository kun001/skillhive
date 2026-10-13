import { CheckCircle2, FolderUp, Loader2, RotateCcw, UploadCloud, X, XCircle } from 'lucide-react'
import { useEffect, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { knowledgeApi } from '@/api/client'
import { toast } from '@/shared/lib/toast'
import { cn } from '@/shared/lib/utils'
import { Button } from '@/shared/ui/button'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/shared/ui/dialog'
import { Label } from '@/shared/ui/label'
import { knowledgeErrorMessage } from './errors'
import { KnowledgeFileIcon } from './file-icon'
import { FolderSelect } from './folder-select'
import type { KnowledgeFolderNode } from './folder-tree'
import { KNOWLEDGE_ACCEPTED_EXTENSIONS, fileExtension, formatFileSize } from './file-types'
import { createUploadItems, isUploadable, summarizeUploads, type UploadItem } from './upload-queue'
import { useInvalidateKnowledge } from './use-knowledge-queries'
import { planUploads, uploadPath, type UploadPlanEntry } from './markdown-images'
import { createUploadFolders, folderPaths } from './folder-upload'

interface UploadDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  namespace: string
  base: string
  tree: KnowledgeFolderNode[]
  folderId: number | undefined
  /** Files dropped onto the page before the dialog opened. */
  initialFiles?: File[]
  /** Called after at least one file uploads successfully so the parent can adjust filters. */
  onUploaded?: (result: { fileNames: string[]; folderId: number | undefined }) => void
}

export function KnowledgeUploadDialog({ open, onOpenChange, namespace, base, tree, folderId, initialFiles, onUploaded }: UploadDialogProps) {
  const { t } = useTranslation()
  const invalidate = useInvalidateKnowledge()
  const inputRef = useRef<HTMLInputElement>(null)
  const directoryRef = useRef<HTMLInputElement>(null)
  const createdFolders = useRef(new Map<string, number>())
  const busy = useRef(false)
  const wasOpen = useRef(false)
  const [items, setItems] = useState<UploadItem[]>([])
  const [targetFolder, setTargetFolder] = useState<number | undefined>(folderId)
  const [dragging, setDragging] = useState(false)
  const [running, setRunning] = useState(false)
  const [confirming, setConfirming] = useState(false)
  const [folderError, setFolderError] = useState<string>()

  useEffect(() => {
    if (open && !wasOpen.current) {
      setItems(initialFiles ? createUploadItems(initialFiles) : [])
      setTargetFolder(folderId)
      setRunning(false)
      setConfirming(false)
      setFolderError(undefined)
      createdFolders.current.clear()
    }
    wasOpen.current = open
  }, [open, folderId, initialFiles])

  const summary = summarizeUploads(items)
  const uploadable = items.filter(isUploadable)
  const directoryMode = items.some((item) => !!item.file.webkitRelativePath)
  const locked = running || createdFolders.current.size > 0
  let directories: string[] = []
  let pathError: string | undefined
  try { directories = folderPaths(items.map((item) => item.file)) }
  catch { pathError = 'knowledge.upload.folderPathInvalid' }

  const update = (id: string, patch: Partial<UploadItem>) =>
    setItems((current) => current.map((item) => (item.id === id ? { ...item, ...patch } : item)))

  const addFiles = (files: FileList | File[] | null) => {
    // Copy now: a FileList is live and empties once the input is reset below.
    const added = createUploadItems(Array.from(files ?? []))
    if (added.length > 0) {
      setItems((current) => [...current, ...added])
    }
  }

  const uploadOne = async ({ item, bundle, error: planningError }: UploadPlanEntry) => {
    if (planningError) {
      update(item.id, { status: 'failed', error: t(planningError.key, { path: planningError.path }), errorIsKey: false })
      return false
    }
    update(item.id, { status: 'uploading', progress: 0, error: undefined, errorIsKey: false })
    try {
      const path = uploadPath(item.file)
      const parentPath = path.includes('/') ? path.slice(0, path.lastIndexOf('/')) : undefined
      const destination = parentPath ? createdFolders.current.get(parentPath) : targetFolder
      if (directoryMode && parentPath && destination === undefined) throw new Error(t('knowledge.upload.folderPathInvalid'))
      await knowledgeApi.uploadDocument(namespace, base, { file: item.file, folderId: destination, ...bundle }, {
        onProgress: (progress) => update(item.id, { progress }),
      })
      update(item.id, { status: 'done', progress: 1 })
      return true
    } catch (error) {
      update(item.id, { status: 'failed', error: knowledgeErrorMessage(error), errorIsKey: false })
      return false
    }
  }

  const uploadAll = async (queue: UploadItem[]) => {
    if (busy.current) return
    busy.current = true
    setRunning(true)
    setConfirming(false)
    setFolderError(undefined)
    try {
      const plan = await planUploads(items, undefined, directoryMode)
      if (directoryMode) {
        const invalid = plan.find((entry) => entry.error)
        if (invalid?.error) {
          setFolderError(t(invalid.error.key, { path: invalid.error.path }))
          return
        }
        const existing = await knowledgeApi.listFolders(namespace, base)
        await createUploadFolders(directories, targetFolder, existing, createdFolders.current,
          (name, parentId) => knowledgeApi.createFolder(namespace, base, { name, parentId }))
      }
      const queuedIds = new Set(queue.map((item) => item.id))
      const pending = plan.filter((entry) => entry.item.status !== 'done' && (queuedIds.has(entry.item.id) || entry.bundle.images.some((image) => queuedIds.has(image.itemId))))
      const completedIds = new Set(items.filter((item) => item.status === 'done').map((item) => item.id))
      const uploadedNames: string[] = []
      let succeeded = 0
      for (const entry of pending) {
        if (await uploadOne(entry)) {
          succeeded += 1
          completedIds.add(entry.item.id)
          uploadedNames.push(entry.item.file.name)
        } else completedIds.delete(entry.item.id)
      }
      for (const item of directoryMode ? [] : items) {
        const parents = plan.filter((entry) => entry.bundle.images.some((image) => image.itemId === item.id))
        if (parents.length) {
          const done = parents.every((entry) => completedIds.has(entry.item.id))
          if (!item.errorIsKey) update(item.id, { status: done ? 'done' : 'waiting', progress: done ? 1 : 0 })
          if (done) completedIds.add(item.id)
          else completedIds.delete(item.id)
        }
      }
      if (succeeded > 0) {
        onUploaded?.({ fileNames: uploadedNames, folderId: targetFolder })
        if (items.every((item) => completedIds.has(item.id))) {
          onOpenChange(false)
        }
        toast.success(t('knowledge.upload.allDone'), t('knowledge.upload.summary', { done: succeeded, total: pending.length }))
      }
    } catch (error) {
      const message = error instanceof Error && error.message.startsWith('knowledge.upload.')
        ? t(error.message) : knowledgeErrorMessage(error)
      setFolderError(message)
    } finally {
      busy.current = false
      setRunning(false)
      await invalidate()
    }
  }

  const allFinished = items.length > 0 && !running && uploadable.length === 0

  return (
    <Dialog open={open} onOpenChange={(next) => !running && onOpenChange(next)}>
      <DialogContent className="w-[min(calc(100vw-2rem),40rem)]">
        <DialogHeader>
          <DialogTitle>{t(confirming ? 'knowledge.upload.confirmFolderTitle' : 'knowledge.upload.title')}</DialogTitle>
          <DialogDescription>{t(confirming ? 'knowledge.upload.confirmFolderHint' : 'knowledge.upload.hint')}</DialogDescription>
        </DialogHeader>

        <div className="space-y-2">
          <Label htmlFor="kb-upload-folder">{t('knowledge.upload.targetFolder')}</Label>
          <FolderSelect id="kb-upload-folder" tree={tree} value={targetFolder} onChange={setTargetFolder} disabled={locked || confirming} />
        </div>

        <input
          ref={inputRef}
          type="file"
          multiple
          className="hidden"
          accept={KNOWLEDGE_ACCEPTED_EXTENSIONS.map((extension) => `.${extension}`).join(',')}
          onChange={(event) => {
            addFiles(event.target.files)
            event.target.value = ''
          }}
        />
        <input ref={directoryRef} type="file" multiple className="hidden"
          {...{ webkitdirectory: '', directory: '' }}
          onChange={(event) => {
            if (!locked) { setItems(createUploadItems(Array.from(event.target.files ?? []))); setFolderError(undefined) }
            event.target.value = ''
          }} />
        {!confirming ? <Button type="button" variant="outline" disabled={locked} onClick={() => directoryRef.current?.click()}>
          <FolderUp className="mr-2 h-4 w-4" aria-hidden />{t('knowledge.upload.selectFolder')}
        </Button> : null}
        {!confirming ? <>
        <button
          type="button"
          disabled={locked}
          onClick={() => inputRef.current?.click()}
          onDragOver={(event) => {
            event.preventDefault()
            setDragging(true)
          }}
          onDragLeave={() => setDragging(false)}
          onDrop={(event) => {
            event.preventDefault()
            setDragging(false)
            if (!locked) addFiles(event.dataTransfer.files)
          }}
          className={cn(
            'flex w-full flex-col items-center justify-center gap-2 rounded-xl border border-dashed p-6 text-center transition-colors',
            dragging ? 'border-primary bg-primary/5' : 'border-border/80 bg-secondary/30 hover:border-primary/50 hover:bg-secondary/50',
          )}
        >
          <UploadCloud className="h-7 w-7 text-muted-foreground" aria-hidden />
          <span className="text-sm text-foreground/80">{t('knowledge.upload.dropzone')}</span>
        </button>
        <p className="text-xs leading-5 text-muted-foreground">{t('knowledge.upload.markdownHint')}</p>
        </> : null}
        {directoryMode ? <p className="text-sm text-muted-foreground">{t('knowledge.upload.folderSummary', { folders: directories.length, files: items.length })}</p> : null}
        {pathError || folderError ? <p role="alert" className="text-sm text-destructive">{pathError ? t(pathError) : folderError}</p> : null}
        {confirming ? <ul className="max-h-64 overflow-auto rounded-lg border p-3" aria-label={t('knowledge.upload.directoryPreview')}>
          {[...directories.map((path) => ({ path, folder: true })), ...items.map((item) => ({ path: uploadPath(item.file), folder: false }))]
            .sort((a, b) => a.path.localeCompare(b.path)).map(({ path, folder }) => (
              <li key={`${folder ? 'folder' : 'file'}:${path}`} style={{ paddingInlineStart: `${(path.split('/').length - 1) * 16}px` }} className="truncate text-sm leading-7" title={path}>
                {folder ? <FolderUp className="mr-2 inline h-4 w-4" aria-hidden /> : null}{path.split('/').at(-1)}{folder ? '/' : ''}
              </li>
            ))}
        </ul> : null}

        {items.length > 0 && !confirming ? (
          <ul className="max-h-72 space-y-2 overflow-y-auto pr-1" aria-live="polite">
            {items.map((item) => (
              <li key={item.id} className="flex items-center gap-3 rounded-lg border border-border/50 bg-background/60 px-3 py-2">
                <KnowledgeFileIcon extension={fileExtension(item.file.name)} />
                <div className="min-w-0 flex-1">
                  <p className="truncate text-sm font-medium text-foreground" title={uploadPath(item.file)}>{uploadPath(item.file)}</p>
                  <p className={cn('truncate text-xs', item.status === 'failed' ? 'text-destructive' : 'text-muted-foreground')}>
                    {formatFileSize(item.file.size)}
                    {' · '}
                    {item.status === 'waiting' && t('knowledge.upload.waiting')}
                    {item.status === 'uploading' && t('knowledge.upload.uploading', { percent: Math.round(item.progress * 100) })}
                    {item.status === 'done' && t('knowledge.upload.done')}
                    {item.status === 'failed' && (item.errorIsKey && item.error ? t(item.error) : item.error ?? t('knowledge.upload.failed'))}
                  </p>
                  {item.status === 'uploading' ? (
                    <div className="mt-1.5 h-1 overflow-hidden rounded-full bg-secondary">
                      <div className="h-full bg-primary transition-[width]" style={{ width: `${Math.round(item.progress * 100)}%` }} />
                    </div>
                  ) : null}
                </div>
                {item.status === 'uploading' ? <Loader2 className="h-4 w-4 animate-spin text-muted-foreground" aria-hidden /> : null}
                {item.status === 'done' ? <CheckCircle2 className="h-4 w-4 text-emerald-600" aria-hidden /> : null}
                {item.status === 'failed' && !item.errorIsKey && !running ? (
                  <Button variant="ghost" size="icon" className="h-7 w-7" aria-label={t('knowledge.upload.retry')} onClick={() => void uploadAll([item])}>
                    <RotateCcw className="h-3.5 w-3.5" />
                  </Button>
                ) : null}
                {item.status === 'failed' && item.errorIsKey ? <XCircle className="h-4 w-4 text-destructive" aria-hidden /> : null}
                {(item.status === 'waiting' || item.status === 'failed') && !locked ? (
                  <Button
                    variant="ghost"
                    size="icon"
                    className="h-7 w-7"
                    aria-label={t('knowledge.upload.remove')}
                    onClick={() => setItems((current) => current.filter((candidate) => candidate.id !== item.id))}
                  >
                    <X className="h-3.5 w-3.5" />
                  </Button>
                ) : null}
              </li>
            ))}
          </ul>
        ) : null}

        <DialogFooter className="items-center sm:justify-between">
          <span className="text-xs text-muted-foreground">
            {items.length > 0 ? t('knowledge.upload.summary', { done: summary.done, total: summary.total }) : null}
          </span>
          <div className="flex gap-2">
            {allFinished ? (
              <Button type="button" onClick={() => onOpenChange(false)}>
                {t('knowledge.upload.close')}
              </Button>
            ) : (
              <>
                <Button type="button" variant="outline" disabled={running} onClick={() => confirming ? setConfirming(false) : onOpenChange(false)}>
                  {t(confirming ? 'knowledge.upload.back' : 'dialog.cancel')}
                </Button>
                <Button type="button" disabled={running || uploadable.length === 0 || !!pathError || (directoryMode && items.some((item) => item.errorIsKey))} onClick={() => {
                  if (directoryMode && !confirming && createdFolders.current.size === 0) setConfirming(true)
                  else void uploadAll(uploadable)
                }}>
                  {running ? <Loader2 className="mr-2 h-4 w-4 animate-spin" aria-hidden /> : null}
                  {t(directoryMode && !confirming && createdFolders.current.size === 0 ? 'knowledge.upload.reviewFolder' : confirming ? 'knowledge.upload.confirmFolder' : 'knowledge.upload.start', { count: uploadable.length })}
                </Button>
              </>
            )}
          </div>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
