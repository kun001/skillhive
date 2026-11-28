import { Share2 } from 'lucide-react'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { skillShareApi } from '@/api/client'
import type { components } from '@/api/generated/schema'
import { useAuth } from '@/features/auth/use-auth'
import { VisitorShareDialog } from '@/shared/components/visitor-share-dialog'
import { Button } from '@/shared/ui/button'
import { Label } from '@/shared/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/shared/ui/select'

export function ShareButton({ skillId, title }: { skillId: number; title: string }) {
  const { t } = useTranslation()
  const { user } = useAuth()
  const [open, setOpen] = useState(false)
  const [visibility, setVisibility] = useState<components['schemas']['SkillShareSettingsResponse']['visibility']>()

  return (
    <>
      <Button type="button" variant="outline" size="lg" className="w-full" onClick={() => setOpen(true)}>
        <Share2 className="mr-2 h-4 w-4" aria-hidden />
        {t('skillDetail.share.button')}
      </Button>
      {open ? (
        <VisitorShareDialog
          title={t('skillDetail.share.button')}
          description={t('skillDetail.share.description', { title })}
          queryKey={['skill-share-settings', skillId, user?.userId]}
          loadSettings={() => skillShareApi.settings(skillId)}
          saveSettings={(update, settings) => skillShareApi.update(skillId, {
            ...update,
            visibility: update.enabled ? visibility ?? (settings.shareId ? undefined : 'ENTRY') : undefined,
          })}
          sharePath={(id) => `/skill-share/${id}`}
          onClose={() => {
            setVisibility(undefined)
            setOpen(false)
          }}
          onSaved={() => setVisibility(undefined)}
          renderScope={(settings, pending, enabled) => {
            if (!enabled) return null
            const mode = visibility ?? settings.visibility ?? 'ENTRY'
            return (
              <div className="space-y-2">
                <Label htmlFor="skill-share-visibility">{t('skillDetail.share.visibility')}</Label>
                <Select value={mode} onValueChange={(value) => setVisibility(value === 'PUBLIC' ? 'PUBLIC' : 'ENTRY')} disabled={pending}>
                  <SelectTrigger id="skill-share-visibility"><SelectValue /></SelectTrigger>
                  <SelectContent>
                    <SelectItem value="ENTRY">{t('knowledge.share.visibility.ENTRY')}</SelectItem>
                    <SelectItem value="PUBLIC">{t('skillDetail.share.public')}</SelectItem>
                  </SelectContent>
                </Select>
                <p className="text-xs text-muted-foreground">
                  {t(mode === 'ENTRY' ? 'skillDetail.share.entryDescription' : 'skillDetail.share.publicDescription')}
                </p>
              </div>
            )
          }}
        />
      ) : null}
    </>
  )
}
