/**
 * Word files sometimes embed a subset font that still advertises a full cmap.
 * Empty cmap entries point at glyphs with no outline. The browser then paints
 * those characters at zero width, or reserves the advance and draws nothing,
 * instead of falling back. Drop those entries and append a system CJK stack
 * for the characters the subset cannot draw. Outlines the embed actually
 * contains keep their face. A blank with an advance, such as space, stays.
 */

const SFNT_VERSION = 0x00010000
const CHECKSUM_ADJUSTMENT_MAGIC = 0xb1b0afba
const FORMAT4_MAX_BYTES = 65535

const DOCX_CJK_FALLBACKS = [
  'PingFang SC',
  'Hiragino Sans GB',
  'Microsoft YaHei',
  'Noto Sans SC',
  'Noto Sans CJK SC',
  'Source Han Sans SC',
  'WenQuanYi Micro Hei',
  'Droid Sans Fallback',
  'sans-serif',
]

const FALLBACK_SENTINEL = 'Droid Sans Fallback'
const GENERIC_FAMILIES = new Set(['inherit', 'initial', 'unset', 'revert', 'serif', 'sans-serif', 'monospace', 'cursive', 'fantasy', 'system-ui'])

interface TableRecord {
  tag: string
  offset: number
  length: number
}

interface CodeMapping {
  code: number
  glyphId: number
}

function u16(bytes: Uint8Array, offset: number): number {
  return (bytes[offset] << 8) | bytes[offset + 1]
}

function u32(bytes: Uint8Array, offset: number): number {
  return (((bytes[offset] << 24) | (bytes[offset + 1] << 16) | (bytes[offset + 2] << 8) | bytes[offset + 3]) >>> 0)
}

function putU16(out: number[], value: number) {
  out.push((value >> 8) & 0xff, value & 0xff)
}

function checksum(bytes: Uint8Array): number {
  let sum = 0
  const whole = bytes.length - (bytes.length % 4)
  for (let offset = 0; offset < whole; offset += 4) {
    sum = (sum + u32(bytes, offset)) >>> 0
  }
  if (whole !== bytes.length) {
    let word = 0
    for (let index = 0; index < 4; index += 1) {
      word = (word << 8) | (whole + index < bytes.length ? bytes[whole + index] : 0)
    }
    sum = (sum + (word >>> 0)) >>> 0
  }
  return sum
}

function readTables(bytes: Uint8Array): TableRecord[] | null {
  if (bytes.length < 12 || u32(bytes, 0) !== SFNT_VERSION) return null
  const numTables = u16(bytes, 4)
  if (bytes.length < 12 + numTables * 16) return null
  const records: TableRecord[] = []
  for (let index = 0; index < numTables; index += 1) {
    const offset = 12 + index * 16
    const tag = String.fromCharCode(bytes[offset], bytes[offset + 1], bytes[offset + 2], bytes[offset + 3])
    records.push({ tag, offset: u32(bytes, offset + 8), length: u32(bytes, offset + 12) })
  }
  return records.every((record) => record.offset + record.length <= bytes.length) ? records : null
}

function table(records: TableRecord[], tag: string): TableRecord | undefined {
  return records.find((record) => record.tag === tag)
}

function locaOffsets(bytes: Uint8Array, loca: TableRecord, head: TableRecord, numGlyphs: number): number[] | null {
  if (head.length < 52) return null
  const format = u16(bytes, head.offset + 50)
  const count = numGlyphs + 1
  const offsets: number[] = []
  if (format === 0) {
    if (loca.length < count * 2) return null
    for (let index = 0; index < count; index += 1) offsets.push(u16(bytes, loca.offset + index * 2) * 2)
    return offsets
  }
  if (format === 1) {
    if (loca.length < count * 4) return null
    for (let index = 0; index < count; index += 1) offsets.push(u32(bytes, loca.offset + index * 4))
    return offsets
  }
  return null
}

function advanceOf(bytes: Uint8Array, hmtx: TableRecord, numberOfHMetrics: number, glyphId: number): number {
  if (numberOfHMetrics <= 0) return 0
  const index = glyphId < numberOfHMetrics ? glyphId : numberOfHMetrics - 1
  const offset = hmtx.offset + index * 4
  if (offset + 2 > bytes.length) return 0
  return u16(bytes, offset)
}

function glyphHasOutline(bytes: Uint8Array, glyf: TableRecord, offsets: number[], glyphId: number): boolean {
  if (glyphId <= 0 || glyphId >= offsets.length - 1) return false
  const start = offsets[glyphId]
  const end = offsets[glyphId + 1]
  if (end - start < 2) return false
  const offset = glyf.offset + start
  if (offset + 2 > bytes.length) return false
  const contours = u16(bytes, offset)
  const signed = contours > 0x7fff ? contours - 0x10000 : contours
  return signed !== 0
}

/** Space and other blanks keep their advance so gaps do not collapse onto the next font. */
function isKeptBlank(code: number): boolean {
  return code <= 0x20 || code === 0xa0 || (code >= 0x2000 && code <= 0x200a) || code === 0x3000
}

function subtableLength(cmap: Uint8Array, offset: number): number | null {
  if (offset + 4 > cmap.length) return null
  const format = u16(cmap, offset)
  if (format === 0 || format === 2 || format === 4 || format === 6) {
    const length = u16(cmap, offset + 2)
    return offset + length <= cmap.length ? length : null
  }
  if (format === 8 || format === 10 || format === 12 || format === 13 || format === 14) {
    if (offset + 8 > cmap.length) return null
    const length = u32(cmap, offset + 4)
    return offset + length <= cmap.length ? length : null
  }
  return null
}

function format4Mappings(subtable: Uint8Array): CodeMapping[] | null {
  if (subtable.length < 16 || u16(subtable, 0) !== 4) return null
  const length = u16(subtable, 2)
  if (length > subtable.length) return null
  const segCount = u16(subtable, 6) / 2
  const glyphArrayStart = 16 + segCount * 8
  if (!Number.isInteger(segCount) || glyphArrayStart > length) return null
  const endCount: number[] = []
  const startCount: number[] = []
  const idDelta: number[] = []
  const idRangeOffset: number[] = []
  for (let index = 0; index < segCount; index += 1) {
    endCount.push(u16(subtable, 14 + index * 2))
    startCount.push(u16(subtable, 16 + segCount * 2 + index * 2))
    const delta = u16(subtable, 16 + segCount * 4 + index * 2)
    idDelta.push(delta > 0x7fff ? delta - 0x10000 : delta)
    idRangeOffset.push(u16(subtable, 16 + segCount * 6 + index * 2))
  }
  const glyphIdArray: number[] = []
  for (let offset = glyphArrayStart; offset + 1 < length; offset += 2) glyphIdArray.push(u16(subtable, offset))
  const mappings: CodeMapping[] = []
  for (let index = 0; index < segCount; index += 1) {
    if (startCount[index] === 0xffff) continue
    for (let code = startCount[index]; code <= endCount[index]; code += 1) {
      let glyphId: number
      if (idRangeOffset[index] === 0) {
        glyphId = (code + idDelta[index]) & 0xffff
      } else {
        const arrayIndex = (idRangeOffset[index] / 2) + (code - startCount[index]) - (segCount - index)
        const raw = glyphIdArray[arrayIndex] ?? 0
        glyphId = raw === 0 ? 0 : (raw + idDelta[index]) & 0xffff
      }
      if (glyphId !== 0) mappings.push({ code, glyphId })
    }
  }
  return mappings
}

function writeFormat4(mappings: CodeMapping[]): Uint8Array | null {
  const ordered = [...mappings].sort((left, right) => left.code - right.code)
  const segments: { start: number; end: number; delta: number }[] = []
  let index = 0
  while (index < ordered.length) {
    const start = ordered[index]
    let end = start
    let next = index + 1
    while (
      next < ordered.length
      && ordered[next].code === end.code + 1
      && ordered[next].glyphId === start.glyphId + (ordered[next].code - start.code)
    ) {
      end = ordered[next]
      next += 1
    }
    segments.push({ start: start.code, end: end.code, delta: (start.glyphId - start.code) & 0xffff })
    index = next
  }
  segments.push({ start: 0xffff, end: 0xffff, delta: 1 })
  const length = 16 + segments.length * 8
  if (length > FORMAT4_MAX_BYTES) return null
  let entrySelector = 0
  while ((1 << (entrySelector + 1)) <= segments.length) entrySelector += 1
  const searchRange = (1 << entrySelector) * 2
  const rangeShift = segments.length * 2 - searchRange
  const out: number[] = []
  putU16(out, 4)
  putU16(out, length)
  putU16(out, 0)
  putU16(out, segments.length * 2)
  putU16(out, searchRange)
  putU16(out, entrySelector)
  putU16(out, rangeShift)
  segments.forEach((segment) => putU16(out, segment.end))
  putU16(out, 0)
  segments.forEach((segment) => putU16(out, segment.start))
  segments.forEach((segment) => putU16(out, segment.delta))
  segments.forEach(() => putU16(out, 0))
  return Uint8Array.from(out)
}

function rebuildCmap(cmap: Uint8Array, drawable: (glyphId: number, code: number) => boolean): Uint8Array | null {
  if (cmap.length < 4) return null
  const numTables = u16(cmap, 2)
  if (cmap.length < 4 + numTables * 8) return null
  const records: { platform: number; encoding: number; offset: number }[] = []
  for (let index = 0; index < numTables; index += 1) {
    const offset = 4 + index * 8
    records.push({ platform: u16(cmap, offset), encoding: u16(cmap, offset + 2), offset: u32(cmap, offset + 4) })
  }
  const seen = new Map<number, Uint8Array>()
  let hidden = 0
  for (const record of records) {
    if (seen.has(record.offset)) continue
    const length = subtableLength(cmap, record.offset)
    if (length == null) return null
    const subtable = cmap.subarray(record.offset, record.offset + length)
    if (u16(subtable, 0) !== 4) {
      seen.set(record.offset, subtable)
      continue
    }
    const mappings = format4Mappings(subtable)
    if (!mappings) return null
    const kept: CodeMapping[] = []
    for (const mapping of mappings) {
      if (drawable(mapping.glyphId, mapping.code)) kept.push(mapping)
      else hidden += 1
    }
    const rebuilt = writeFormat4(kept)
    if (!rebuilt) return null
    seen.set(record.offset, rebuilt)
  }
  if (hidden === 0) return null
  const order: number[] = []
  records.forEach((record) => {
    if (!order.includes(record.offset)) order.push(record.offset)
  })
  let cursor = 4 + numTables * 8
  const rewritten = new Map<number, { bytes: Uint8Array; offset: number }>()
  for (const oldOffset of order) {
    const bytes = seen.get(oldOffset)
    if (!bytes) return null
    if (cursor % 2 !== 0) cursor += 1
    rewritten.set(oldOffset, { bytes, offset: cursor })
    cursor += bytes.length
  }
  const out = new Uint8Array(cursor)
  out[0] = 0
  out[1] = 0
  out[2] = (numTables >> 8) & 0xff
  out[3] = numTables & 0xff
  records.forEach((record, index) => {
    const placed = rewritten.get(record.offset)
    if (!placed) return
    const offset = 4 + index * 8
    out[offset] = (record.platform >> 8) & 0xff
    out[offset + 1] = record.platform & 0xff
    out[offset + 2] = (record.encoding >> 8) & 0xff
    out[offset + 3] = record.encoding & 0xff
    const view = new DataView(out.buffer)
    view.setUint32(offset + 4, placed.offset)
  })
  rewritten.forEach((placed) => out.set(placed.bytes, placed.offset))
  return out
}

function packFont(original: Uint8Array, records: TableRecord[], cmap: Uint8Array): Uint8Array {
  const headerSize = 12 + records.length * 16
  const slices = records.map((record) => {
    const source = record.tag === 'cmap' ? cmap : original.subarray(record.offset, record.offset + record.length)
    const copy = new Uint8Array(source)
    if (record.tag === 'head' && copy.length >= 12) copy.fill(0, 8, 12)
    return copy
  })
  let cursor = headerSize
  const placed = slices.map((data) => {
    const start = cursor
    cursor += data.length
    cursor = (cursor + 3) & ~3
    return { data, start }
  })
  const font = new Uint8Array(cursor)
  const view = new DataView(font.buffer)
  view.setUint32(0, SFNT_VERSION)
  view.setUint16(4, records.length)
  view.setUint16(6, u16(original, 6))
  view.setUint16(8, u16(original, 8))
  view.setUint16(10, u16(original, 10))
  const tableChecksums: number[] = []
  placed.forEach((entry, index) => {
    font.set(entry.data, entry.start)
    const sum = checksum(entry.data)
    tableChecksums.push(sum)
    const record = records[index]
    const offset = 12 + index * 16
    for (let charIndex = 0; charIndex < 4; charIndex += 1) font[offset + charIndex] = record.tag.charCodeAt(charIndex)
    view.setUint32(offset + 4, sum)
    view.setUint32(offset + 8, entry.start)
    view.setUint32(offset + 12, entry.data.length)
  })
  const headIndex = records.findIndex((record) => record.tag === 'head')
  if (headIndex >= 0) {
    const directorySum = checksum(font.subarray(0, headerSize))
    const tablesSum = tableChecksums.reduce((sum, value) => (sum + value) >>> 0, 0)
    const adjustment = (CHECKSUM_ADJUSTMENT_MAGIC - ((directorySum + tablesSum) >>> 0)) >>> 0
    view.setUint32(placed[headIndex].start + 8, adjustment)
  }
  return font
}

/** Unicode scalar values with a non-zero glyph in the font's format-4 cmaps. */
export function fontUnicodeCodepoints(bytes: Uint8Array): number[] | null {
  const records = readTables(bytes)
  const cmap = records && table(records, 'cmap')
  if (!records || !cmap) return null
  const cmapBytes = bytes.subarray(cmap.offset, cmap.offset + cmap.length)
  const numTables = u16(cmapBytes, 2)
  const codes = new Set<number>()
  for (let index = 0; index < numTables; index += 1) {
    const offset = u32(cmapBytes, 4 + index * 8 + 4)
    const length = subtableLength(cmapBytes, offset)
    if (length == null || u16(cmapBytes, offset) !== 4) continue
    const mappings = format4Mappings(cmapBytes.subarray(offset, offset + length))
    if (!mappings) return null
    mappings.forEach((mapping) => codes.add(mapping.code))
  }
  return [...codes].sort((left, right) => left - right)
}

/**
 * Returns a font with empty zero-advance cmap entries removed, or null when
 * the font is not a TrueType embed or every mapped glyph can already be drawn.
 */
export function repairEmbeddedFont(bytes: Uint8Array): Uint8Array | null {
  const records = readTables(bytes)
  if (!records) return null
  const cmap = table(records, 'cmap')
  const loca = table(records, 'loca')
  const glyf = table(records, 'glyf')
  const head = table(records, 'head')
  const hhea = table(records, 'hhea')
  const hmtx = table(records, 'hmtx')
  const maxp = table(records, 'maxp')
  if (!cmap || !loca || !glyf || !head || !hhea || !hmtx || !maxp || maxp.length < 6 || hhea.length < 36) return null
  const numGlyphs = u16(bytes, maxp.offset + 4)
  const offsets = locaOffsets(bytes, loca, head, numGlyphs)
  if (!offsets) return null
  const numberOfHMetrics = u16(bytes, hhea.offset + 34)
  const rebuilt = rebuildCmap(bytes.subarray(cmap.offset, cmap.offset + cmap.length), (glyphId, code) => {
    if (isKeptBlank(code) && advanceOf(bytes, hmtx, numberOfHMetrics, glyphId) > 0) return true
    return glyphHasOutline(bytes, glyf, offsets, glyphId)
  })
  if (!rebuilt) return null
  return packFont(bytes, records, rebuilt)
}

function quoteFamily(name: string): string {
  return /^[A-Za-z-]+$/.test(name) ? name : `"${name.replace(/"/g, '\\"')}"`
}

function unquote(value: string): string {
  const trimmed = value.trim()
  if ((trimmed.startsWith('"') && trimmed.endsWith('"')) || (trimmed.startsWith("'") && trimmed.endsWith("'"))) {
    return trimmed.slice(1, -1)
  }
  return trimmed
}

function splitFontFamily(value: string): string[] {
  const parts: string[] = []
  let current = ''
  let quote: string | null = null
  for (const char of value) {
    if (quote) {
      current += char
      if (char === quote) quote = null
      continue
    }
    if (char === '"' || char === "'") {
      quote = char
      current += char
      continue
    }
    if (char === ',') {
      parts.push(current.trim())
      current = ''
      continue
    }
    current += char
  }
  if (current.trim()) parts.push(current.trim())
  return parts
}

function bytesToBase64(bytes: Uint8Array): string {
  let binary = ''
  for (let offset = 0; offset < bytes.length; offset += 8192) {
    binary += String.fromCharCode(...bytes.subarray(offset, offset + 8192))
  }
  return btoa(binary)
}

function decodeDataUrl(url: string): Uint8Array | null {
  const comma = url.indexOf(',')
  if (comma < 0) return null
  try {
    const binary = atob(url.slice(comma + 1))
    const bytes = new Uint8Array(binary.length)
    for (let index = 0; index < binary.length; index += 1) bytes[index] = binary.charCodeAt(index)
    return bytes
  } catch {
    return null
  }
}

/** Append system CJK faces after the document fonts, renaming repaired embeds. */
export function mapDocxFontFamily(list: string, renamed: ReadonlyMap<string, string>): string {
  const parts = splitFontFamily(list).map((part) => {
    const next = renamed.get(unquote(part))
    return next ? quoteFamily(next) : part.trim()
  })
  const bare = parts.map(unquote)
  if (bare.length === 1 && GENERIC_FAMILIES.has(bare[0])) return parts[0]
  if (bare.includes(FALLBACK_SENTINEL)) return parts.join(', ')
  return [...parts, ...DOCX_CJK_FALLBACKS.map(quoteFamily)].join(', ')
}

interface PlannedFace {
  block: string
  family: string
  repaired: Uint8Array | null
}

function declaration(block: string, property: string): string | null {
  const match = new RegExp(`${property}\\s*:\\s*([^;}{]+)`, 'i').exec(block)
  return match ? match[1].trim() : null
}

function replaceDeclaration(block: string, property: string, value: string): string {
  return block.replace(new RegExp(`(${property}\\s*:\\s*)([^;}{]+)`, 'i'), `$1${value}`)
}

function planStyle(css: string): { body: string; faces: PlannedFace[] } {
  const faces: PlannedFace[] = []
  const body = css.replace(/@font-face\s*\{[^}]*\}/gi, (block) => {
    const familyValue = declaration(block, 'font-family')
    const source = /url\(\s*(['"]?)(data:[^)'"]+)\1\s*\)/i.exec(block)
    const bytes = source ? decodeDataUrl(source[2]) : null
    faces.push({
      block,
      family: familyValue ? unquote(familyValue) : '',
      repaired: bytes ? repairEmbeddedFont(bytes) : null,
    })
    return `/*SHFACE${faces.length - 1}*/`
  })
  return { body, faces }
}

function finishStyle(planned: { body: string; faces: PlannedFace[] }, renamed: ReadonlyMap<string, string>): string {
  const faces = planned.faces.map((face) => {
    const nextName = renamed.get(face.family)
    if (!nextName) return face.block
    let block = replaceDeclaration(face.block, 'font-family', nextName)
    if (face.repaired) {
      const source = /url\(\s*(['"]?)(data:[^)'"]+)\1\s*\)/i.exec(block)
      if (source) block = block.replace(source[2], `data:font/ttf;base64,${bytesToBase64(face.repaired)}`)
    }
    return block
  })
  const withLists = planned.body.replace(/font-family\s*:\s*([^;}{]+)/gi, (_match, list: string) => (
    `font-family: ${mapDocxFontFamily(list, renamed)}`
  ))
  return withLists.replace(/\/\*SHFACE(\d+)\*\//g, (_match, index) => faces[Number(index)] ?? '')
}

/** Repair embedded faces inside a rendered docx-preview tree and load the replacements. */
export async function repairDocxPreviewFonts(root: ParentNode): Promise<void> {
  const styles = [...root.querySelectorAll('style')]
  const planned = styles.map((style) => planStyle(style.textContent ?? ''))
  const renamed = new Map<string, string>()
  planned.forEach((style) => {
    style.faces.forEach((face) => {
      if (face.repaired && face.family && !renamed.has(face.family)) {
        renamed.set(face.family, `SkillHiveEmbedded${renamed.size}`)
      }
    })
  })
  styles.forEach((style, index) => {
    const next = finishStyle(planned[index], renamed)
    if (next !== style.textContent) style.textContent = next
  })
  root.querySelectorAll<HTMLElement>('*').forEach((element) => {
    const family = element.style.fontFamily
    if (!family) return
    const next = mapDocxFontFamily(family, renamed)
    if (next !== family) element.style.fontFamily = next
  })
  if (renamed.size > 0 && document.fonts?.load) {
    await Promise.all([...renamed.values()].map((name) => document.fonts.load(`16px "${name}"`).catch(() => undefined)))
  }
  await document.fonts?.ready
}

function glyphsAreMeasurable(): boolean {
  const probe = document.createElement('span')
  probe.textContent = 'M'
  probe.style.fontSize = '16px'
  document.body.append(probe)
  const range = document.createRange()
  range.selectNodeContents(probe)
  const rect = range.getClientRects()[0]
  probe.remove()
  return !!rect && rect.width > 0.3 && rect.height > 0.3
}

/**
 * Last resort for a glyph that is still in the DOM but has no advance after
 * embedded faces load. Layout engines without real fonts (unit tests) skip this.
 */
export function revealZeroWidthGlyphs(root: HTMLElement): number {
  if (!glyphsAreMeasurable()) return 0
  const fallback = DOCX_CJK_FALLBACKS.map(quoteFamily).join(', ')
  const nodes: Text[] = []
  const walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT)
  let current: Node | null
  while ((current = walker.nextNode())) nodes.push(current as Text)
  let revealed = 0
  for (const node of nodes) {
    const value = node.textContent ?? ''
    if (!value || !node.parentElement) continue
    const hidden: boolean[] = []
    let any = false
    for (let index = 0; index < value.length; index += 1) {
      if (/\s/.test(value[index])) {
        hidden.push(false)
        continue
      }
      const range = document.createRange()
      range.setStart(node, index)
      range.setEnd(node, index + 1)
      const rect = range.getClientRects()[0]
      const missing = !!rect && rect.height > 0.3 && rect.width < 0.3
      hidden.push(missing)
      if (missing) any = true
    }
    if (!any) continue
    const fragment = document.createDocumentFragment()
    let buffer = ''
    let mode = hidden[0]
    const flush = () => {
      if (!buffer) return
      if (mode) {
        const span = document.createElement('span')
        span.style.fontFamily = fallback
        span.textContent = buffer
        fragment.append(span)
        revealed += [...buffer].length
      } else {
        fragment.append(document.createTextNode(buffer))
      }
      buffer = ''
    }
    for (let index = 0; index < value.length; index += 1) {
      if (hidden[index] !== mode) {
        flush()
        mode = hidden[index]
      }
      buffer += value[index]
    }
    flush()
    node.replaceWith(fragment)
  }
  return revealed
}
