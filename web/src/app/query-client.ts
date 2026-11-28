import { QueryClient, QueryCache, MutationCache } from '@tanstack/react-query'
import { handleApiError } from '@/shared/lib/api-error'

export const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 30 * 1000,
      retry: (failureCount, error) => {
        // Don't retry on 401/403/404
        if (error instanceof Error && /HTTP (401|403|404)/.test(error.message)) {
          return false
        }
        return failureCount < 1
      },
      refetchOnWindowFocus: false,
    },
  },
  queryCache: new QueryCache({
    onError: (error, query) => {
      if (query.meta?.skipGlobalErrorHandler) {
        return
      }
      handleApiError(error)
    },
  }),
  mutationCache: new MutationCache({
    onError: (error, _variables, _context, mutation) => {
      if (mutation.meta?.skipGlobalErrorHandler) {
        return
      }
      // Only auto-handle if the mutation doesn't have its own onError
      if (!mutation.options.onError) {
        handleApiError(error)
      }
    },
  }),
})
