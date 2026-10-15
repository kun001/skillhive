/** Real HTTP + stdio regression with disposable namespaces. Requires an authorized local/test server.
 * SMOKE_ADMIN_USERNAME=... SMOKE_ADMIN_PASSWORD=... node mcp/scripts/knowledge-crud-smoke.mjs http://localhost:18089
 * Build mcp/dist first. Never prints or saves the generated personal tokens.
 */
import assert from 'node:assert/strict'
import { randomUUID } from 'node:crypto'
import { fileURLToPath } from 'node:url'
import { Client } from '@modelcontextprotocol/sdk/client/index.js'
import { StreamableHTTPClientTransport } from '@modelcontextprotocol/sdk/client/streamableHttp.js'
import { StdioClientTransport } from '@modelcontextprotocol/sdk/client/stdio.js'

const endpoint = new URL(process.argv[2] ?? 'http://localhost:18089')
const cookies = new Map()
const clients = []
const tokenIds = []
const namespaces = []
const documentIds = []
let checks = 0
function pass(label) { checks++; console.log(`PASS ${checks}: ${label}`) }

async function http(method, path, body, { token, expected = 200 } = {}) {
  const headers = { Accept: 'application/json' }
  if (token) headers.Authorization = `Bearer ${token}`
  else {
    headers.Cookie = [...cookies].map(([name, value]) => `${name}=${value}`).join('; ')
    if (cookies.has('XSRF-TOKEN')) headers['X-XSRF-TOKEN'] = decodeURIComponent(cookies.get('XSRF-TOKEN'))
  }
  if (body !== undefined) headers['Content-Type'] = 'application/json'
  const response = await fetch(new URL(path, endpoint), { method, headers, body: body === undefined ? undefined : JSON.stringify(body), redirect: 'error' })
  if (!token) for (const cookie of response.headers.getSetCookie()) {
    const pair = cookie.split(';', 1)[0], split = pair.indexOf('=')
    cookies.set(pair.slice(0, split), pair.slice(split + 1))
  }
  assert.ok((Array.isArray(expected) ? expected : [expected]).includes(response.status), `${method} ${path}: HTTP ${response.status}`)
  if (response.status === 204) return undefined
  const envelope = await response.json()
  if (expected === 200) assert.equal(envelope.code, 0, `${method} ${path}: ${envelope.msg}`)
  return envelope.data
}

async function issue(scopes, ids, baseIds) {
  const issued = await http('POST', '/api/v1/tokens', { name: `crud-${randomUUID()}`, mcp: true, scopes, namespaceIds: ids, knowledgeBaseIds: baseIds })
  tokenIds.push(issued.id)
  return issued.token
}

async function connect(token, stdio = false) {
  const client = new Client({ name: 'skillhive-knowledge-crud-regression', version: '1.0.0' })
  const transport = stdio ? new StdioClientTransport({ command: process.execPath,
    args: [fileURLToPath(new URL('../dist/index.js', import.meta.url))],
    env: { ...process.env, SKILLHIVE_URL: endpoint.origin, SKILLHIVE_TOKEN: token }, stderr: 'pipe' })
    : new StreamableHTTPClientTransport(new URL('/api/v1/mcp', endpoint), { requestInit: { headers: { Authorization: `Bearer ${token}` } } })
  await client.connect(transport)
  clients.push(client)
  return client
}

async function call(client, name, args = {}, denied = false) {
  const result = await client.callTool({ name, arguments: args })
  assert.equal(!!result.isError, denied, `${name}: unexpected tool status`)
  if (denied) { pass(`${name} denied as expected`); return undefined }
  pass(`${name} succeeded`)
  return JSON.parse(result.content[0].text)
}

async function mutate(client, name, args, denied = false) {
  return call(client, name, { idempotencyKey: randomUUID(), ...args }, denied)
}

try {
  assert.ok(process.env.SMOKE_ADMIN_USERNAME && process.env.SMOKE_ADMIN_PASSWORD, 'Set the test administrator credentials')
  await http('GET', '/api/v1/auth/me', undefined, { expected: 401 })
  await http('POST', '/api/v1/auth/local/login', { username: process.env.SMOKE_ADMIN_USERNAME, password: process.env.SMOKE_ADMIN_PASSWORD })
  await http('GET', '/api/v1/auth/me')
  for (const suffix of ['a', 'b']) {
    const slug = `mcp-crud-${randomUUID().slice(0, 8)}-${suffix}`
    const team = await http('POST', '/api/web/namespaces', { slug, displayName: 'Disposable MCP CRUD regression' })
    namespaces.push({ id: team.id, slug })
  }
  const [team, foreign] = namespaces
  const token = await issue(['knowledge:read', 'knowledge:upload', 'knowledge:update', 'knowledge:delete', 'knowledge:manage'], [team.id])
  const client = await connect(token)
  const tools = (await client.listTools()).tools
  for (const name of ['get_knowledge_base', 'create_knowledge_base', 'update_knowledge_base', 'delete_knowledge_base',
    'create_knowledge_folder', 'update_knowledge_folder', 'delete_knowledge_folder', 'update_document', 'upload_document_version']) {
    assert.ok(tools.some(tool => tool.name === name), `Missing ${name}`)
  }
  pass('official SDK discovers all knowledge CRUD tools')
  const baseArgs = { namespace: team.slug, slug: 'files', displayName: 'Files', idempotencyKey: randomUUID() }
  const base = await mutate(client, 'create_knowledge_base', baseArgs)
  assert.equal((await mutate(client, 'create_knowledge_base', baseArgs)).id, base.id)
  await mutate(client, 'create_knowledge_base', { ...baseArgs, displayName: 'Changed parameters' }, true)
  await mutate(client, 'create_knowledge_base', { namespace: foreign.slug, slug: 'forbidden', displayName: 'Denied' }, true)
  const location = { namespace: team.slug, base: 'files' }
  assert.equal((await call(client, 'get_knowledge_base', location)).id, base.id)
  await mutate(client, 'update_knowledge_base', { ...location, displayName: 'Team files', description: 'Regression' })
  const folder = await mutate(client, 'create_knowledge_folder', { ...location, name: 'Policies' })
  const child = await mutate(client, 'create_knowledge_folder', { ...location, name: 'Child', parentId: folder.id })
  await mutate(client, 'update_knowledge_folder', { ...location, folderId: folder.id, name: 'Cycle', parentId: child.id }, true)
  await mutate(client, 'update_knowledge_folder', { ...location, folderId: child.id, name: 'Moved to root' })
  const content = text => Buffer.from(text).toString('base64')
  const uploaded = await mutate(client, 'upload_document', { ...location, fileName: 'guide.md', contentBase64: content('# 第一版'), folderId: folder.id })
  const documentId = uploaded.id
  documentIds.push(documentId)
  await mutate(client, 'upload_document', { ...location, fileName: 'guide.md', contentBase64: content('# 重名'), folderId: folder.id }, true)
  await mutate(client, 'update_document', { documentId, title: 'Updated title', description: 'Updated details' })
  let detail = await call(client, 'get_document', { documentId })
  assert.equal(detail.detail.document.title, 'Updated title')
  assert.equal(detail.detail.document.folderId, null)
  pass('metadata update moves to root without changing bytes')
  const versionArgs = { documentId, fileName: 'guide.md', contentBase64: content('# 第二版'), expectedVersion: 1, idempotencyKey: randomUUID() }
  assert.equal((await mutate(client, 'upload_document_version', versionArgs)).versionNumber, 2)
  assert.equal((await mutate(client, 'upload_document_version', versionArgs)).versionNumber, 2)
  await mutate(client, 'upload_document_version', { ...versionArgs, idempotencyKey: randomUUID() }, true)
  detail = await call(client, 'get_document', { documentId })
  assert.equal(detail.versions.length, 2)
  for (const [version, expected] of [[1, '# 第一版'], [2, '# 第二版']]) {
    const read = await client.callTool({ name: 'read_document', arguments: { documentId, version, format: 'original' } })
    assert.ok(!read.isError)
    assert.equal(read.content[1].resource.text, expected)
  }
  pass('both original and new file versions remain readable')
  await mutate(client, 'delete_knowledge_base', location, true)
  await mutate(client, 'update_document', { documentId, title: 'Moved back', folderId: folder.id })
  await mutate(client, 'delete_knowledge_folder', { ...location, folderId: folder.id }, true)
  const limitedToken = await issue(['knowledge:read', 'knowledge:update', 'knowledge:manage'], [team.id], [base.id])
  const limited = await connect(limitedToken)
  await mutate(limited, 'create_knowledge_base', { namespace: team.slug, slug: 'outside-ceiling', displayName: 'Denied' }, true)
  const second = await mutate(client, 'create_knowledge_base', { namespace: team.slug, slug: 'other', displayName: 'Other' })
  await call(limited, 'get_knowledge_base', { namespace: team.slug, base: 'other' }, true)
  await mutate(limited, 'create_knowledge_folder', { namespace: team.slug, base: 'other', name: 'Denied' }, true)
  const readToken = await issue(['knowledge:read'], [team.id])
  const readOnly = await connect(readToken)
  assert.ok(!(await readOnly.listTools()).tools.some(tool => tool.name === 'update_document' || tool.name === 'create_knowledge_base'))
  await mutate(readOnly, 'update_document', { documentId, title: 'Denied' }, true)
  await http('PUT', `/api/v1/mcp/knowledge/documents/${documentId}`, { title: 'Denied', idempotencyKey: randomUUID() }, { token: readToken, expected: 403 })
  pass('read-only tokens cannot bypass tool scopes through REST')
  const stdio = await connect(token, true)
  await mutate(stdio, 'update_document', { documentId, title: 'Updated through stdio' })
  await mutate(stdio, 'upload_document_version', { documentId, fileName: 'guide.md', contentBase64: content('# 第三版'), expectedVersion: 2 })
  const stdioFolder = await mutate(stdio, 'create_knowledge_folder', { ...location, name: 'Stdio folder' })
  await mutate(stdio, 'delete_knowledge_folder', { ...location, folderId: stdioFolder.id })
  assert.equal((await call(client, 'get_document', { documentId })).versions.length, 3)
  pass('stdio and remote MCP share version history and permissions')
  const concurrent = await Promise.all(['A', 'B'].map(label => client.callTool({ name: 'upload_document_version', arguments: {
    documentId, fileName: 'guide.md', contentBase64: content(`# Concurrent ${label}`), expectedVersion: 3, idempotencyKey: randomUUID(),
  } })))
  assert.equal(concurrent.filter(result => !result.isError).length, 1)
  assert.equal((await call(client, 'get_document', { documentId })).versions.length, 4)
  pass('two concurrent updates of one version publish exactly one new version')
  const retryArgs = { documentId, fileName: 'guide.md', contentBase64: content('# Concurrent identical retry'), expectedVersion: 4, idempotencyKey: randomUUID() }
  const retries = await Promise.all([client.callTool({ name: 'upload_document_version', arguments: retryArgs }),
    client.callTool({ name: 'upload_document_version', arguments: retryArgs })])
  assert.ok(retries.every(result => !result.isError))
  assert.ok(retries.every(result => JSON.parse(result.content[0].text).versionNumber === 5))
  assert.equal((await call(client, 'get_document', { documentId })).versions.length, 5)
  pass('concurrent identical retry keys return the same single published version')
  const deletion = { documentId, idempotencyKey: randomUUID() }
  await mutate(client, 'delete_document', deletion)
  await mutate(client, 'delete_document', deletion)
  await call(client, 'get_document', { documentId }, true)
  await mutate(client, 'delete_knowledge_folder', { ...location, folderId: child.id })
  await mutate(client, 'delete_knowledge_folder', { ...location, folderId: folder.id })
  const archive = { ...location, idempotencyKey: randomUUID() }
  await mutate(client, 'delete_knowledge_base', archive)
  await mutate(client, 'delete_knowledge_base', archive)
  assert.ok(!(await call(client, 'list_knowledge_bases')).some(item => item.id === base.id))
  await mutate(client, 'delete_knowledge_base', { namespace: team.slug, base: second.slug })
  await http('DELETE', `/api/v1/tokens/${tokenIds[0]}`, undefined, { expected: 204 })
  await assert.rejects(() => client.callTool({ name: 'list_knowledge_bases', arguments: {} }))
  pass('revoked credentials fail on the next remote call')
} finally {
  for (const client of clients) await client.close().catch(() => {})
  for (const id of documentIds) await http('DELETE', `/api/web/knowledge/documents/${id}`, undefined, { expected: [200, 404] })
  for (const id of tokenIds) await http('DELETE', `/api/v1/tokens/${id}`, undefined, { expected: 204 })
  for (const team of namespaces.reverse()) {
    // Each created team is private to this run; the normal OWNER delete flow cleans archived history.
    await http('DELETE', `/api/web/namespaces/${team.slug}`)
  }
}
console.log(`Completed ${checks} real MCP checks; disposable namespaces and tokens removed.`)
