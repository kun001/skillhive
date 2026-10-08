// @vitest-environment jsdom
import JSZip from 'jszip'
import { describe, expect, it } from 'vitest'
import { hasAllSlides, preparePptx } from './prepare-pptx'

async function deck({ missing = false, referenced = false, wide = true } = {}) {
  const zip = new JSZip()
  zip.file('ppt/presentation.xml', `<p:presentation xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main"><p:sldIdLst><p:sldId id="1"/><p:sldId id="2"/></p:sldIdLst><p:sldSz cx="${wide ? 12192000 : 9144000}" cy="6858000"/></p:presentation>`)
  zip.file('[Content_Types].xml', `<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">${missing ? '<Override PartName="/ppt/slideMasters/slideMaster2.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.slideMaster+xml"/>' : ''}</Types>`)
  if (referenced) zip.file('ppt/slides/_rels/slide1.xml.rels', '<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Target="../slideMasters/slideMaster2.xml"/></Relationships>')
  return zip.generateAsync({ type: 'arraybuffer' })
}

describe('PowerPoint preview metadata', () => {
  it.each([[true, 540], [false, 720]])('keeps the slide aspect ratio (wide=%s)', async (wide, height) => {
    const data = await deck({ wide })
    const result = await preparePptx(data)
    expect(result).toMatchObject({ slideCount: 2, width: 960, height })
    expect(result.data).toBe(data)
  })
  it('repairs unused missing masters in a preview copy without changing the original', async () => {
    const data = await deck({ missing: true })
    const prepared = await preparePptx(data)
    const repaired = await JSZip.loadAsync(prepared.data)
    expect(await repaired.file('[Content_Types].xml')!.async('string')).not.toContain('slideMaster2.xml')
    const original = await JSZip.loadAsync(data)
    expect(await original.file('[Content_Types].xml')!.async('string')).toContain('slideMaster2.xml')
  })
  it('rejects referenced missing masters and corrupt packages', async () => {
    await expect(preparePptx(await deck({ missing: true, referenced: true }))).rejects.toThrow('missing master')
    await expect(preparePptx(new ArrayBuffer(3))).rejects.toThrow()
  })
  it('does not treat empty or partial output as a successful preview', () => {
    expect(hasAllSlides({ slides: [1, 2] }, 2)).toBe(true)
    expect(hasAllSlides({ slides: [1] }, 2)).toBe(false)
    expect(hasAllSlides(undefined, 2)).toBe(false)
    expect(hasAllSlides({ slides: [] }, 0)).toBe(false)
  })
})
