// @vitest-environment jsdom
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { fireEvent, render, screen } from '@testing-library/react'
import { expect, it, vi } from 'vitest'
import { skillShareApi } from '@/api/client'
import { ShareButton } from './share-button'

vi.mock('react-i18next', () => ({ useTranslation: () => ({ t: (key: string) => key }) }))
vi.mock('@/features/auth/use-auth', () => ({ useAuth: () => ({ user: { userId: 'admin' } }) }))
vi.mock('@/api/client', () => ({ skillShareApi: { settings: vi.fn(), update: vi.fn() } }))

it('loads and creates the visitor link from the shared settings dialog', async () => {
  vi.mocked(skillShareApi.settings).mockResolvedValue({})
  vi.mocked(skillShareApi.update).mockResolvedValue({ shareId: 'visitor-id' })
  const client = new QueryClient()
  render(
    <QueryClientProvider client={client}>
      <ShareButton skillId={42} title="Demo" />
    </QueryClientProvider>,
  )

  expect(skillShareApi.settings).not.toHaveBeenCalled()
  fireEvent.click(screen.getByRole('button', { name: 'skillDetail.share.button' }))
  fireEvent.click(await screen.findByRole('checkbox', { name: 'knowledge.share.enable' }))
  fireEvent.click(screen.getByRole('button', { name: 'knowledge.baseDialog.save' }))

  const link = await screen.findByRole<HTMLInputElement>('textbox', { name: 'knowledge.share.link' })
  expect(link.value).toBe(`${window.location.origin}/skill-share/visitor-id`)
  expect(skillShareApi.settings).toHaveBeenCalledWith(42)
  expect(skillShareApi.update).toHaveBeenCalledWith(42, expect.objectContaining({ enabled: true, visibility: 'ENTRY' }))
})
