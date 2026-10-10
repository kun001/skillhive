/** @vitest-environment jsdom */
import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { SearchBar } from './search-bar'

vi.mock('react-i18next', () => ({ useTranslation: () => ({ t: (key: string) => key }) }))

describe('SearchBar explicit submission', () => {
  afterEach(cleanup)

  it('does not search while typing, but submits on confirmation', () => {
    const onSearch = vi.fn()
    render(<SearchBar onSearch={onSearch} />)
    const input = screen.getByRole('textbox')
    fireEvent.change(input, { target: { value: 'agent' } })
    expect(onSearch).not.toHaveBeenCalled()
    fireEvent.click(screen.getByRole('button', { name: 'searchBar.button' }))
    expect(onSearch).toHaveBeenLastCalledWith('agent')
    onSearch.mockClear()
    fireEvent.submit(input.closest('form')!)
    expect(onSearch).toHaveBeenCalledWith('agent')
  })

  it('resets the search as soon as the box is emptied', () => {
    const onSearch = vi.fn()
    render(<SearchBar onSearch={onSearch} defaultValue="agent" />)
    fireEvent.click(screen.getByRole('button', { name: 'searchBar.clear' }))
    expect(onSearch).toHaveBeenCalledTimes(1)
    expect(onSearch).toHaveBeenCalledWith('')

    onSearch.mockClear()
    const input = screen.getByRole('textbox')
    fireEvent.change(input, { target: { value: 'ab' } })
    fireEvent.change(input, { target: { value: '' } })
    expect(onSearch).toHaveBeenCalledTimes(1)
    expect(onSearch).toHaveBeenCalledWith('')
  })
})
