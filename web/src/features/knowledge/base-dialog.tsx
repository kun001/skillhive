import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import type { KnowledgeBase } from '@/api/knowledge-types'
import { toast } from '@/shared/lib/toast'
import { Button } from '@/shared/ui/button'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/shared/ui/dialog'
import { Input } from '@/shared/ui/input'
import { Label } from '@/shared/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/shared/ui/select'
import { Textarea } from '@/shared/ui/textarea'
import { knowledgeErrorMessage } from './errors'
import { useCreateKnowledgeBase, useUpdateKnowledgeBase } from './use-knowledge-queries'

interface NamespaceOption {
  slug: string
  displayName: string
}

interface CreateBaseDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  namespaces: NamespaceOption[]
  onCreated: (base: KnowledgeBase) => void
}

export function CreateKnowledgeBaseDialog({ open, onOpenChange, namespaces, onCreated }: CreateBaseDialogProps) {
  const { t } = useTranslation()
  const createBase = useCreateKnowledgeBase()
  const [namespace, setNamespace] = useState('')
  const [displayName, setDisplayName] = useState('')
  const [slug, setSlug] = useState('')
  const [description, setDescription] = useState('')

  useEffect(() => {
    if (open) {
      setNamespace(namespaces.length === 1 ? namespaces[0].slug : '')
      setDisplayName('')
      setSlug('')
      setDescription('')
    }
  }, [open, namespaces])

  const canSubmit = namespace !== '' && displayName.trim() !== '' && !createBase.isPending

  const submit = async () => {
    try {
      const base = await createBase.mutateAsync({
        namespace,
        displayName: displayName.trim(),
        slug: slug.trim() || undefined,
        description: description.trim() || undefined,
      })
      toast.success(t('knowledge.baseDialog.created'))
      onOpenChange(false)
      onCreated(base)
    } catch (error) {
      toast.error(knowledgeErrorMessage(error))
    }
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="w-[min(calc(100vw-2rem),32rem)]">
        <DialogHeader>
          <DialogTitle>{t('knowledge.baseDialog.createTitle')}</DialogTitle>
        </DialogHeader>
        <form
          className="space-y-4"
          onSubmit={(event) => {
            event.preventDefault()
            if (canSubmit) void submit()
          }}
        >
          <div className="space-y-2">
            <Label htmlFor="kb-namespace">{t('knowledge.baseDialog.namespace')}</Label>
            <Select value={namespace} onValueChange={setNamespace}>
              <SelectTrigger id="kb-namespace">
                <SelectValue placeholder={t('knowledge.baseDialog.namespacePlaceholder')} />
              </SelectTrigger>
              <SelectContent>
                {namespaces.map((option) => (
                  <SelectItem key={option.slug} value={option.slug}>
                    {option.displayName} <span className="text-muted-foreground">@{option.slug}</span>
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
          <div className="space-y-2">
            <Label htmlFor="kb-name">{t('knowledge.baseDialog.name')}</Label>
            <Input
              id="kb-name"
              value={displayName}
              maxLength={128}
              placeholder={t('knowledge.baseDialog.namePlaceholder')}
              onChange={(event) => setDisplayName(event.target.value)}
            />
          </div>
          <div className="space-y-2">
            <Label htmlFor="kb-slug">{t('knowledge.baseDialog.slug')}</Label>
            <Input
              id="kb-slug"
              value={slug}
              maxLength={64}
              placeholder={t('knowledge.baseDialog.slugPlaceholder')}
              onChange={(event) => setSlug(event.target.value.toLowerCase())}
            />
            <p className="text-xs text-muted-foreground">{t('knowledge.baseDialog.slugHint')}</p>
          </div>
          <div className="space-y-2">
            <Label htmlFor="kb-description">{t('knowledge.baseDialog.description')}</Label>
            <Textarea
              id="kb-description"
              value={description}
              maxLength={2000}
              rows={3}
              placeholder={t('knowledge.baseDialog.descriptionPlaceholder')}
              onChange={(event) => setDescription(event.target.value)}
            />
          </div>
          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => onOpenChange(false)}>
              {t('dialog.cancel')}
            </Button>
            <Button type="submit" disabled={!canSubmit}>
              {t('knowledge.baseDialog.create')}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}

interface EditBaseDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  base: KnowledgeBase
}

export function EditKnowledgeBaseDialog({ open, onOpenChange, base }: EditBaseDialogProps) {
  const { t } = useTranslation()
  const updateBase = useUpdateKnowledgeBase(base.namespace, base.slug)
  const [displayName, setDisplayName] = useState(base.displayName)
  const [description, setDescription] = useState(base.description ?? '')

  useEffect(() => {
    if (open) {
      setDisplayName(base.displayName)
      setDescription(base.description ?? '')
    }
  }, [open, base])

  const submit = async () => {
    try {
      await updateBase.mutateAsync({ displayName: displayName.trim(), description: description.trim() || undefined })
      toast.success(t('knowledge.baseDialog.saved'))
      onOpenChange(false)
    } catch (error) {
      toast.error(knowledgeErrorMessage(error))
    }
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="w-[min(calc(100vw-2rem),32rem)]">
        <DialogHeader>
          <DialogTitle>{t('knowledge.baseDialog.editTitle')}</DialogTitle>
          <DialogDescription>@{base.namespace} / {base.slug}</DialogDescription>
        </DialogHeader>
        <form
          className="space-y-4"
          onSubmit={(event) => {
            event.preventDefault()
            if (displayName.trim()) void submit()
          }}
        >
          <div className="space-y-2">
            <Label htmlFor="kb-edit-name">{t('knowledge.baseDialog.name')}</Label>
            <Input id="kb-edit-name" value={displayName} maxLength={128} onChange={(event) => setDisplayName(event.target.value)} />
          </div>
          <div className="space-y-2">
            <Label htmlFor="kb-edit-description">{t('knowledge.baseDialog.description')}</Label>
            <Textarea
              id="kb-edit-description"
              value={description}
              maxLength={2000}
              rows={3}
              onChange={(event) => setDescription(event.target.value)}
            />
          </div>
          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => onOpenChange(false)}>
              {t('dialog.cancel')}
            </Button>
            <Button type="submit" disabled={!displayName.trim() || updateBase.isPending}>
              {t('knowledge.baseDialog.save')}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
