import { describe, expect, it } from 'vitest'
import { markdownImageUrls, planUploads, resolveImagePath } from './markdown-images'
import { createUploadItems } from './upload-queue'

function file(name: string, content = 'image', path?: string) {
  const result = new File([content], name)
  Object.defineProperty(result, 'text', { value: async () => content })
  if (path) Object.defineProperty(result, 'webkitRelativePath', { value: path })
  return result
}

describe('Markdown image uploads', () => {
  it('recognizes inline and reference images, ignoring fenced code and ordinary links', () => {
    expect(markdownImageUrls('![x](a.png)\n![y][flow]\n\n[flow]: b.png\n\n[link](c.png)\n```md\n![example](not-real.png)\n```'))
      .toEqual(['a.png', 'b.png'])
  })
  it('bundles a regular multi-file selection using unique image names', async () => {
    const plan = await planUploads(createUploadItems([file('guide.md', '![x](images/a.png)'), file('a.png'), file('other.txt')]))
    expect(plan.map((entry) => entry.item.file.name)).toEqual(['guide.md', 'other.txt'])
    expect(plan[0].bundle.images[0].path).toBe('images/a.png')
    expect(plan[0].error).toBeUndefined()
  })
  it('keeps every image selected with Markdown out of the document list, including unused images', async () => {
    const plan = await planUploads(createUploadItems([
      file('guide.md', '![流程](images/flow.png)'), file('flow.png'), file('unused.png'),
    ]))
    expect(plan.map((entry) => entry.item.file.name)).toEqual(['guide.md'])
    expect(plan[0].bundle.images.map((image) => image.path)).toEqual(['images/flow.png', 'unused.png'])
    expect(plan[0].error).toBeUndefined()
  })
  it('keeps independent image uploads available when no Markdown is selected', async () => {
    const plan = await planUploads(createUploadItems([file('a.png'), file('b.png')]))
    expect(plan.map((entry) => entry.item.file.name)).toEqual(['a.png', 'b.png'])
    expect(plan.every((entry) => entry.bundle.images.length === 0)).toBe(true)
  })
  it('preserves folder paths, parent references, Unicode and same names in different folders', async () => {
    const plan = await planUploads(createUploadItems([
      file('说明.md', '![x](../图/a%20图.png)\n![y](../其他/a%20图.png)', '资料/docs/说明.md'),
      file('a 图.png', 'one', '资料/图/a 图.png'), file('a 图.png', 'two', '资料/其他/a 图.png'),
    ]))
    expect(plan).toHaveLength(1)
    expect(plan[0].error).toBeUndefined()
    expect(plan[0].bundle.images.map((image) => image.path)).toEqual(['资料/图/a 图.png', '资料/其他/a 图.png'])
  })
  it('reports missing and ambiguous images without guessing', async () => {
    const missing = await planUploads(createUploadItems([file('a.md', '![x](missing.png)')]))
    expect(missing[0].error?.key).toBe('knowledge.upload.imageMissing')
    const ambiguous = await planUploads(createUploadItems([file('a.md', '![x](img/a.png)'), file('a.png'), file('a.png')]))
    expect(ambiguous[0].error?.key).toBe('knowledge.upload.imageAmbiguous')
  })
  it('keeps unchanged images for a new version and replaces supplied ones', async () => {
    const existing = { sourcePath: 'pack/docs/a.md', paths: ['pack/img/a.png'] }
    const plan = await planUploads(createUploadItems([file('a.md', '![x](../img/a.png)')]), existing)
    expect(plan[0].error).toBeUndefined()
    expect(plan[0].bundle.sourcePath).toBe(existing.sourcePath)
    const replacement = await planUploads(createUploadItems([file('a.md', '![x](../img/a.png)'), file('a.png')]), existing)
    expect(replacement[0].bundle.images[0].path).toBe('pack/img/a.png')
  })
  it('never rewrites remote, root-relative or unsafe URLs and bounds traversal', () => {
    for (const url of ['https://example.com/a.png', '/api/img.png', '//example.com/a.png', 'javascript:bad', '../../outside.png']) {
      expect(resolveImagePath('docs/a.md', url)).toBeUndefined()
    }
    expect(resolveImagePath('docs/a.md', '../img/a.png?x=1#top')).toBe('img/a.png')
  })
})
