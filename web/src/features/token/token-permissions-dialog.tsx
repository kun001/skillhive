import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { ShieldCheck } from 'lucide-react'
import type { ApiToken } from '@/api/types'
import { useMyNamespaces } from '@/features/namespace/use-my-namespaces'
import { useNamespaceDetail } from '@/shared/hooks/use-namespace-queries'
import { useKnowledgeBases } from '@/features/knowledge/use-knowledge-queries'
import { Button } from '@/shared/ui/button'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle, DialogTrigger } from '@/shared/ui/dialog'
import { TokenPermissionGrid } from './token-permission-grid'

export function TokenPermissionsDialog({ token }: { token: ApiToken }) {
  const { t } = useTranslation()
  const [open, setOpen] = useState(false)
  const scopes = token.scopes ?? []
  const scoped = scopes.includes('mcp:access')
  const grants = token.resourceGrants
  const hasKnowledge = scopes.some(scope => scope.startsWith('knowledge:'))
  const namespaces = useMyNamespaces(open && !!grants)
  const global = useNamespaceDetail(open && grants ? 'global' : '')
  const bases = useKnowledgeBases(open && !!grants && hasKnowledge && !!grants.knowledgeBaseIds?.length)
  const displayedScopes = scoped ? scopes : scopes.filter(scope => scope === 'skill:read' || scope === 'knowledge:read')
  const legacyExtra = scoped ? [] : scopes.filter(scope => scope !== 'skill:read' && scope !== 'knowledge:read')

  return <Dialog open={open} onOpenChange={setOpen}>
    <DialogTrigger asChild>
      <Button variant="outline" size="sm"><ShieldCheck className="mr-1 h-4 w-4" />{t('mcp.viewPermissions')}</Button>
    </DialogTrigger>
    <DialogContent className="max-h-[90vh] overflow-y-auto">
      <DialogHeader>
        <DialogTitle>{t('token.scopes')}</DialogTitle>
        <DialogDescription className="break-all">{t('mcp.permissionFor', { name: token.name })}</DialogDescription>
      </DialogHeader>
      <div className="space-y-4 py-4">
        <fieldset className="space-y-2">
          <legend className="text-sm font-medium">{t('mcp.operations')}</legend>
          <TokenPermissionGrid scopes={displayedScopes} />
        </fieldset>
        {legacyExtra.length > 0 && <fieldset className="space-y-2 rounded-md border p-3">
          <legend className="text-sm font-medium">{t('mcp.legacyApiPermissions')}</legend>
          <div className="flex flex-wrap gap-2">{legacyExtra.map(scope => <code key={scope} className="rounded bg-muted px-2 py-1 text-xs">{scope}</code>)}</div>
          <p className="text-sm text-muted-foreground">{t('mcp.legacyWriteToken')}</p>
        </fieldset>}
        <fieldset className="space-y-2 rounded-md border p-3">
          <legend className="text-sm font-medium">{t('mcp.namespaces')}</legend>
          {grants ? <>
            {grants.namespaceIds?.map(id => {
              const namespace = namespaces.data?.find(item => item.id === id) ?? (global.data?.id === id ? global.data : undefined)
              return <label key={id} className="flex items-center gap-2 text-sm">
                <input type="checkbox" className="h-4 w-4 accent-primary" checked disabled />
                {namespace?.displayName ?? `#${id}`} {namespace && <span className="text-muted-foreground">@{namespace.slug}</span>}
              </label>
            })}
            {!grants.namespaceIds?.length && <p className="text-sm text-muted-foreground">{t('mcp.noNamespaces')}</p>}
            {namespaces.isError && <p role="alert" className="text-sm text-destructive">{t('mcp.resourceNamesUnavailable')}</p>}
          </> : <p className="text-sm text-muted-foreground">{t(scoped ? 'mcp.grantsUnavailable' : 'mcp.legacyResources')}</p>}
        </fieldset>
        {hasKnowledge && <fieldset className="space-y-2 rounded-md border p-3">
          <legend className="text-sm font-medium">{t('mcp.knowledgeBases')}</legend>
          {!grants ? <p className="text-sm text-muted-foreground">{t(scoped ? 'mcp.grantsUnavailable' : 'mcp.legacyKnowledge')}</p>
            : grants.knowledgeBaseIds == null ? <p className="text-sm text-muted-foreground">{t('mcp.allGrantedBases')}</p>
              : grants.knowledgeBaseIds.length === 0 ? <p className="text-sm text-muted-foreground">{t('mcp.noKnowledgeBases')}</p>
                : grants.knowledgeBaseIds.map(id => {
                  const base = bases.data?.find(item => item.id === id)
                  return <label key={id} className="flex items-center gap-2 text-sm">
                    <input type="checkbox" className="h-4 w-4 accent-primary" checked disabled />
                    {base?.displayName ?? `#${id}`} {base && <span className="text-muted-foreground">@{base.namespace}/{base.slug}</span>}
                  </label>
                })}
          {bases.isError && <p role="alert" className="text-sm text-destructive">{t('mcp.resourceNamesUnavailable')}</p>}
        </fieldset>}
      </div>
      <DialogFooter><Button variant="outline" onClick={() => setOpen(false)}>{t('dialog.close')}</Button></DialogFooter>
    </DialogContent>
  </Dialog>
}
