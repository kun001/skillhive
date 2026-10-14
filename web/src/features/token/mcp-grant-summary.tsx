import { useTranslation } from 'react-i18next'
import { useMyNamespaces } from '@/features/namespace/use-my-namespaces'
import type { ApiToken } from '@/api/types'

export function McpGrantSummary({ grants }: { grants: ApiToken['resourceGrants'] }) {
  const { t } = useTranslation()
  const namespaces = useMyNamespaces(!!grants)
  if (!grants) return null
  const names = grants.namespaceIds?.map(id => namespaces.data?.find(item => item.id === id)?.displayName ?? `#${id}`).join(t('token.scopeSeparator'))
  return <p className="mt-2 text-xs text-muted-foreground">
    {t('mcp.resourceSummary', { names })}
    {grants.knowledgeBaseIds ? ` · ${t('mcp.baseCount', { count: grants.knowledgeBaseIds.length })}` : ''}
  </p>
}
