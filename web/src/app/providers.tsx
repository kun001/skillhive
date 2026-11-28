import { QueryClientProvider } from '@tanstack/react-query'
import { RouterProvider } from '@tanstack/react-router'
import { Toaster } from '@/shared/components/toaster'
import { queryClient } from './query-client'
import { router } from './router'

/**
 * Front-end application root.
 *
 * It wires TanStack Query, TanStack Router, and the global toaster so all pages share one query
 * cache and one navigation context.
 */
export function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <RouterProvider router={router} />
      <Toaster />
    </QueryClientProvider>
  )
}
