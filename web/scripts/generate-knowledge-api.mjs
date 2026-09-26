// Generates src/api/generated/knowledge.d.ts from the running backend's OpenAPI document,
// keeping only the knowledge endpoints under /api/web/knowledge and the schemas they reference.
//
// The shared schema.d.ts still carries legacy upstream contracts that the current backend no
// longer serves, so it cannot be regenerated wholesale yet (see docs/skillhive-architecture.md).
import { execFileSync } from 'node:child_process'
import { mkdtempSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'

const source = process.env.OPENAPI_URL ?? 'http://localhost:8080/v3/api-docs'
const prefix = '/api/web/knowledge'
const output = 'src/api/generated/knowledge.d.ts'

const response = await fetch(source)
if (!response.ok) {
  throw new Error(`Failed to fetch ${source}: HTTP ${response.status}`)
}
const spec = await response.json()

const paths = Object.fromEntries(Object.entries(spec.paths).filter(([path]) => path.startsWith(prefix)))
if (Object.keys(paths).length === 0) {
  throw new Error(`No ${prefix} paths found in ${source}`)
}

const schemas = spec.components?.schemas ?? {}
const kept = new Set()
const pending = [paths]
while (pending.length > 0) {
  const node = pending.pop()
  if (Array.isArray(node)) {
    pending.push(...node)
  } else if (node && typeof node === 'object') {
    for (const [key, value] of Object.entries(node)) {
      if (key === '$ref' && typeof value === 'string' && value.startsWith('#/components/schemas/')) {
        const name = value.slice('#/components/schemas/'.length)
        if (!kept.has(name) && schemas[name]) {
          kept.add(name)
          pending.push(schemas[name])
        }
      } else {
        pending.push(value)
      }
    }
  }
}

const filtered = {
  ...spec,
  paths,
  components: { schemas: Object.fromEntries([...kept].sort().map((name) => [name, schemas[name]])) },
}
const specFile = join(mkdtempSync(join(tmpdir(), 'knowledge-api-')), 'knowledge-openapi.json')
writeFileSync(specFile, JSON.stringify(filtered))
execFileSync('pnpm', ['exec', 'openapi-typescript', specFile, '-o', output], { stdio: 'inherit' })
