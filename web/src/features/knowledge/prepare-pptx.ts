import JSZip from 'jszip'

const PRESENTATION_NS = 'http://schemas.openxmlformats.org/presentationml/2006/main'
const TYPES_NS = 'http://schemas.openxmlformats.org/package/2006/content-types'
const RELS_NS = 'http://schemas.openxmlformats.org/package/2006/relationships'

function parseXml(source: string) {
  const xml = new DOMParser().parseFromString(source, 'application/xml')
  if (xml.getElementsByTagName('parsererror').length) throw new Error('Invalid PPTX metadata')
  return xml
}

/** Validate the deck and repair unused missing masters in a preview copy (PptxGenJS compatibility). */
export async function preparePptx(data: ArrayBuffer) {
  const zip = await JSZip.loadAsync(data)
  const typesPart = zip.file('[Content_Types].xml')
  const presentationPart = zip.file('ppt/presentation.xml')
  if (!typesPart || !presentationPart) throw new Error('Invalid PPTX package')
  const types = parseXml(await typesPart.async('string'))
  const presentation = parseXml(await presentationPart.async('string'))
  const slideCount = presentation.getElementsByTagNameNS(PRESENTATION_NS, 'sldId').length
  const size = presentation.getElementsByTagNameNS(PRESENTATION_NS, 'sldSz')[0]
  const cx = Number(size?.getAttribute('cx'))
  const cy = Number(size?.getAttribute('cy'))
  if (!slideCount || cx <= 0 || cy <= 0 || !Number.isFinite(cx / cy)) throw new Error('Invalid PPTX slides')
  const width = 960
  const height = Math.round(width * cy / cx)
  const missingMasters = Array.from(types.getElementsByTagNameNS(TYPES_NS, 'Override')).filter((entry) =>
    entry.getAttribute('ContentType') === 'application/vnd.openxmlformats-officedocument.presentationml.slideMaster+xml'
    && !zip.file(decodeURIComponent((entry.getAttribute('PartName') ?? '').replace(/^\//, ''))))
  if (!missingMasters.length) return { data, slideCount, width, height }

  const references = new Set<string>()
  for (const part of Object.values(zip.files)) {
    if (part.dir || !part.name.endsWith('.rels')) continue
    const rels = parseXml(await part.async('string'))
    const base = part.name === '_rels/.rels' ? '' : part.name.slice(0, part.name.lastIndexOf('/_rels/') + 1)
    for (const rel of Array.from(rels.getElementsByTagNameNS(RELS_NS, 'Relationship'))) {
      const target = rel.getAttribute('Target')
      if (target && rel.getAttribute('TargetMode') !== 'External') {
        references.add(decodeURIComponent(new URL(target, `https://pptx.invalid/${base}`).pathname.slice(1)))
      }
    }
  }
  for (const entry of missingMasters) {
    const path = decodeURIComponent(entry.getAttribute('PartName')!.replace(/^\//, ''))
    if (references.has(path)) throw new Error('PPTX references a missing master')
    entry.remove()
  }
  zip.file('[Content_Types].xml', new XMLSerializer().serializeToString(types))
  return { data: await zip.generateAsync({ type: 'arraybuffer' }), slideCount, width, height }
}

export function hasAllSlides(result: unknown, expected: number): boolean {
  const slides = (result as { slides?: unknown[] } | null)?.slides
  return expected > 0 && Array.isArray(slides) && slides.length === expected
}
