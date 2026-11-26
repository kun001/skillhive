import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { knowledgeApi } from '@/api/client'
import type { KnowledgeShareVisibility, UpdateKnowledgeShareRequest } from '@/api/knowledge-types'
import { useAuth } from '@/features/auth/use-auth'
import { CopyButton } from '@/shared/components/copy-button'
import { withBasePath } from '@/shared/lib/base-path'
import { toLocalDateTimeInputValue } from '@/shared/lib/date-time'
import { toast } from '@/shared/lib/toast'
import { Button } from '@/shared/ui/button'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/shared/ui/dialog'
import { Input } from '@/shared/ui/input'
import { Label } from '@/shared/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/shared/ui/select'
import { knowledgeErrorMessage } from './errors'
import { flattenFolderTree, type KnowledgeFolderNode } from './folder-tree'
import { knowledgeKeys, useInvalidateKnowledge } from './use-knowledge-queries'

const MODES: KnowledgeShareVisibility[] = ['INHERIT', 'PRIVATE', 'ENTRY', 'PUBLIC']

type ShareDialogProps = { onClose: () => void } & (
  | { namespace: string; base: string; tree: KnowledgeFolderNode[] }
  | { documentId: number; documentTitle: string }
)

export function KnowledgeShareDialog({ onClose, ...target }: ShareDialogProps) {
  const { t } = useTranslation()
  const { user } = useAuth()
  const client = useQueryClient()
  const invalidate = useInvalidateKnowledge()
  const fileOnly = 'documentId' in target
  const resourceKey = fileOnly ? knowledgeKeys.document(target.documentId) : knowledgeKeys.base(target.namespace, target.base)
  const queryKey = [...resourceKey, 'share-settings', user?.userId]
  const settings = useQuery({
    queryKey,
    queryFn: () => fileOnly ? knowledgeApi.getDocumentShareSettings(target.documentId) : knowledgeApi.getShareSettings(target.namespace, target.base),
    staleTime: 0,
    gcTime: 0,
    retry: false,
  })
  const [enabledDraft, setEnabledDraft] = useState<boolean>()
  const [expirationDraft, setExpirationDraft] = useState<string | null>()
  const [rootDraft, setRootDraft] = useState<boolean>()
  const [modes, setModes] = useState<Record<number, KnowledgeShareVisibility>>({})
  const enabled = enabledDraft ?? Boolean(settings.data?.shareId)
  const savedExpiration = settings.data?.expiresAt
  const expired = Boolean(savedExpiration && Date.parse(savedExpiration) <= Date.now())
  const dateValue = expirationDraft === undefined
    ? (savedExpiration ? toLocalDateTimeInputValue(savedExpiration) : null)
    : expirationDraft
  const chosenTime = expirationDraft == null ? undefined : new Date(expirationDraft)
  const invalidExpiration = enabled && chosenTime !== undefined && !(chosenTime.getTime() > Date.now())
  const link = settings.data?.shareId && !expired
    ? window.location.origin + withBasePath(`/knowledge-share/${settings.data.shareId}`)
    : undefined
  const folders = fileOnly ? [] : flattenFolderTree(target.tree)

  const save = useMutation({
    mutationFn: (request: UpdateKnowledgeShareRequest) => fileOnly
      ? knowledgeApi.updateDocumentShareSettings(target.documentId, { enabled: request.enabled, expiration: request.expiration })
      : knowledgeApi.updateShareSettings(target.namespace, target.base, request),
    onSuccess: async (result) => {
      client.setQueryData(queryKey, result)
      await invalidate()
      setEnabledDraft(undefined)
      setExpirationDraft(undefined)
      setRootDraft(undefined)
      setModes({})
      toast.success(t('knowledge.share.saved'))
    },
    onError: (error) => toast.error(knowledgeErrorMessage(error)),
  })
  const submit = () => {
    if (!settings.data || save.isPending || invalidExpiration) return
    save.mutate({
      enabled,
      expiration: enabled && expirationDraft !== undefined ? { expiresAt: chosenTime?.toISOString() } : undefined,
      shareRootFiles: rootDraft,
      folders: folders.filter(({ folder }) => modes[folder.id] && modes[folder.id] !== (folder.shareVisibility ?? 'INHERIT'))
        .map(({ folder }) => ({ folderId: folder.id, visibility: modes[folder.id] })),
    })
  }

  return (
    <Dialog open onOpenChange={(open) => !open && !save.isPending && onClose()}>
      <DialogContent className="max-h-[85vh] max-w-[40rem] overflow-y-auto">
        <DialogHeader>
          <DialogTitle>{t(fileOnly ? 'knowledge.share.fileSettings' : 'knowledge.share.settings')}</DialogTitle>
          <DialogDescription>{fileOnly
            ? t('knowledge.share.fileSettingsDescription', { title: target.documentTitle })
            : t('knowledge.share.settingsDescription')}</DialogDescription>
        </DialogHeader>
        {settings.isPending ? <p role="status">{t('knowledge.preview.loading')}</p> : null}
        {settings.isError ? <p role="alert">{knowledgeErrorMessage(settings.error)}</p> : null}
        {settings.data ? (
          <form className="space-y-4" onSubmit={(event) => { event.preventDefault(); submit() }}>
            <label className="flex items-center gap-2 text-sm font-medium">
              <input type="checkbox" checked={enabled} onChange={(event) => setEnabledDraft(event.target.checked)} disabled={save.isPending} className="h-4 w-4 accent-primary" />
              {t('knowledge.share.enable')}
            </label>
            {enabled ? (
              <div className="grid gap-2 sm:grid-cols-[10rem_minmax(0,1fr)] sm:items-end">
                <div className="space-y-1.5">
                  <Label htmlFor="share-expiration-mode">{t('knowledge.share.expiration')}</Label>
                  <Select value={dateValue === null ? 'never' : 'date'} disabled={save.isPending}
                    onValueChange={(mode) => setExpirationDraft(mode === 'date'
                      ? toLocalDateTimeInputValue(new Date(Date.now() + 24 * 60 * 60 * 1000))
                      : null)}
                  >
                    <SelectTrigger id="share-expiration-mode"><SelectValue /></SelectTrigger>
                    <SelectContent>
                      <SelectItem value="never">{t('knowledge.share.neverExpires')}</SelectItem>
                      <SelectItem value="date">{t('knowledge.share.customExpiration')}</SelectItem>
                    </SelectContent>
                  </Select>
                </div>
                {dateValue !== null ? (
                  <div className="space-y-1.5">
                    <Label htmlFor="share-expiration-date">{t('knowledge.share.localExpiration')}</Label>
                    <Input id="share-expiration-date" type="datetime-local" value={dateValue}
                      onChange={(event) => setExpirationDraft(event.target.value)}
                      disabled={save.isPending} aria-invalid={invalidExpiration || undefined} />
                  </div>
                ) : null}
                {invalidExpiration ? (
                  <p role="alert" className="text-xs text-destructive sm:col-span-2">{t('knowledge.share.expirationFuture')}</p>
                ) : null}
                {expired ? (
                  <p className="text-xs text-muted-foreground sm:col-span-2">{t('knowledge.share.expiredRenewal')}</p>
                ) : null}
              </div>
            ) : null}
            {link ? (
              <div className="flex flex-wrap items-center gap-2">
                <Input aria-label={t('knowledge.share.link')} value={link} readOnly className="min-w-[12rem] flex-1" />
                <CopyButton text={link} ariaLabel={t('knowledge.share.copy')} />
                <a href={link} target="_blank" rel="noreferrer" className="text-sm font-medium text-primary underline">
                  {t('knowledge.share.openLink')}
                </a>
              </div>
            ) : null}
            {!fileOnly ? <fieldset className="space-y-2" disabled={save.isPending}>
              <legend className="text-sm font-semibold">{t('knowledge.share.scope')}</legend>
              <label className="flex items-center gap-2 text-sm font-medium">
                <input type="checkbox" checked={rootDraft ?? Boolean(settings.data.shareRootFiles)}
                  onChange={(event) => setRootDraft(event.target.checked)} className="h-4 w-4 accent-primary" />
                {t('knowledge.share.rootFiles')}
              </label>
              <p className="text-xs text-muted-foreground">{t('knowledge.share.rootFilesDescription')}</p>
              {folders.length > 0 ? <>
                <p className="pt-2 text-sm font-medium">{t('knowledge.share.folderVisibility')}</p>
                <p className="text-xs text-muted-foreground">{t('knowledge.share.inheritance')}</p>
              <div className="divide-y divide-border rounded-lg border border-border">
                {folders.map(({ folder, depth }) => {
                  const mode = modes[folder.id] ?? folder.shareVisibility ?? 'INHERIT'
                  return (
                  <div key={folder.id} className="flex flex-wrap items-center gap-x-3 gap-y-1 px-3 py-2">
                    <span className="min-w-[8rem] flex-1 break-words text-sm" style={{ paddingInlineStart: `${Math.min(depth, 4) * 0.75}rem` }}>
                      {folder.name}
                    </span>
                    <Select
                      value={depth === 0 && mode === 'INHERIT' ? 'PRIVATE' : mode}
                      disabled={save.isPending}
                      onValueChange={(value) => setModes((previous) => ({ ...previous, [folder.id]: value as KnowledgeShareVisibility }))}
                    >
                      <SelectTrigger className="w-[10rem]" aria-label={t('knowledge.share.folderSetting', { name: folder.name })}>
                        <SelectValue />
                      </SelectTrigger>
                      <SelectContent>
                        {MODES.filter((mode) => mode !== 'INHERIT' || depth > 0).map((mode) => (
                          <SelectItem key={mode} value={mode}>
                            {t(`knowledge.share.visibility.${mode}`)}
                          </SelectItem>
                        ))}
                      </SelectContent>
                    </Select>
                  </div>
                  )
                })}
              </div>
              </> : null}
            </fieldset> : null}
            <DialogFooter>
              <Button type="button" variant="outline" onClick={onClose} disabled={save.isPending}>{t('dialog.cancel')}</Button>
              <Button type="submit" disabled={save.isPending || invalidExpiration}>{t('knowledge.baseDialog.save')}</Button>
            </DialogFooter>
          </form>
        ) : null}
      </DialogContent>
    </Dialog>
  )
}
