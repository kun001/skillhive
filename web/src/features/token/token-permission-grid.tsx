import { useTranslation } from 'react-i18next'

const OPERATIONS = [
  { key: 'skillRead', scope: 'skill:read' },
  { key: 'skillUpload', scope: 'skill:upload' },
  { key: 'skillDelete', scope: 'skill:delete' },
  { key: 'knowledgeRead', scope: 'knowledge:read' },
  { key: 'knowledgeUpload', scope: 'knowledge:upload' },
  { key: 'knowledgeDelete', scope: 'knowledge:delete' },
] as const

type PermissionKey = typeof OPERATIONS[number]['key']

/** The same operation labels and layout are used when issuing and inspecting a token. */
export function TokenPermissionGrid({ scopes, onChange }: {
  scopes: readonly string[]
  onChange?: (permission: PermissionKey, granted: boolean) => void
}) {
  const { t } = useTranslation()
  return <div className="grid grid-cols-2 gap-2 rounded-md border p-3">
    {OPERATIONS.map(({ key, scope }) => <label key={scope} className="flex items-center gap-2 text-sm">
      <input type="checkbox" className="h-4 w-4 accent-primary" checked={scopes.includes(scope)}
        disabled={!onChange} onChange={event => onChange?.(key, event.target.checked)} />
      {t(`mcp.${key}`)}
    </label>)}
  </div>
}
