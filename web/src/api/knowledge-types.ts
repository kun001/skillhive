import type { components } from './generated/schema'

type Schemas = components['schemas']

/** Makes the listed keys required and non-null; the backend always populates them. */
type Populated<T, K extends keyof T> = Omit<T, K> & { [P in K]-?: NonNullable<T[P]> }

export type KnowledgePreviewKind = 'PDF' | 'IMAGE' | 'MARKDOWN' | 'TEXT' | 'OFFICE' | 'NONE'


export type KnowledgeMarkdownImages = Omit<Populated<Schemas['KnowledgeMarkdownImagesResponse'], 'sourcePath' | 'images'>, 'images'> & {
  images: Populated<Schemas['Image'], 'id' | 'path'>[]
}

export type KnowledgeUser = Populated<Schemas['KnowledgeUserResponse'], 'id' | 'displayName'>

export type KnowledgeBase = Populated<
  Schemas['KnowledgeBaseResponse'],
  'id' | 'namespace' | 'namespaceDisplayName' | 'slug' | 'displayName' | 'status' | 'documentCount' | 'updatedAt' | 'canManage' | 'canContribute'
>

export type KnowledgeFolder = Populated<Schemas['KnowledgeFolderResponse'], 'id' | 'name' | 'documentCount' | 'canManage'>

export type KnowledgeDocument = Omit<
  Populated<
    Schemas['KnowledgeDocumentResponse'],
    'id' | 'knowledgeBaseId' | 'title' | 'fileName' | 'fileExtension' | 'contentType' | 'sizeBytes' | 'currentVersion' | 'createdAt' | 'updatedAt' | 'canManage'
  >,
  'previewKind' | 'owner'
> & {
  previewKind: KnowledgePreviewKind
  owner: KnowledgeUser
}

export type KnowledgeFolderPathItem = Populated<Schemas['KnowledgeFolderPathItem'], 'id' | 'name'>

/** State of the agent-readable Markdown text extracted from the current version. */
export type KnowledgeTextStatus = 'PENDING' | 'RUNNING' | 'COMPLETED' | 'UNSUPPORTED' | 'FAILED'

export type KnowledgeDocumentDetail = {
  document: KnowledgeDocument
  knowledgeBase: KnowledgeBase
  folderPath: KnowledgeFolderPathItem[]
  sha256: string
  textStatus?: KnowledgeTextStatus | null
  summary?: string | null
  keywords?: string[]
}

export type KnowledgeDocumentVersion = Omit<
  Populated<
    Schemas['KnowledgeDocumentVersionResponse'],
    'id' | 'versionNumber' | 'fileName' | 'contentType' | 'sizeBytes' | 'sha256' | 'createdAt' | 'current'
  >,
  'createdBy'
> & {
  createdBy: KnowledgeUser
}

export type CreateKnowledgeBaseRequest = Schemas['CreateKnowledgeBaseRequest']
export type UpdateKnowledgeBaseRequest = Schemas['UpdateKnowledgeBaseRequest']
export type CreateKnowledgeFolderRequest = Schemas['CreateKnowledgeFolderRequest']
export type UpdateKnowledgeFolderRequest = Schemas['UpdateKnowledgeFolderRequest']
export type UpdateKnowledgeDocumentRequest = Schemas['UpdateKnowledgeDocumentRequest']

export type KnowledgeDocumentSort = 'updated' | 'title' | 'created'

export interface KnowledgeDocumentQuery {
  folderId?: number
  q?: string
  extensions?: string[]
  ownerId?: string
  updatedFrom?: string
  updatedTo?: string
  sort?: KnowledgeDocumentSort
  page?: number
  size?: number
}

export interface KnowledgeDocumentPage {
  items: KnowledgeDocument[]
  total: number
  page: number
  size: number
}

/** A file found across knowledge bases, with where it lives. */
export type KnowledgeDocumentSearchHit = Omit<
  Populated<
    Schemas['KnowledgeDocumentSearchHitResponse'],
    'namespace' | 'namespaceDisplayName' | 'knowledgeBaseSlug' | 'knowledgeBaseDisplayName'
  >,
  'document'
> & {
  document: KnowledgeDocument
}

export interface KnowledgeDocumentSearchQuery {
  q: string
  namespace?: string
  page?: number
  size?: number
}

export interface KnowledgeDocumentSearchPage {
  items: KnowledgeDocumentSearchHit[]
  total: number
  page: number
  size: number
}

/** A deleted (archived) file that still occupies storage until it is permanently purged. */
export interface ArchivedKnowledgeDocument {
  id: number
  knowledgeBaseId: number
  folderId?: number | null
  title: string
  owner: KnowledgeUser
  updatedAt: string
  versionCount: number
  storedBytes: number
  canPurge: boolean
}

export interface ArchivedKnowledgeDocumentPage {
  items: ArchivedKnowledgeDocument[]
  total: number
  page: number
  size: number
}

/** Outcome of a permanent purge; {@code remainingArchived} is only set for base-wide purges. */
export interface KnowledgePurgeResult {
  purgedDocuments: number
  freedBytes: number
  remainingArchived?: number | null
}

/** The signed-in user's stored bytes and per-user storage quota. */
export interface UserStorageQuota {
  usedBytes: number
  skillBytes: number
  knowledgeBytes: number
  quotaBytes?: number | null
  unlimited: boolean
  exempt: boolean
}
