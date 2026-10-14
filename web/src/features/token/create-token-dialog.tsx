import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { tokenApi } from '@/api/client'
import { useCopyToClipboard } from '@/shared/lib/clipboard'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from '@/shared/ui/dialog'
import { Button } from '@/shared/ui/button'
import { Input } from '@/shared/ui/input'
import { Label } from '@/shared/ui/label'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/shared/ui/select'
import { centeredToastOptions, toast } from '@/shared/lib/toast'
import { formatLocalDateTime } from '@/shared/lib/date-time'
import type { CreateTokenRequest, CreateTokenResponse, Namespace } from '@/api/types'
import { resolveTokenExpiresAt, toLocalDateTimeInputValue, type TokenExpirationMode } from './token-expiration'
import { McpConnection } from './mcp-connection'
import { McpGrantSummary } from './mcp-grant-summary'
import { TokenPermissionGrid } from './token-permission-grid'
import { useMyNamespaces } from '@/features/namespace/use-my-namespaces'
import { useKnowledgeBases } from '@/features/knowledge/use-knowledge-queries'
import { useNamespaceDetail } from '@/shared/hooks/use-namespace-queries'
import {
  DEFAULT_TOKEN_SCOPE_SELECTION,
  domainsForScopes,
  scopesForSelection,
  type TokenScopeSelection,
} from './token-scopes'

interface CreateTokenDialogProps {
  children: React.ReactNode
  existingNames?: string[]
}

const MAX_TOKEN_NAME_LENGTH = 64

/**
 * Handles API token creation, including duplicate-name checks, expiration
 * selection, and the one-time reveal of the raw token value after creation.
 */
export function CreateTokenDialog({ children, existingNames = [] }: CreateTokenDialogProps) {
  const { t, i18n } = useTranslation()
  const [open, setOpen] = useState(false)
  const [name, setName] = useState('')
  const [createdToken, setCreatedToken] = useState<CreateTokenResponse | null>(null)
  const [nameError, setNameError] = useState<string | null>(null)
  const [expirationMode, setExpirationMode] = useState<TokenExpirationMode>('never')
  const [customExpiresAt, setCustomExpiresAt] = useState('')
  const [expiresAtError, setExpiresAtError] = useState<string | null>(null)
  const [scopeSelection, setScopeSelection] = useState<TokenScopeSelection>(DEFAULT_TOKEN_SCOPE_SELECTION)
  const [scopeError, setScopeError] = useState<string | null>(null)
  const [namespaceIds, setNamespaceIds] = useState<number[]>([])
  const [limitBases, setLimitBases] = useState(false)
  const [knowledgeBaseIds, setKnowledgeBaseIds] = useState<number[]>([])
  const namespaces = useMyNamespaces(open)
  const publicNamespace = useNamespaceDetail(open ? 'global' : '')
  const selectableNamespaces: Namespace[] = [...(namespaces.data ?? [])]
  if (publicNamespace.data && !selectableNamespaces.some(item => item.id === publicNamespace.data?.id)) {
    selectableNamespaces.push(publicNamespace.data)
  }
  const bases = useKnowledgeBases(open)
  const [, copy] = useCopyToClipboard()
  const queryClient = useQueryClient()
  const mcpWrites = !!(scopeSelection.skillUpload || scopeSelection.skillDelete || scopeSelection.knowledgeUpload || scopeSelection.knowledgeDelete)
  const effectiveExpiration = mcpWrites && (expirationMode === 'never' || expirationMode === '90d') ? '7d' : expirationMode

  const normalizedName = name.trim()
  const hasDuplicateName = existingNames.some(
    (existingName) => existingName.trim().toLocaleLowerCase() === normalizedName.toLocaleLowerCase()
  )

  const createMutation = useMutation({
    mutationFn: (request: CreateTokenRequest) => tokenApi.createToken(request),
    onSuccess: (data) => {
      setCreatedToken(data)
      setName('')
      setNameError(null)
      setExpirationMode('never')
      setCustomExpiresAt('')
      setExpiresAtError(null)
      setScopeSelection(DEFAULT_TOKEN_SCOPE_SELECTION)
      setScopeError(null)
      queryClient.invalidateQueries({ queryKey: ['tokens'] })
    },
  })

  const handleCreate = () => {
    if (!normalizedName) {
      setNameError(t('createToken.nameRequired'))
      return
    }
    if (normalizedName.length > MAX_TOKEN_NAME_LENGTH) {
      setNameError(t('createToken.nameTooLong', { max: MAX_TOKEN_NAME_LENGTH }))
      return
    }
    if (hasDuplicateName) {
      setNameError(t('createToken.nameDuplicate'))
      return
    }

    const scopes = scopesForSelection(scopeSelection, true)
    if (scopes.length === 0) {
      setScopeError(t('createToken.scopeRequired'))
      return
    }
    if (namespaceIds.length === 0 || limitBases && knowledgeBaseIds.length === 0
        && (scopeSelection.knowledge || scopeSelection.knowledgeUpload || scopeSelection.knowledgeDelete)) {
      setScopeError(t('mcp.resourcesRequired'))
      return
    }

    const expiresAt = resolveTokenExpiresAt(effectiveExpiration, customExpiresAt)
    if (expirationMode === 'custom' && !expiresAt) {
      setExpiresAtError(t('createToken.expiresAtRequired'))
      return
    }
    if (mcpWrites && expiresAt && new Date(expiresAt).getTime() > Date.now() + 30 * 24 * 60 * 60 * 1000) {
      setExpiresAtError(t('mcp.writeExpiryLimit'))
      return
    }

    setNameError(null)
    setExpiresAtError(null)
    setScopeError(null)
    createMutation.mutate({ name: normalizedName, scopes, expiresAt, mcp: true,
      namespaceIds,
      knowledgeBaseIds: limitBases ? knowledgeBaseIds : undefined })
  }

  const handleClose = () => {
    setOpen(false)
    setCreatedToken(null)
    setName('')
    setNameError(null)
    setExpirationMode('never')
    setCustomExpiresAt('')
    setExpiresAtError(null)
    setScopeSelection(DEFAULT_TOKEN_SCOPE_SELECTION)
    setScopeError(null)
    createMutation.reset()
  }

  const handleCopyToken = async () => {
    if (!createdToken) return

    try {
      await copy(createdToken.token)
      toast.success(t('createToken.copySuccess'), undefined, centeredToastOptions())
    } catch (error) {
      console.error('Failed to copy token:', error)
      toast.error(t('createToken.copyFailed'), undefined, centeredToastOptions())
    }
  }

  const formatExpiresAt = (expiresAt?: string) => {
    if (!expiresAt) {
      return t('token.neverExpires')
    }
    return formatLocalDateTime(expiresAt, i18n.language)
  }

  const minDateTime = toLocalDateTimeInputValue(new Date())

  const updateScope = (change: Partial<TokenScopeSelection>) => {
    setScopeSelection((current) => ({ ...current, ...change }))
    setScopeError(null)
  }

  return (
    // Reopening the dialog resets transient creation state because the raw token
    // is only meant to be shown once, immediately after a successful create call.
    <Dialog open={open} onOpenChange={(nextOpen) => {
      if (nextOpen) {
        setNamespaceIds([])
        setLimitBases(false)
        setKnowledgeBaseIds([])
        setCreatedToken(null)
        setName('')
        setNameError(null)
        setExpirationMode('never')
        setCustomExpiresAt('')
        setExpiresAtError(null)
        setScopeSelection(DEFAULT_TOKEN_SCOPE_SELECTION)
        setScopeError(null)
        createMutation.reset()
      }
      setOpen(nextOpen)
    }}>
      <DialogTrigger asChild>{children}</DialogTrigger>
      <DialogContent className="max-h-[90vh] overflow-y-auto">
        {!createdToken ? (
          <>
            <DialogHeader className="text-center">
              <DialogTitle>{t('createToken.title')}</DialogTitle>
              <DialogDescription>
                {t('createToken.description')}
              </DialogDescription>
            </DialogHeader>
            <div className="space-y-4 py-4">
              <div className="space-y-2">
                <Label htmlFor="token-name">{t('createToken.nameLabel')}</Label>
                <Input
                  id="token-name"
                  placeholder={t('createToken.namePlaceholder')}
                  value={name}
                  maxLength={MAX_TOKEN_NAME_LENGTH}
                  onChange={(e) => {
                    setName(e.target.value)
                    if (nameError) {
                      setNameError(null)
                    }
                  }}
                  onKeyDown={(e) => {
                    if (e.key === 'Enter') {
                      handleCreate()
                    }
                  }}
                  aria-invalid={nameError || hasDuplicateName ? 'true' : 'false'}
                />
                <div className="flex items-center justify-between gap-3 text-xs">
                  <span className="text-red-600">
                    {nameError ?? (hasDuplicateName && normalizedName ? t('createToken.nameDuplicate') : '')}
                  </span>
                  <span className="text-muted-foreground">
                    {normalizedName.length}/{MAX_TOKEN_NAME_LENGTH}
                  </span>
                </div>
              </div>

              <fieldset className="space-y-2">
                <legend className="text-sm font-medium leading-none">{t('createToken.scopeLabel')}</legend>
                <TokenPermissionGrid scopes={scopesForSelection(scopeSelection, true)} onChange={(key, checked) => {
                  updateScope(key === 'skillRead' ? { skill: checked ? 'read' : 'none' }
                    : key === 'knowledgeRead' ? { knowledge: checked } : { [key]: checked })
                }} />
                  <fieldset className="space-y-2 rounded-md border p-3">
                    <legend className="text-sm font-medium">{t('mcp.namespaces')}</legend>
                    {selectableNamespaces.filter(item => item.status === 'ACTIVE').map(item => <label key={item.id} className="flex items-center gap-2 text-sm">
                      <input type="checkbox" checked={namespaceIds.includes(item.id)} onChange={event => {
                        setNamespaceIds(current => event.target.checked ? [...current, item.id] : current.filter(id => id !== item.id))
                        setKnowledgeBaseIds([])
                      }} />{item.displayName} <span className="text-muted-foreground">@{item.slug}</span>
                    </label>)}
                    {namespaces.isLoading && <p role="status" className="text-sm">{t('mcp.loading')}</p>}
                    {namespaces.isError && <p role="alert" className="text-sm text-destructive">{t('mcp.loadFailed')}</p>}
                  </fieldset>
                  {(scopeSelection.knowledge || scopeSelection.knowledgeUpload || scopeSelection.knowledgeDelete) && <fieldset className="space-y-2 rounded-md border p-3">
                    <label className="flex items-center gap-2 text-sm"><input type="checkbox" checked={limitBases} onChange={event => setLimitBases(event.target.checked)} />{t('mcp.limitBases')}</label>
                    {limitBases && bases.data?.filter(base => namespaces.data?.some(item => namespaceIds.includes(item.id) && item.slug === base.namespace)).map(base => <label key={base.id} className="flex items-center gap-2 text-sm">
                      <input type="checkbox" checked={knowledgeBaseIds.includes(base.id)} onChange={event => setKnowledgeBaseIds(current => event.target.checked ? [...current, base.id] : current.filter(id => id !== base.id))} />{base.displayName}
                    </label>)}
                  </fieldset>}
                {scopeError ? <p className="text-xs text-red-600">{scopeError}</p> : null}
              </fieldset>

              <div className="space-y-2">
                <Label htmlFor="token-expiration">{t('createToken.expirationLabel')}</Label>
                <Select
                  value={effectiveExpiration}
                  onValueChange={(value) => {
                    setExpirationMode(value as TokenExpirationMode)
                    setExpiresAtError(null)
                  }}
                >
                  <SelectTrigger id="token-expiration">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="never" disabled={mcpWrites}>{t('createToken.expirationNever')}</SelectItem>
                    <SelectItem value="7d">{t('createToken.expiration7d')}</SelectItem>
                    <SelectItem value="30d">{t('createToken.expiration30d')}</SelectItem>
                    <SelectItem value="90d" disabled={mcpWrites}>{t('createToken.expiration90d')}</SelectItem>
                    <SelectItem value="custom">{t('createToken.expirationCustom')}</SelectItem>
                  </SelectContent>
                </Select>
                {expirationMode === 'custom' ? (
                  <Input
                    id="token-custom-expiration"
                    type="datetime-local"
                    value={customExpiresAt}
                    min={minDateTime}
                    max={mcpWrites ? toLocalDateTimeInputValue(new Date(Date.now() + 30 * 24 * 60 * 60 * 1000)) : undefined}
                    onChange={(e) => {
                      setCustomExpiresAt(e.target.value)
                      if (expiresAtError) {
                        setExpiresAtError(null)
                      }
                    }}
                  />
                ) : null}
                {expiresAtError ? (
                  <p className="text-xs text-red-600">{expiresAtError}</p>
                ) : (
                  <p className="text-xs text-muted-foreground">{t(mcpWrites ? 'mcp.writeExpiryHint' : 'createToken.expirationHint')}</p>
                )}
              </div>
            </div>
            {createMutation.error ? (
              <p className="text-sm text-red-600">{createMutation.error.message}</p>
            ) : null}
            <DialogFooter className="sm:justify-center sm:space-x-3">
              <Button variant="outline" onClick={handleClose}>
                {t('dialog.cancel')}
              </Button>
              <Button
                onClick={handleCreate}
                disabled={!normalizedName || hasDuplicateName || createMutation.isPending
                  || scopesForSelection(scopeSelection, true).length === 0}
              >
                {createMutation.isPending ? t('createToken.creating') : t('createToken.create')}
              </Button>
            </DialogFooter>
          </>
        ) : (
          <>
            <DialogHeader className="min-w-0 text-center">
              <DialogTitle>{t('createToken.successTitle')}</DialogTitle>
              <DialogDescription className="break-words">
                {t('createToken.successDescription')}
              </DialogDescription>
            </DialogHeader>
            <div className="min-w-0 space-y-4 py-4">
              <div className="space-y-2">
                <Label>{t('createToken.tokenLabel')}</Label>
                <div className="overflow-hidden rounded-md bg-muted p-3 font-mono text-sm break-all">
                  {createdToken.token}
                </div>
              </div>
              <McpConnection token={createdToken.token} scopes={createdToken.scopes} />
              <div className="space-y-2">
                <Label>{t('createToken.nameDisplay')}</Label>
                <div className="text-sm break-all">{createdToken.name}</div>
              </div>
              <div className="space-y-2">
                <Label>{t('createToken.scopeLabel')}</Label>
                <div className="text-sm">
                  {domainsForScopes(createdToken.scopes).map((domain) => t(`token.scopeDomain.${domain}`)).join(t('token.scopeSeparator'))}
                </div>
                <McpGrantSummary grants={createdToken.resourceGrants} />
              </div>
              <div className="space-y-2">
                <Label>{t('createToken.expiresAtDisplay')}</Label>
                <div className="text-sm">{formatExpiresAt(createdToken.expiresAt)}</div>
              </div>
            </div>
            <DialogFooter className="sm:justify-center sm:space-x-3">
              <Button onClick={handleCopyToken}>
                {t('createToken.copyToken')}
              </Button>
              <Button variant="outline" onClick={handleClose}>
                {t('dialog.close')}
              </Button>
            </DialogFooter>
          </>
        )}
      </DialogContent>
    </Dialog>
  )
}
