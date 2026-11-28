import { useMutation, useQuery, useQueryClient, type QueryKey } from '@tanstack/react-query'
import { useState, type ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import type { components } from '@/api/generated/schema'
import { CopyButton } from '@/shared/components/copy-button'
import { withBasePath } from '@/shared/lib/base-path'
import { toLocalDateTimeInputValue } from '@/shared/lib/date-time'
import { toast } from '@/shared/lib/toast'
import { Button } from '@/shared/ui/button'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/shared/ui/dialog'
import { Input } from '@/shared/ui/input'
import { Label } from '@/shared/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/shared/ui/select'

type Settings = { shareId?: string | null; expiresAt?: string | null }
type ShareUpdate = { enabled: boolean; expiration?: components['schemas']['VisitorShareExpiration'] }

interface VisitorShareDialogProps<T extends Settings> {
  title: string
  description: string
  queryKey: QueryKey
  loadSettings: () => Promise<T>
  saveSettings: (update: ShareUpdate) => Promise<T>
  sharePath: (id: string) => string
  onClose: () => void
  onSaved?: () => void | Promise<void>
  renderScope?: (settings: T, pending: boolean) => ReactNode
}

export function VisitorShareDialog<T extends Settings>({
  title, description, queryKey, loadSettings, saveSettings, sharePath, onClose, onSaved, renderScope,
}: VisitorShareDialogProps<T>) {
  const { t } = useTranslation()
  const client = useQueryClient()
  const errorMessage = (error: unknown) => error instanceof Error && error.message ? error.message : t('apiError.unknown')
  const settings = useQuery({ queryKey, queryFn: loadSettings, staleTime: 0, gcTime: 0, retry: false })
  const [enabledDraft, setEnabledDraft] = useState<boolean>()
  const [expirationDraft, setExpirationDraft] = useState<string | null>()
  const enabled = enabledDraft ?? Boolean(settings.data?.shareId)
  const savedExpiration = settings.data?.expiresAt
  const expired = Boolean(savedExpiration && Date.parse(savedExpiration) <= Date.now())
  const dateValue = expirationDraft === undefined
    ? (savedExpiration ? toLocalDateTimeInputValue(savedExpiration) : null)
    : expirationDraft
  const chosenTime = expirationDraft == null ? undefined : new Date(expirationDraft)
  const invalidExpiration = enabled && chosenTime !== undefined && !(chosenTime.getTime() > Date.now())
  const link = settings.data?.shareId && !expired && enabled
    ? window.location.origin + withBasePath(sharePath(settings.data.shareId))
    : undefined

  const save = useMutation({
    mutationFn: saveSettings,
    onSuccess: async (result) => {
      client.setQueryData(queryKey, result)
      await onSaved?.()
      setEnabledDraft(undefined)
      setExpirationDraft(undefined)
      toast.success(t('knowledge.share.saved'))
    },
    onError: (error) => toast.error(errorMessage(error)),
  })
  const submit = () => {
    if (!settings.data || save.isPending || invalidExpiration) return
    save.mutate({
      enabled,
      expiration: enabled && expirationDraft !== undefined ? { expiresAt: chosenTime?.toISOString() } : undefined,
    })
  }

  return (
    <Dialog open onOpenChange={(open) => !open && !save.isPending && onClose()}>
      <DialogContent className="max-h-[85vh] max-w-[40rem] overflow-y-auto">
        <DialogHeader>
          <DialogTitle>{title}</DialogTitle>
          <DialogDescription>{description}</DialogDescription>
        </DialogHeader>
        {settings.isPending ? <p role="status">{t('knowledge.preview.loading')}</p> : null}
        {settings.isError ? <p role="alert">{errorMessage(settings.error)}</p> : null}
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
            {renderScope?.(settings.data, save.isPending)}
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
