import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { knowledgeApi } from '@/api/client'
import type { KnowledgeShareVisibility } from '@/api/knowledge-types'
import { useAuth } from '@/features/auth/use-auth'
import { VisitorShareDialog } from '@/shared/components/visitor-share-dialog'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/shared/ui/select'
import { flattenFolderTree, type KnowledgeFolderNode } from './folder-tree'
import { knowledgeKeys, useInvalidateKnowledge } from './use-knowledge-queries'

const MODES: KnowledgeShareVisibility[] = ['INHERIT', 'PRIVATE', 'ENTRY', 'PUBLIC']

type ShareDialogProps = { onClose: () => void } & (
  | { namespace: string; base: string; tree: KnowledgeFolderNode[] }
  | { documentId: number; documentTitle: string }
)

export function KnowledgeShareDialog({ onClose, ...target }: ShareDialogProps) {
  const { t } = useTranslation()
  const { user } = useAuth()
  const invalidate = useInvalidateKnowledge()
  const [rootDraft, setRootDraft] = useState<boolean>()
  const [modes, setModes] = useState<Record<number, KnowledgeShareVisibility>>({})
  const fileOnly = 'documentId' in target
  const resourceKey = fileOnly ? knowledgeKeys.document(target.documentId) : knowledgeKeys.base(target.namespace, target.base)
  const folders = fileOnly ? [] : flattenFolderTree(target.tree)

  return (
    <VisitorShareDialog
      title={t(fileOnly ? 'knowledge.share.fileSettings' : 'knowledge.share.settings')}
      description={fileOnly
        ? t('knowledge.share.fileSettingsDescription', { title: target.documentTitle })
        : t('knowledge.share.settingsDescription')}
      queryKey={[...resourceKey, 'share-settings', user?.userId]}
      loadSettings={() => fileOnly
        ? knowledgeApi.getDocumentShareSettings(target.documentId)
        : knowledgeApi.getShareSettings(target.namespace, target.base)}
      saveSettings={(update) => fileOnly
        ? knowledgeApi.updateDocumentShareSettings(target.documentId, update)
        : knowledgeApi.updateShareSettings(target.namespace, target.base, {
          ...update,
          shareRootFiles: rootDraft,
          folders: folders.filter(({ folder }) => modes[folder.id] && modes[folder.id] !== (folder.shareVisibility ?? 'INHERIT'))
            .map(({ folder }) => ({ folderId: folder.id, visibility: modes[folder.id] })),
        })}
      sharePath={(id) => `/knowledge-share/${id}`}
      onClose={onClose}
      onSaved={async () => {
        await invalidate()
        setRootDraft(undefined)
        setModes({})
      }}
      renderScope={fileOnly ? undefined : (settings, pending) => (
        <fieldset className="space-y-2" disabled={pending}>
          <legend className="text-sm font-semibold">{t('knowledge.share.scope')}</legend>
          <label className="flex items-center gap-2 text-sm font-medium">
            <input type="checkbox" checked={rootDraft ?? Boolean(settings.shareRootFiles)}
              onChange={(event) => setRootDraft(event.target.checked)} className="h-4 w-4 accent-primary" />
            {t('knowledge.share.rootFiles')}
          </label>
          <p className="text-xs text-muted-foreground">{t('knowledge.share.rootFilesDescription')}</p>
          {folders.length > 0 ? <>
            <p className="pt-2 text-sm font-medium">{t('knowledge.share.folderVisibility')}</p>
            <p className="text-xs text-muted-foreground">{t('knowledge.share.inheritance')}</p>
          <div className="divide-y divide-border rounded-lg border border-border">
            {folders.map(({ folder, depth }) => {
              const mode = modes[folder.id] ?? folder.shareVisibility ?? 'INHERIT'
              return (
              <div key={folder.id} className="flex flex-wrap items-center gap-x-3 gap-y-1 px-3 py-2">
                <span className="min-w-[8rem] flex-1 break-words text-sm" style={{ paddingInlineStart: `${Math.min(depth, 4) * 0.75}rem` }}>
                  {folder.name}
                </span>
                <Select
                  value={depth === 0 && mode === 'INHERIT' ? 'PRIVATE' : mode}
                  disabled={pending}
                  onValueChange={(value) => setModes((previous) => ({ ...previous, [folder.id]: value as KnowledgeShareVisibility }))}
                >
                  <SelectTrigger className="w-[10rem]" aria-label={t('knowledge.share.folderSetting', { name: folder.name })}>
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    {MODES.filter((mode) => mode !== 'INHERIT' || depth > 0).map((mode) => (
                      <SelectItem key={mode} value={mode}>
                        {t(`knowledge.share.visibility.${mode}`)}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
              )
            })}
          </div>
          </> : null}
        </fieldset>
      )}
    />
  )
}
