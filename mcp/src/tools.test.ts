import { Client } from '@modelcontextprotocol/sdk/client/index.js'
import { InMemoryTransport } from '@modelcontextprotocol/sdk/inMemory.js'
import { McpServer } from '@modelcontextprotocol/sdk/server/mcp.js'
import { describe, expect, it } from 'vitest'
import { SkillHiveClient } from './client.js'
import type { ReadAccess } from './scopes.js'
import { registerTools } from './tools.js'

type Route = (url: URL) => Response

const ok = (data: unknown) => new Response(JSON.stringify({ code: 0, data }), { headers: { 'content-type': 'application/json' } })

/** A fake SkillHive: routes are keyed by pathname; every requested URL is recorded. */
function fakeSkillHive(routes: Record<string, Route>) {
  const requested: string[] = []
  const fetchImpl = (async (input: URL) => {
    requested.push(input.pathname + input.search)
    const route = routes[input.pathname]
    return route ? route(input) : new Response(JSON.stringify({ code: 404, msg: 'Not found' }), { status: 404 })
  }) as typeof fetch
  return { client: new SkillHiveClient('https://hive.example.com', 'sk_test', fetchImpl), requested }
}

async function connect(client: SkillHiveClient, access: ReadAccess, maxFileBytes = 1024) {
  const server = new McpServer({ name: 'skillhive', version: 'test' })
  registerTools(server, client, { access, maxFileBytes })
  const [clientTransport, serverTransport] = InMemoryTransport.createLinkedPair()
  const mcp = new Client({ name: 'test', version: 'test' })
  await Promise.all([server.connect(serverTransport), mcp.connect(clientTransport)])
  return mcp
}

const SKILL_DETAIL = { publishedVersion: { version: '1.2.0' }, headlineVersion: { version: '1.2.0' } }
const SKILL_FILES = [
  { filePath: 'SKILL.md', fileSize: 12, contentType: 'text/markdown' },
  { filePath: 'assets/big.bin', fileSize: 5000, contentType: 'application/octet-stream' },
]

describe('tool registration', () => {
  it('offers only independently granted write tools and sends mutations to restricted endpoints', async () => {
    const { client, requested } = fakeSkillHive({
      '/api/v1/mcp/knowledge/team/docs/uploads': () => new Response(JSON.stringify({ code: 0, data: { id: 19 } })),
    })
    const mcp = await connect(client, { skills: false, knowledge: false, knowledgeUpload: true })
    const tools = (await mcp.listTools()).tools
    expect(tools.map(tool => tool.name)).toEqual(['whoami', 'get_team_guide', 'upload_document'])
    expect(tools.find(tool => tool.name === 'upload_document')?.annotations?.readOnlyHint).toBe(false)
    const result = await mcp.callTool({ name: 'upload_document', arguments: {
      namespace: 'team', base: 'docs', fileName: 'guide.txt', contentBase64: 'aGVsbG8=', idempotencyKey: 'once',
    } })
    expect(result.isError).toBeFalsy()
    expect(requested).toEqual(['/api/v1/mcp/knowledge/team/docs/uploads'])
  })
  it('only offers tools for the domains the token can read', async () => {
    const { client } = fakeSkillHive({})
    const skillsOnly = await (await connect(client, { skills: true, knowledge: false })).listTools()
    const names = skillsOnly.tools.map((tool) => tool.name)
    expect(names).toContain('read_skill_file')
    expect(names).not.toContain('read_document')

    const knowledgeOnly = await (await connect(client, { skills: false, knowledge: true })).listTools()
    const knowledgeNames = knowledgeOnly.tools.map((tool) => tool.name)
    expect(knowledgeNames).toContain('read_document')
    expect(knowledgeNames).not.toContain('search_skills')
    expect(knowledgeNames).toContain('whoami')
  })

  it('marks every tool read-only', async () => {
    const { client } = fakeSkillHive({})
    const { tools } = await (await connect(client, { skills: true, knowledge: true })).listTools()
    expect(tools.every((tool) => tool.annotations?.readOnlyHint === true)).toBe(true)
  })
})

describe('read_skill_file', () => {
  it('resolves the current version and returns text content', async () => {
    const { client, requested } = fakeSkillHive({
      '/api/v1/skills/team-a/demo': () => ok(SKILL_DETAIL),
      '/api/v1/skills/team-a/demo/versions/1.2.0/files': () => ok(SKILL_FILES),
      '/api/v1/skills/team-a/demo/versions/1.2.0/file': () =>
        new Response('# Demo skill', { headers: { 'content-type': 'application/octet-stream' } }),
    })
    const mcp = await connect(client, { skills: true, knowledge: false })

    const result = await mcp.callTool({ name: 'read_skill_file', arguments: { namespace: 'team-a', slug: 'demo', path: 'SKILL.md' } })

    expect(result.isError).toBeFalsy()
    expect(result.content).toEqual([
      expect.objectContaining({ type: 'text' }),
      { type: 'resource', resource: { uri: 'skillhive://skills/team-a/demo/versions/1.2.0/SKILL.md', mimeType: 'text/markdown', text: '# Demo skill' } },
    ])
    expect(requested).toContain('/api/v1/skills/team-a/demo/versions/1.2.0/file?path=SKILL.md')
  })

  it('does not download files over the size limit', async () => {
    const { client, requested } = fakeSkillHive({
      '/api/v1/skills/team-a/demo/versions/1.2.0/files': () => ok(SKILL_FILES),
    })
    const mcp = await connect(client, { skills: true, knowledge: false })

    const result = await mcp.callTool({ name: 'read_skill_file', arguments: { namespace: 'team-a', slug: 'demo', version: '1.2.0', path: 'assets/big.bin' } })

    expect(result.isError).toBe(true)
    expect(requested.some((url) => url.includes('/file?'))).toBe(false)
  })

  it('reports files that are not in the package', async () => {
    const { client } = fakeSkillHive({ '/api/v1/skills/team-a/demo/versions/1.2.0/files': () => ok(SKILL_FILES) })
    const mcp = await connect(client, { skills: true, knowledge: false })

    const result = await mcp.callTool({ name: 'read_skill_file', arguments: { namespace: 'team-a', slug: 'demo', version: '1.2.0', path: '../etc/passwd' } })

    expect(result.isError).toBe(true)
  })
})

describe('read_document', () => {
  const detail = { document: { id: 7, fileName: 'plan.pdf', contentType: 'application/pdf', sizeBytes: 4, currentVersion: 3 } }

  it('returns the original file bytes for binary documents', async () => {
    const pdf = new Uint8Array([0x25, 0x50, 0x44, 0x46])
    const { client, requested } = fakeSkillHive({
      '/api/v1/knowledge/documents/7': () => ok(detail),
      '/api/v1/knowledge/documents/7/content': () => new Response(pdf, {
        headers: { 'content-type': 'application/pdf', 'content-disposition': 'attachment; filename*=UTF-8\'\'plan.pdf' },
      }),
    })
    const mcp = await connect(client, { skills: false, knowledge: true })

    const result = await mcp.callTool({ name: 'read_document', arguments: { documentId: 7 } })

    expect(result.isError).toBeFalsy()
    const resource = (result.content as Array<{ type: string; resource?: { blob?: string; mimeType?: string } }>)[1]
    expect(resource.resource?.mimeType).toBe('application/pdf')
    expect(Array.from(Buffer.from(resource.resource!.blob!, 'base64'))).toEqual(Array.from(pdf))
    expect(requested).toContain('/api/v1/knowledge/documents/7/content?version=3')
  })

  it('checks the size of a requested version before downloading it', async () => {
    const { client, requested } = fakeSkillHive({
      '/api/v1/knowledge/documents/7/versions': () => ok([
        { versionNumber: 1, fileName: 'plan.pdf', contentType: 'application/pdf', sizeBytes: 999999 },
      ]),
    })
    const mcp = await connect(client, { skills: false, knowledge: true })

    const result = await mcp.callTool({ name: 'read_document', arguments: { documentId: 7, version: 1 } })

    expect(result.isError).toBe(true)
    expect(requested.some((url) => url.includes('/content'))).toBe(false)
  })

  it('returns extracted Markdown with its summary by default', async () => {
    const { client, requested } = fakeSkillHive({
      '/api/v1/knowledge/documents/7/text': () => ok({
        documentId: 7, versionNumber: 3, fileName: 'plan.pdf', status: 'COMPLETED', truncated: true,
        summary: '迁移计划概要', content: '## Page 1\n\n迁移步骤',
      }),
    })
    const mcp = await connect(client, { skills: false, knowledge: true })

    const result = await mcp.callTool({ name: 'read_document', arguments: { documentId: 7 } })

    const [notice, resource] = result.content as Array<{ type: string; text?: string; resource?: { text?: string; mimeType?: string } }>
    expect(notice.text).toContain('Summary: 迁移计划概要')
    expect(notice.text).toContain('truncated')
    expect(resource.resource?.mimeType).toBe('text/markdown')
    expect(resource.resource?.text).toBe('## Page 1\n\n迁移步骤')
    expect(requested.some((url) => url.includes('/content'))).toBe(false)
  })

  it('falls back to the original file when no text exists, unless markdown is required', async () => {
    const pdf = new Uint8Array([0x25, 0x50, 0x44, 0x46])
    const { client, requested } = fakeSkillHive({
      '/api/v1/knowledge/documents/7/text': () => ok({ documentId: 7, versionNumber: 3, fileName: 'plan.pdf', status: 'UNSUPPORTED', content: null }),
      '/api/v1/knowledge/documents/7': () => ok(detail),
      '/api/v1/knowledge/documents/7/content': () => new Response(pdf, { headers: { 'content-type': 'application/pdf' } }),
    })
    const mcp = await connect(client, { skills: false, knowledge: true })

    expect((await mcp.callTool({ name: 'read_document', arguments: { documentId: 7 } })).isError).toBeFalsy()
    expect(requested).toContain('/api/v1/knowledge/documents/7/content?version=3')
    const strict = await mcp.callTool({ name: 'read_document', arguments: { documentId: 7, format: 'markdown' } })
    expect(strict.isError).toBe(true)
    requested.length = 0
    await mcp.callTool({ name: 'read_document', arguments: { documentId: 7, format: 'original' } })
    expect(requested.some((url) => url.includes('/text'))).toBe(false)
  })

  it('passes SkillHive permission errors back to the model', async () => {
    const { client } = fakeSkillHive({
      '/api/v1/knowledge/documents/7': () => new Response(
        JSON.stringify({ code: 403, msg: 'Namespace membership required' }), { status: 403 }),
    })
    const mcp = await connect(client, { skills: false, knowledge: true })

    const result = await mcp.callTool({ name: 'read_document', arguments: { documentId: 7 } })

    expect(result.isError).toBe(true)
    expect(result.content).toEqual([{ type: 'text', text: 'Namespace membership required (HTTP 403)' }])
  })
})

describe('get_team_guide', () => {
  it('returns the guide as Markdown and reports a missing one plainly', async () => {
    const { client, requested } = fakeSkillHive({
      '/api/v1/namespaces/team/guide': () => ok({ namespace: 'team', namespaceDisplayName: 'Team', content: '# 约定' }),
      '/api/v1/namespaces/empty/guide': () => ok({ namespace: 'empty', namespaceDisplayName: 'Empty', content: '' }),
    })
    const mcp = await connect(client, { skills: false, knowledge: false })

    const guide = await mcp.callTool({ name: 'get_team_guide', arguments: { namespace: '@team' } })
    const empty = await mcp.callTool({ name: 'get_team_guide', arguments: { namespace: 'empty' } })

    expect((guide.content as Array<{ resource?: { text?: string } }>)[1].resource?.text).toBe('# 约定')
    expect(JSON.stringify(empty.content)).toContain('no guide yet')
    expect(requested).toContain('/api/v1/namespaces/team/guide')
  })
})
