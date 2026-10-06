import type { QueryClient } from '@tanstack/react-query'

export function getNotificationQueryKeyScope(userId?: string | null) {
  return userId ? ['notifications', userId] as const : ['notifications', 'guest'] as const
}

export function clearSessionScopedQueries(queryClient: QueryClient) {
  queryClient.removeQueries({ queryKey: ['knowledge'] })
  queryClient.removeQueries({ queryKey: ['notifications'] })
  queryClient.removeQueries({ queryKey: ['labels'] })
  queryClient.removeQueries({ queryKey: ['skills'] })
  queryClient.removeQueries({ queryKey: ['namespaces'] })
  queryClient.removeQueries({ queryKey: ['governance'] })
  queryClient.removeQueries({ queryKey: ['reviews'] })
  queryClient.removeQueries({ queryKey: ['promotions'] })
  queryClient.removeQueries({ queryKey: ['reports'] })
  queryClient.removeQueries({ queryKey: ['admin', 'users'] })
}
