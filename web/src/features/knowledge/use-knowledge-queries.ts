import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useAuth } from '@/features/auth/use-auth'
import { knowledgeApi } from '@/api/client'
import type {
  CreateKnowledgeBaseRequest,
  CreateKnowledgeFolderRequest,
  KnowledgeDocumentQuery,
  UpdateKnowledgeBaseRequest,
  UpdateKnowledgeDocumentRequest,
  UpdateKnowledgeFolderRequest,
} from '@/api/knowledge-types'

export const knowledgeKeys = {
  all: ['knowledge'] as const,
  bases: () => [...knowledgeKeys.all, 'bases'] as const,
  base: (namespace: string, base: string) => [...knowledgeKeys.all, 'base', namespace, base] as const,
  folders: (namespace: string, base: string) => [...knowledgeKeys.all, 'folders', namespace, base] as const,
  documents: (namespace: string, base: string, query?: KnowledgeDocumentQuery) =>
    query
      ? ([...knowledgeKeys.all, 'documents', namespace, base, query] as const)
      : ([...knowledgeKeys.all, 'documents', namespace, base] as const),
  document: (documentId: number) => [...knowledgeKeys.all, 'document', documentId] as const,
  versions: (documentId: number) => [...knowledgeKeys.all, 'versions', documentId] as const,
}

export function useKnowledgeBases() {
  const { user, isLoading } = useAuth()
  const scope = user?.userId ?? 'guest'
  return useQuery({ queryKey: [...knowledgeKeys.bases(), scope], enabled: !isLoading, queryFn: () => knowledgeApi.listBases() })
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
  return useQuery({
    queryKey: [...knowledgeKeys.documents(namespace, base, query), scope],
    enabled: !isLoading,
    retry: false,
    queryFn: () => knowledgeApi.listDocuments(namespace, base, query),
    placeholderData: (previous, query) => query?.queryKey.at(-1) === scope ? previous : undefined,
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
  return () => queryClient.invalidateQueries({ queryKey: knowledgeKeys.all })
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

export function useRestoreKnowledgeVersion(documentId: number) {
  const invalidate = useInvalidateKnowledge()
  return useMutation({
    mutationFn: ({ versionNumber, changeNote }: { versionNumber: number; changeNote?: string }) =>
      knowledgeApi.restoreVersion(documentId, versionNumber, changeNote),
    onSuccess: invalidate,
  })
}
