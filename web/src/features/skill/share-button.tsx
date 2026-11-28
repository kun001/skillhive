import { Share2 } from 'lucide-react'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { skillShareApi } from '@/api/client'
import { useAuth } from '@/features/auth/use-auth'
import { VisitorShareDialog } from '@/shared/components/visitor-share-dialog'
import { Button } from '@/shared/ui/button'

export function ShareButton({ skillId, title }: { skillId: number; title: string }) {
  const { t } = useTranslation()
  const { user } = useAuth()
  const [open, setOpen] = useState(false)

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
          saveSettings={(update) => skillShareApi.update(skillId, update)}
          sharePath={(id) => `/skill-share/${id}`}
          onClose={() => setOpen(false)}
        />
      ) : null}
    </>
  )
}
