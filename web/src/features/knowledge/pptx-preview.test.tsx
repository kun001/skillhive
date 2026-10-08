// @vitest-environment jsdom
import { act, cleanup, render, screen, waitFor } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { PptxCanvas } from './pptx-preview'

const mocks = vi.hoisted(() => ({ init: vi.fn(), preparePptx: vi.fn() }))
vi.mock('pptx-preview', () => ({ init: mocks.init }))
vi.mock('./prepare-pptx', async (original) => ({ ...await original<typeof import('./prepare-pptx')>(), preparePptx: mocks.preparePptx }))
vi.mock('react-i18next', () => ({ useTranslation: () => ({ t: (key: string) => key }) }))

function blob() { return { arrayBuffer: async () => new ArrayBuffer(2) } as Blob }
beforeEach(() => {
  vi.stubGlobal('ResizeObserver', class { observe() {} disconnect() {} })
  vi.spyOn(HTMLElement.prototype, 'offsetHeight', 'get').mockReturnValue(1200)
  vi.spyOn(HTMLElement.prototype, 'clientWidth', 'get').mockReturnValue(480)
  mocks.preparePptx.mockResolvedValue({ data: new ArrayBuffer(2), slideCount: 2, width: 960, height: 540 })
  mocks.init.mockImplementation((host: HTMLElement) => ({
    destroy: vi.fn(),
    preview: async () => {
      const wrapper = document.createElement('div')
      wrapper.className = 'pptx-preview-wrapper'
      wrapper.style.height = '540px'
      wrapper.style.overflowY = 'auto'
      wrapper.textContent = 'Two complete slides'
      host.append(wrapper)
      return { slides: [1, 2] }
    },
  }))
})
afterEach(() => { cleanup(); vi.restoreAllMocks(); vi.unstubAllGlobals(); vi.resetAllMocks() })

describe('PowerPoint browser preview', () => {
  it('renders the complete deck and scales it to fit without squeezing the slide', async () => {
    render(<PptxCanvas blob={blob()} title="Slides" />)
    expect(await screen.findByText('Two complete slides')).toBeTruthy()
    expect(mocks.init).toHaveBeenCalledWith(expect.any(HTMLElement), { width: 960, height: 540, mode: 'list' })
    const canvas = screen.getByRole('region').querySelector('[aria-hidden="false"]') as HTMLElement
    expect(canvas.style.transform).toBe('scale(0.5)')
    const wrapper = canvas.querySelector('.pptx-preview-wrapper') as HTMLElement
    expect(wrapper.style.height).toBe('auto')
    expect(wrapper.style.overflow).toBe('visible')
  })
  it('reports failure for a partial deck rather than leaving a blank success state', async () => {
    mocks.init.mockReturnValue({ destroy: vi.fn(), preview: async () => ({ slides: [1] }) })
    render(<PptxCanvas blob={blob()} title="Slides" />)
    expect(await screen.findByRole('alert')).toBeTruthy()
    expect(screen.queryByText('Two complete slides')).toBeNull()
  })
  it('serializes renders and does not let an older render dispose the new deck', async () => {
    let finishOld: () => void = () => undefined
    const oldDestroy = vi.fn()
    const newDestroy = vi.fn()
    mocks.init.mockImplementationOnce((host: HTMLElement) => ({ destroy: oldDestroy, preview: async () => {
      await new Promise<void>((resolve) => { finishOld = resolve })
      host.textContent = 'Old slides'
      return { slides: [1, 2] }
    } })).mockImplementationOnce((host: HTMLElement) => ({ destroy: newDestroy, preview: async () => {
      host.textContent = 'New slides'
      return { slides: [1, 2] }
    } }))
    const view = render(<PptxCanvas blob={blob()} title="Slides" />)
    await waitFor(() => expect(mocks.init).toHaveBeenCalledTimes(1))
    view.rerender(<PptxCanvas blob={blob()} title="Slides" />)
    await act(async () => { finishOld() })
    expect(await screen.findByText('New slides')).toBeTruthy()
    expect(screen.queryByText('Old slides')).toBeNull()
    expect(oldDestroy).toHaveBeenCalledTimes(1)
    expect(newDestroy).not.toHaveBeenCalled()
  })
})
