import { useTranslation } from 'react-i18next'
import { APP_SHELL_PAGE_CLASS_NAME } from '@/app/page-shell-style'

/** Reserved workspace for the future knowledge domain. */
export function KnowledgePage() {
  const { t } = useTranslation()

  return (
    <div className={APP_SHELL_PAGE_CLASS_NAME}>
      <h1 className="text-3xl font-semibold text-foreground">{t('hiveLanding.knowledgeLabel')}</h1>
      <div className="min-h-[55vh]" aria-hidden="true" />
    </div>
  )
}
