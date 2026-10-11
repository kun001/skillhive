import type { McpServer } from '@modelcontextprotocol/sdk/server/mcp.js'
import type { CallToolResult } from '@modelcontextprotocol/sdk/types.js'
import { z } from 'zod'
import { SkillHiveClient, SkillHiveError } from './client.js'
import { baseMimeType, errorResult, fileContent, jsonContent, tooLargeResult } from './content.js'
import type { ReadAccess } from './scopes.js'

export interface ToolOptions {
  access: ReadAccess
  maxFileBytes: number
}

interface SkillVersionRef {
  version: string
}

interface SkillDetail {
  publishedVersion?: SkillVersionRef | null
  headlineVersion?: SkillVersionRef | null
}

interface SkillFile {
  filePath: string
  fileSize: number
  contentType?: string
}

interface KnowledgeDocument {
  id: number
  fileName: string
  contentType: string
  sizeBytes: number
  currentVersion: number
}

interface KnowledgeVersion {
  versionNumber: number
  fileName: string
  contentType: string
  sizeBytes: number
}

const READ_ONLY = { readOnlyHint: true, destructiveHint: false, idempotentHint: true, openWorldHint: false }

const page = z.number().int().min(0).optional().describe('Zero-based page number, default 0')
const size = z.number().int().min(1).max(50).optional().describe('Page size, 1-50, default 20')

const seg = encodeURIComponent

/** Runs a tool body, turning SkillHive errors into tool errors the model can read and act on. */
async function run(body: () => Promise<CallToolResult>): Promise<CallToolResult> {
  try {
    return await body()
  } catch (error) {
    if (error instanceof SkillHiveError) {
      return errorResult(error.message)
    }
    return errorResult(`SkillHive request failed: ${error instanceof Error ? error.message : String(error)}`)
  }
}

export function registerTools(server: McpServer, client: SkillHiveClient, options: ToolOptions): void {
  server.registerTool('whoami', {
    title: 'Who am I',
    description: 'Shows the SkillHive user behind the API token, what the token may read, and the teams (namespaces) the user belongs to.',
    annotations: READ_ONLY,
  }, () => run(async () => {
    const [me, namespaces] = await Promise.all([
      client.getJson<Record<string, unknown>>('/api/v1/auth/me'),
      client.getJson<unknown>('/api/v1/me/namespaces'),
    ])
    return jsonContent({
      userId: me.userId,
      displayName: me.displayName,
      tokenScopes: me.tokenScopes,
      namespaces,
    })
  }))

  if (options.access.skills) {
    registerSkillTools(server, client, options)
  }
  if (options.access.knowledge) {
    registerKnowledgeTools(server, client, options)
  }
}

function registerSkillTools(server: McpServer, client: SkillHiveClient, options: ToolOptions): void {
  server.registerTool('search_skills', {
    title: 'Search skills',
    description: 'Searches the SkillHive skill library by keyword. Filter by team namespace, by library '
      + '("public" for the shared catalogue, "team" for your teams\' skills) or by label slugs.',
    inputSchema: {
      query: z.string().optional().describe('Keywords matched against skill names and descriptions'),
      namespace: z.string().optional().describe('Team namespace slug, e.g. "team-a"'),
      library: z.enum(['public', 'team']).optional(),
      labels: z.array(z.string()).optional().describe('Label slugs that results must carry'),
      page,
      size,
    },
    annotations: READ_ONLY,
  }, ({ query, namespace, library, labels, page, size }) => run(async () => jsonContent(
    await client.getJson('/api/web/skills', { q: query, namespace, library, label: labels, page, size }))))

  server.registerTool('get_skill', {
    title: 'Get skill',
    description: 'Returns a skill\'s details, including its current published version.',
    inputSchema: {
      namespace: z.string().describe('Namespace slug the skill belongs to, e.g. "global" or "team-a"'),
      slug: z.string().describe('Skill slug'),
    },
    annotations: READ_ONLY,
  }, ({ namespace, slug }) => run(async () => jsonContent(
    await client.getJson(`/api/v1/skills/${seg(namespace)}/${seg(slug)}`))))

  server.registerTool('list_skill_versions', {
    title: 'List skill versions',
    description: 'Lists the versions of a skill visible to you, newest first.',
    inputSchema: { namespace: z.string(), slug: z.string(), page, size },
    annotations: READ_ONLY,
  }, ({ namespace, slug, page, size }) => run(async () => jsonContent(
    await client.getJson(`/api/v1/skills/${seg(namespace)}/${seg(slug)}/versions`, { page, size }))))

  server.registerTool('list_skill_files', {
    title: 'List skill files',
    description: 'Lists the files packaged in a skill version (SKILL.md, references, scripts...). '
      + 'Without a version, the current published version is used.',
    inputSchema: {
      namespace: z.string(),
      slug: z.string(),
      version: z.string().optional().describe('Exact version string; defaults to the current version'),
    },
    annotations: READ_ONLY,
  }, ({ namespace, slug, version }) => run(async () => {
    const resolved = version ?? await currentSkillVersion(client, namespace, slug)
    const files = await client.getJson<SkillFile[]>(skillVersionPath(namespace, slug, resolved) + '/files')
    return jsonContent({ version: resolved, files })
  }))

  server.registerTool('read_skill_file', {
    title: 'Read skill file',
    description: 'Reads one file from a skill package, e.g. "SKILL.md". Text files come back as text; other files '
      + 'come back unchanged as the original bytes. Without a version, the current published version is used. '
      + 'Reads made through this tool are recorded in the SkillHive audit log.',
    inputSchema: {
      namespace: z.string(),
      slug: z.string(),
      path: z.string().describe('File path inside the package, as listed by list_skill_files'),
      version: z.string().optional(),
    },
    annotations: READ_ONLY,
  }, ({ namespace, slug, path, version }) => run(async () => {
    const resolved = version ?? await currentSkillVersion(client, namespace, slug)
    const base = skillVersionPath(namespace, slug, resolved)
    const files = await client.getJson<SkillFile[]>(base + '/files')
    const entry = files.find((file) => file.filePath === path)
    if (!entry) {
      return errorResult(`"${path}" is not in ${namespace}/${slug}@${resolved}; use list_skill_files to see its files`)
    }
    if (entry.fileSize > options.maxFileBytes) {
      return tooLargeResult(path, entry.fileSize, options.maxFileBytes)
    }
    const file = await client.getFile(base + '/file', { path })
    return {
      content: fileContent({
        uri: `skillhive://skills/${seg(namespace)}/${seg(slug)}/versions/${seg(resolved)}/${path.split('/').map(seg).join('/')}`,
        label: `${namespace}/${slug}@${resolved}:${path}`,
        fileName: path,
        // The endpoint streams application/octet-stream, so the type recorded at upload is more useful.
        mimeType: baseMimeType(entry.contentType ?? file.contentType),
        bytes: file.bytes,
      }),
    }
  }))

  server.registerTool('list_my_skills', {
    title: 'List my skills',
    description: 'Lists skills you published, starred or subscribed to.',
    inputSchema: {
      kind: z.enum(['published', 'stars', 'subscriptions']).describe('Which list to return'),
      page,
      size,
    },
    annotations: READ_ONLY,
  }, ({ kind, page, size }) => run(async () => {
    const path = kind === 'published' ? '/api/v1/me/skills' : `/api/v1/me/${kind}`
    return jsonContent(await client.getJson(path, { page, size }))
  }))
}

function registerKnowledgeTools(server: McpServer, client: SkillHiveClient, options: ToolOptions): void {
  server.registerTool('list_knowledge_bases', {
    title: 'List knowledge bases',
    description: 'Lists the knowledge bases of the teams you belong to.',
    annotations: READ_ONLY,
  }, () => run(async () => jsonContent(await client.getJson('/api/v1/knowledge/bases'))))

  server.registerTool('browse_knowledge_base', {
    title: 'Browse knowledge base',
    description: 'Returns the folder tree of a knowledge base and the files in one folder (the root when folderId '
      + 'is omitted). Use query to filter files by title within the knowledge base.',
    inputSchema: {
      namespace: z.string().describe('Team namespace slug'),
      base: z.string().describe('Knowledge base slug'),
      folderId: z.number().int().optional(),
      query: z.string().optional(),
      page,
      size,
    },
    annotations: READ_ONLY,
  }, ({ namespace, base, folderId, query, page, size }) => run(async () => {
    const basePath = `/api/v1/knowledge/bases/${seg(namespace)}/${seg(base)}`
    const [folders, documents] = await Promise.all([
      client.getJson(basePath + '/folders'),
      client.getJson(basePath + '/documents', { folderId, q: query, page, size }),
    ])
    return jsonContent({ folders, documents })
  }))

  server.registerTool('search_knowledge', {
    title: 'Search knowledge',
    description: 'Searches files across your knowledge bases by title and description (file contents are not indexed).',
    inputSchema: {
      query: z.string().min(1),
      namespace: z.string().optional().describe('Limit to one team namespace slug'),
      page,
      size,
    },
    annotations: READ_ONLY,
  }, ({ query, namespace, page, size }) => run(async () => jsonContent(
    await client.getJson('/api/v1/knowledge/documents/search', { q: query, namespace, page, size }))))

  server.registerTool('get_document', {
    title: 'Get knowledge file',
    description: 'Returns a knowledge base file\'s details, its folder path and its version history.',
    inputSchema: { documentId: z.number().int() },
    annotations: READ_ONLY,
  }, ({ documentId }) => run(async () => {
    const [detail, versions] = await Promise.all([
      client.getJson(`/api/v1/knowledge/documents/${documentId}`),
      client.getJson(`/api/v1/knowledge/documents/${documentId}/versions`),
    ])
    return jsonContent({ detail, versions })
  }))

  server.registerTool('read_document', {
    title: 'Read knowledge file',
    description: 'Reads a knowledge base file. Text files come back as text; PDF, Office documents, images and '
      + 'other files come back unchanged as the original file. Defaults to the current version. Requires the '
      + 'download permission in that team, and every read is recorded in the SkillHive audit log.',
    inputSchema: {
      documentId: z.number().int(),
      version: z.number().int().min(1).optional().describe('Version number; defaults to the current version'),
    },
    annotations: READ_ONLY,
  }, ({ documentId, version }) => run(async () => {
    // Size and type come from metadata first, so an oversized file is never downloaded (or audited).
    const target = await knowledgeVersion(client, documentId, version)
    if (target.sizeBytes > options.maxFileBytes) {
      return tooLargeResult(target.fileName, target.sizeBytes, options.maxFileBytes)
    }
    const file = await client.getFile(`/api/v1/knowledge/documents/${documentId}/content`,
      { version: target.versionNumber })
    const fileName = file.fileName ?? target.fileName
    return {
      content: fileContent({
        uri: `skillhive://knowledge/documents/${documentId}/versions/${target.versionNumber}/${seg(fileName)}`,
        label: `${fileName} (document ${documentId}, version ${target.versionNumber})`,
        fileName,
        mimeType: baseMimeType(target.contentType || file.contentType),
        bytes: file.bytes,
      }),
    }
  }))
}

function skillVersionPath(namespace: string, slug: string, version: string): string {
  return `/api/v1/skills/${seg(namespace)}/${seg(slug)}/versions/${seg(version)}`
}

async function currentSkillVersion(client: SkillHiveClient, namespace: string, slug: string): Promise<string> {
  const detail = await client.getJson<SkillDetail>(`/api/v1/skills/${seg(namespace)}/${seg(slug)}`)
  const version = detail.publishedVersion?.version ?? detail.headlineVersion?.version
  if (!version) {
    throw new SkillHiveError(404, `${namespace}/${slug} has no version you can read`)
  }
  return version
}

async function knowledgeVersion(client: SkillHiveClient, documentId: number, version: number | undefined): Promise<KnowledgeVersion> {
  if (version === undefined) {
    const detail = await client.getJson<{ document: KnowledgeDocument }>(`/api/v1/knowledge/documents/${documentId}`)
    const document = detail.document
    return {
      versionNumber: document.currentVersion,
      fileName: document.fileName,
      contentType: document.contentType,
      sizeBytes: document.sizeBytes,
    }
  }
  const versions = await client.getJson<KnowledgeVersion[]>(`/api/v1/knowledge/documents/${documentId}/versions`)
  const match = versions.find((candidate) => candidate.versionNumber === version)
  if (!match) {
    throw new SkillHiveError(404, `Document ${documentId} has no version ${version}`)
  }
  return match
}
