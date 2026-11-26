import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useAuth } from '@/features/auth/use-auth'
import { knowledgeApi } from '@/api/client'
import type {
  CreateKnowledgeBaseRequest,
  CreateKnowledgeFolderRequest,
  KnowledgeDocumentQuery,
  KnowledgeDocumentSearchQuery,
  UpdateKnowledgeBaseRequest,
  UpdateKnowledgeDocumentRequest,
  UpdateKnowledgeFolderRequest,
} from '@/api/knowledge-types'

export const knowledgeKeys = {
  all: ['knowledge'] as const,
  bases: () => [...knowledgeKeys.all, 'bases'] as const,
  base: (namespace: string, base: string) => [...knowledgeKeys.all, 'base', namespace, base] as const,
  folders: (namespace: string, base: string) => [...knowledgeKeys.all, 'folders', namespace, base] as const,
  documents: (namespace: string, base: string) => [...knowledgeKeys.all, 'documents', namespace, base] as const,
  search: (query: KnowledgeDocumentSearchQuery) => [...knowledgeKeys.all, 'search', query] as const,
  document: (documentId: number) => [...knowledgeKeys.all, 'document', documentId] as const,
  versions: (documentId: number) => [...knowledgeKeys.all, 'versions', documentId] as const,
  archived: (namespace: string, base: string, page?: number) =>
    page === undefined
      ? ([...knowledgeKeys.all, 'archived', namespace, base] as const)
      : ([...knowledgeKeys.all, 'archived', namespace, base, page] as const),
  storageQuota: () => [...knowledgeKeys.all, 'storage-quota'] as const,
}

export function useKnowledgeBases(enabled = true) {
  const { user, isLoading } = useAuth()
  const scope = user?.userId ?? 'guest'
  return useQuery({ queryKey: [...knowledgeKeys.bases(), scope], enabled: enabled && !isLoading, queryFn: () => knowledgeApi.listBases() })
}

export function useKnowledgeBase(namespace: string, base: string) {
  const { user, isLoading } = useAuth()
  const scope = user?.userId ?? 'guest'
  return useQuery({ queryKey: [...knowledgeKeys.base(namespace, base), scope], enabled: !isLoading, queryFn: () => knowledgeApi.getBase(namespace, base) })
}

export function useKnowledgeFolders(namespace: string, base: string) {
  const { user, isLoading } = useAuth()
  const scope = user?.userId ?? 'guest'
  return useQuery({
    queryKey: [...knowledgeKeys.folders(namespace, base), scope],
    enabled: !isLoading,
    retry: false,
    queryFn: () => knowledgeApi.listFolders(namespace, base),
  })
}

export function useKnowledgeDocuments(namespace: string, base: string, query: KnowledgeDocumentQuery) {
  const { user, isLoading } = useAuth()
  const scope = user?.userId ?? 'guest'
  const queryKey = [...knowledgeKeys.documents(namespace, base), query, scope] as const
  return useQuery({
    queryKey,
    enabled: !isLoading,
    retry: false,
    queryFn: ({ signal }) => knowledgeApi.listDocuments(namespace, base, query, signal),
    placeholderData: (previous, previousQuery) => {
      const previousInput = previousQuery?.queryKey[4]
      const sameLocation = previousQuery?.queryKey[2] === namespace && previousQuery.queryKey[3] === base
      const sameScope = previousQuery?.queryKey[5] === scope && previousInput?.folderId === query.folderId
      return sameLocation && sameScope && previousInput?.recursive === query.recursive ? previous : undefined
    },
  })
}

/** File search across every visible knowledge base; idle until there is a keyword. */
export function useKnowledgeDocumentSearch(query: KnowledgeDocumentSearchQuery, enabled = true) {
  const { user, isLoading } = useAuth()
  const scope = user?.userId ?? 'guest'
  const queryKey = [...knowledgeKeys.search(query), scope] as const
  return useQuery({
    queryKey,
    enabled: enabled && !isLoading && query.q.trim().length > 0,
    retry: false,
    queryFn: ({ signal }) => knowledgeApi.searchDocuments(query, signal),
    placeholderData: (previous, previousQuery) => {
      const previousInput = previousQuery?.queryKey[2]
      const sameScope = previousQuery?.queryKey[3] === scope
        && previousInput?.namespace === query.namespace && previousInput?.memberOnly === query.memberOnly
      return sameScope && previousInput?.q === query.q ? previous : undefined
    },
  })
}

export function useKnowledgeDocument(documentId: number) {
  const { user, isLoading } = useAuth()
  const scope = user?.userId ?? 'guest'
  return useQuery({
    queryKey: [...knowledgeKeys.document(documentId), scope],
    retry: false,
    queryFn: () => knowledgeApi.getDocument(documentId),
    enabled: !isLoading && Number.isFinite(documentId),
  })
}

export function useKnowledgeVersions(documentId: number, enabled = true) {
  const { user, isLoading } = useAuth()
  const scope = user?.userId ?? 'guest'
  return useQuery({
    queryKey: [...knowledgeKeys.versions(documentId), scope],
    retry: false,
    queryFn: () => knowledgeApi.listVersions(documentId),
    enabled: !isLoading && enabled && Number.isFinite(documentId),
  })
}

/** Refreshes everything that shows file lists, counts or details after a change. */
export function useInvalidateKnowledge() {
  const queryClient = useQueryClient()
  return () => Promise.all([
    queryClient.invalidateQueries({ queryKey: knowledgeKeys.all }),
    queryClient.invalidateQueries({ queryKey: ['namespaces', 'my'] }),
  ])
}

export function useCreateKnowledgeBase() {
  const invalidate = useInvalidateKnowledge()
  return useMutation({
    mutationFn: (request: CreateKnowledgeBaseRequest) => knowledgeApi.createBase(request),
    onSuccess: invalidate,
  })
}

export function useUpdateKnowledgeBase(namespace: string, base: string) {
  const invalidate = useInvalidateKnowledge()
  return useMutation({
    mutationFn: (request: UpdateKnowledgeBaseRequest) => knowledgeApi.updateBase(namespace, base, request),
    onSuccess: invalidate,
  })
}

export function useCreateKnowledgeFolder(namespace: string, base: string) {
  const invalidate = useInvalidateKnowledge()
  return useMutation({
    mutationFn: (request: CreateKnowledgeFolderRequest) => knowledgeApi.createFolder(namespace, base, request),
    onSuccess: invalidate,
  })
}

export function useUpdateKnowledgeFolder(namespace: string, base: string) {
  const invalidate = useInvalidateKnowledge()
  return useMutation({
    mutationFn: ({ folderId, request }: { folderId: number; request: UpdateKnowledgeFolderRequest }) =>
      knowledgeApi.updateFolder(namespace, base, folderId, request),
    onSuccess: invalidate,
  })
}

export function useDeleteKnowledgeFolder(namespace: string, base: string) {
  const invalidate = useInvalidateKnowledge()
  return useMutation({
    mutationFn: (folderId: number) => knowledgeApi.deleteFolder(namespace, base, folderId),
    onSuccess: invalidate,
  })
}

export function useUpdateKnowledgeDocument() {
  const invalidate = useInvalidateKnowledge()
  return useMutation({
    mutationFn: ({ documentId, request }: { documentId: number; request: UpdateKnowledgeDocumentRequest }) =>
      knowledgeApi.updateDocument(documentId, request),
    onSuccess: invalidate,
  })
}

export function useDeleteKnowledgeDocument() {
  const invalidate = useInvalidateKnowledge()
  return useMutation({
    mutationFn: (documentId: number) => knowledgeApi.deleteDocument(documentId),
    onSuccess: invalidate,
  })
}

export function useBatchDeleteKnowledgeDocuments(namespace: string, base: string) {
  const invalidate = useInvalidateKnowledge()
  return useMutation({
    mutationFn: (documentIds: number[]) => knowledgeApi.batchDeleteDocuments(namespace, base, documentIds),
    onSettled: invalidate,
  })
}

export function useRestoreKnowledgeVersion(documentId: number) {
  const invalidate = useInvalidateKnowledge()
  return useMutation({
    mutationFn: ({ versionNumber, changeNote }: { versionNumber: number; changeNote?: string }) =>
      knowledgeApi.restoreVersion(documentId, versionNumber, changeNote),
    onSuccess: invalidate,
  })
}

export function useArchivedKnowledgeDocuments(namespace: string, base: string, page: number, size: number, enabled = true) {
  const { user, isLoading } = useAuth()
  const scope = user?.userId ?? 'guest'
  return useQuery({
    queryKey: [...knowledgeKeys.archived(namespace, base, page), size, scope],
    enabled: enabled && !isLoading && Boolean(user),
    retry: false,
    queryFn: () => knowledgeApi.listArchivedDocuments(namespace, base, { page, size }),
  })
}

/** Current user's storage usage; lives under the knowledge keys so file changes refresh it. */
export function useMyStorageQuota(enabled = true) {
  const { user, isLoading } = useAuth()
  const scope = user?.userId ?? 'guest'
  return useQuery({
    queryKey: [...knowledgeKeys.storageQuota(), scope],
    enabled: enabled && !isLoading && Boolean(user),
    retry: false,
    queryFn: () => knowledgeApi.getMyStorageQuota(),
  })
}

export function usePurgeKnowledgeDocument() {
  const invalidate = useInvalidateKnowledge()
  return useMutation({
    mutationFn: (documentId: number) => knowledgeApi.purgeDocument(documentId),
    onSettled: invalidate,
  })
}

export function usePurgeArchivedKnowledgeDocuments(namespace: string, base: string) {
  const invalidate = useInvalidateKnowledge()
  return useMutation({
    mutationFn: () => knowledgeApi.purgeArchivedDocuments(namespace, base),
    onSettled: invalidate,
  })
}
