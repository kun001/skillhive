import { useTranslation } from 'react-i18next'
import { useCopyToClipboard } from '@/shared/lib/clipboard'
import { withBasePath } from '@/shared/lib/base-path'
import { Button } from '@/shared/ui/button'
import { toast } from '@/shared/lib/toast'

/** Configuration contains the newly issued token only while the existing token dialog is open. */
export function McpConnection({ token, scopes }: { token?: string; scopes?: readonly string[] }) {
  const { t } = useTranslation()
  const [copied, copy] = useCopyToClipboard()
  const endpoint = new URL(withBasePath('/api/v1/mcp'), window.location.origin).toString()
  const readOnly = !scopes || scopes.length > 0 && scopes.every((scope) => (scopes.includes('mcp:access')
    ? ['mcp:access', 'skill:read', 'skill:upload', 'skill:delete', 'knowledge:read', 'knowledge:upload', 'knowledge:delete']
    : ['skill:read', 'knowledge:read']).includes(scope))
  const config = JSON.stringify({ mcpServers: { skillhive: {
    type: 'http', url: endpoint, headers: { Authorization: `Bearer ${token ?? '<YOUR_API_TOKEN>'}` },
  } } }, null, 2)
  return <section className="space-y-3 rounded-xl border border-border/60 bg-secondary/20 p-4">
    <h3 className="text-sm font-semibold">{t('mcp.title')}</h3>
    <p className="text-xs font-medium">{t('mcp.endpoint')}</p>
    <code className="block break-all text-sm">{endpoint}</code>
    {readOnly ? <Button variant="outline" size="sm" onClick={() => void copy(config).catch(() => toast.error(t('apiError.unknown')))}>{t(copied ? 'mcp.copied' : 'mcp.copy')}</Button>
      : <p className="text-sm text-destructive">{t('mcp.readOnlyRequired')}</p>}
  </section>
}
