// @vitest-environment jsdom
import JSZip from 'jszip'
import { describe, expect, it } from 'vitest'
import { renderAsync } from 'docx-preview'
import { brokenEmptyCmapFont, intactCmapFont } from './fixtures/fonts'
import {
  fontUnicodeCodepoints,
  mapDocxFontFamily,
  repairDocxPreviewFonts,
  repairEmbeddedFont,
} from './repair-docx-embedded-font'

const brokenFont = brokenEmptyCmapFont
const intactFont = intactCmapFont

function toBase64(bytes: Uint8Array): string {
  let binary = ''
  for (let offset = 0; offset < bytes.length; offset += 8192) {
    binary += String.fromCharCode(...bytes.subarray(offset, offset + 8192))
  }
  return btoa(binary)
}
const JIA = 0x7532
const YI = 0x4e59
const BING = 0x4e19
const SPACE = 0x20

function codes(bytes: Uint8Array): number[] {
  const mapped = fontUnicodeCodepoints(bytes)
  expect(mapped).not.toBeNull()
  return mapped ?? []
}

describe('embedded Word font cmap repair', () => {
  it('reads the synthetic broken font, including both empty glyphs', () => {
    expect(codes(brokenFont)).toEqual([SPACE, BING, YI, JIA])
  })

  it('drops empty glyphs even when they reserve advance width', () => {
    const repaired = repairEmbeddedFont(brokenFont)
    expect(repaired).not.toBeNull()
    expect(codes(repaired ?? brokenFont)).toEqual([SPACE, JIA])
    expect(repairEmbeddedFont(repaired ?? brokenFont)).toBeNull()
  })

  it('leaves a font whose cmap glyphs all have outlines unchanged', () => {
    expect(codes(intactFont)).toEqual([SPACE, YI, JIA])
    expect(repairEmbeddedFont(intactFont)).toBeNull()
  })

  it('renames a repaired face and appends a CJK fallback stack', async () => {
    const host = document.createElement('div')
    const style = document.createElement('style')
    style.textContent = [
      '@font-face { font-family: BrokenFace; src: url(data:font/ttf;base64,' + toBase64(brokenFont) + '); }',
      'p { font-family: "Times New Roman", BrokenFace; }',
    ].join('\n')
    const paragraph = document.createElement('p')
    paragraph.style.fontFamily = '"Times New Roman", BrokenFace'
    paragraph.textContent = '甲乙'
    host.append(style, paragraph)
    document.body.append(host)
    await repairDocxPreviewFonts(host)
    const css = style.textContent ?? ''
    expect(css).toContain('font-family: SkillHiveEmbedded0')
    expect(css).toContain('Droid Sans Fallback')
    expect(css).not.toContain('font-family: BrokenFace')
    expect(paragraph.style.fontFamily).toContain('SkillHiveEmbedded0')
    expect(paragraph.style.fontFamily).toContain('Droid Sans Fallback')
    const payload = css.match(/base64,([A-Za-z0-9+/=]+)/)?.[1] ?? ''
    const repaired = Uint8Array.from(atob(payload), (char) => char.charCodeAt(0))
    expect(codes(repaired)).toEqual([SPACE, JIA])
    await repairDocxPreviewFonts(host)
    expect((style.textContent ?? '').match(/Droid Sans Fallback/g)?.length).toBe(1)
    host.remove()
  })

  it('does not duplicate fallbacks on an already complete stack', () => {
    const once = mapDocxFontFamily('"Times New Roman", BrokenFace', new Map([['BrokenFace', 'SkillHiveEmbedded0']]))
    expect(mapDocxFontFamily(once, new Map())).toBe(once)
  })

  it.each([['甲乙', true], ['甲', false]])(
    'prefers one local FangSong for the whole font only if its text uses missing Han glyphs (%s)',
    async (body, useLocal) => {
      const host = document.createElement('div')
      const style = document.createElement('style')
      const family = '"方正仿宋_GBK"'
      style.textContent = [
        `@font-face { font-family: ${family}; src: url(data:font/ttf;base64,${toBase64(brokenFont)}); }`,
        `.word-body { font-family: "Times New Roman", ${family}; }`,
      ].join('\n')
      const paragraph = document.createElement('p')
      paragraph.className = 'word-body'
      paragraph.textContent = body
      const otherBody = document.createElement('p')
      otherBody.style.fontFamily = family
      otherBody.textContent = '甲'
      const heading = document.createElement('h1')
      heading.style.fontFamily = 'OtherFace'
      heading.textContent = '乙'
      host.append(style, paragraph, otherBody, heading)
      document.body.append(host)
      try {
        await repairDocxPreviewFonts(host)
        const local = 'SkillHiveDocxLocalFangSong'
        const computed = getComputedStyle(paragraph).fontFamily
        expect(computed.includes(local)).toBe(useLocal)
        expect(otherBody.style.fontFamily.includes(local)).toBe(useLocal)
        expect(heading.style.fontFamily).not.toContain(local)
        if (useLocal) {
          expect(computed).toContain(`"Times New Roman", ${local}, "SkillHiveEmbedded0"`)
          expect(host.querySelectorAll('style')[0].textContent).toContain('local("FangSong")')
        }
      } finally {
        host.remove()
      }
    },
  )
})

describe('synthetic docx with an empty embedded glyph', () => {
  it('reproduces the hidden character in docx-preview and removes it after repair', async () => {
    const guid = '{AB7F8945-E9CA-4971-8FE7-5F614128C329}'
    const obfuscated = new Uint8Array(brokenFont)
    const trimmed = guid.replace(/[{}-]/g, '')
    const key = new Array<number>(16)
    for (let index = 0; index < 16; index += 1) key[15 - index] = Number.parseInt(trimmed.slice(index * 2, index * 2 + 2), 16)
    for (let index = 0; index < 32; index += 1) obfuscated[index] ^= key[index % 16]
    const zip = new JSZip()
    zip.file('[Content_Types].xml', `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Default Extension="odttf" ContentType="application/vnd.openxmlformats-officedocument.obfuscatedFont"/>
  <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
  <Override PartName="/word/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml"/>
  <Override PartName="/word/fontTable.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.fontTable+xml"/>
</Types>`)
    zip.file('_rels/.rels', `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
</Relationships>`)
    zip.file('word/_rels/document.xml.rels', `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
  <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/fontTable" Target="fontTable.xml"/>
</Relationships>`)
    zip.file('word/styles.xml', `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:styles xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
  <w:docDefaults><w:rPrDefault><w:rPr>
    <w:rFonts w:ascii="BrokenFace" w:hAnsi="BrokenFace" w:eastAsia="BrokenFace"/>
  </w:rPr></w:rPrDefault></w:docDefaults>
</w:styles>`)
    zip.file('word/fontTable.xml', `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:fonts xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
  <w:font w:name="BrokenFace"><w:embedRegular r:id="rId1" w:fontKey="${guid}"/></w:font>
</w:fonts>`)
    zip.file('word/_rels/fontTable.xml.rels', `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/font" Target="fonts/font1.odttf"/>
</Relationships>`)
    zip.file('word/document.xml', `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
  <w:body>
    <w:p><w:r><w:rPr><w:rFonts w:ascii="BrokenFace" w:hAnsi="BrokenFace" w:eastAsia="BrokenFace"/><w:sz w:val="32"/></w:rPr><w:t>甲乙</w:t></w:r></w:p>
    <w:sectPr><w:pgSz w:w="12240" w:h="15840"/><w:pgMar w:top="1440" w:right="1440" w:bottom="1440" w:left="1440"/></w:sectPr>
  </w:body>
</w:document>`)
    zip.file('word/fonts/font1.odttf', obfuscated)
    const blob = await zip.generateAsync({ type: 'blob' })
    const host = document.createElement('div')
    const styles = document.createElement('div')
    document.body.append(host)
    await renderAsync(blob, host, styles, {
      className: 'fixture-docx',
      inWrapper: false,
      ignoreFonts: false,
      useBase64URL: true,
    })
    host.append(styles)
    const before = [...styles.querySelectorAll('style')].map((style) => style.textContent ?? '').join('\n')
    const beforePayload = before.match(/base64,([A-Za-z0-9+/=]+)/)?.[1] ?? ''
    expect(beforePayload.length).toBeGreaterThan(0)
    const beforeFont = Uint8Array.from(atob(beforePayload), (char) => char.charCodeAt(0))
    expect(codes(beforeFont)).toContain(YI)
    expect(host.textContent).toContain('甲乙')
    await repairDocxPreviewFonts(host)
    const after = [...styles.querySelectorAll('style')].map((style) => style.textContent ?? '').join('\n')
    const afterPayload = after.match(/base64,([A-Za-z0-9+/=]+)/)?.[1] ?? ''
    const afterFont = Uint8Array.from(atob(afterPayload), (char) => char.charCodeAt(0))
    expect(codes(afterFont)).not.toContain(YI)
    expect(codes(afterFont)).toContain(JIA)
    expect(after).toContain('Droid Sans Fallback')
    expect(host.textContent).toContain('甲乙')
    host.remove()
  })
})
