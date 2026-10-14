/** Minimal HTTP client for the SkillHive /api/v1 surface, authenticated with a personal API token. */

export type Query = Record<string, string | number | boolean | string[] | undefined>

export interface FileResponse {
  bytes: Uint8Array
  contentType: string
  fileName: string | undefined
}

export class SkillHiveError extends Error {
  constructor(readonly status: number, message: string) {
    super(message)
  }
}

interface ApiEnvelope<T> {
  code: number
  msg?: string
  data: T
}

export class SkillHiveClient {
  constructor(
    private readonly baseUrl: string,
    private readonly token: string,
    private readonly fetchImpl: typeof fetch = fetch,
  ) {}

  async getJson<T>(path: string, query?: Query): Promise<T> {
    const response = await this.request(path, query, 'application/json')
    const body = (await response.json()) as ApiEnvelope<T>
    if (body.code !== 0) {
      throw new SkillHiveError(response.status, body.msg ?? `SkillHive returned code ${body.code}`)
    }
    return body.data
  }

  async getFile(path: string, query?: Query): Promise<FileResponse> {
    const response = await this.request(path, query, '*/*')
    return {
      bytes: new Uint8Array(await response.arrayBuffer()),
      contentType: response.headers.get('content-type') ?? 'application/octet-stream',
      fileName: fileNameFrom(response.headers.get('content-disposition')),
    }
  }

  private async request(path: string, query: Query | undefined, accept: string): Promise<Response> {
    const url = new URL(this.baseUrl + path)
    for (const [key, value] of Object.entries(query ?? {})) {
      if (value === undefined) continue
      for (const item of Array.isArray(value) ? value : [value]) {
        url.searchParams.append(key, String(item))
      }
    }
    // No endpoint used here redirects; refusing redirects keeps the token from following one elsewhere.
    const response = await this.fetchImpl(url, {
      headers: { Authorization: `Bearer ${this.token}`, Accept: accept },
      redirect: 'error',
    })
    if (!response.ok) {
      throw new SkillHiveError(response.status, await errorMessage(response))
    }
    return response
  }

  async writeJson(path: string, method: 'POST' | 'DELETE', body?: unknown, query?: Query): Promise<unknown> {
    if (!path.startsWith('/api/v1/mcp/')) throw new Error('Unsupported write endpoint')
    const url = new URL(this.baseUrl + path)
    for (const [key, value] of Object.entries(query ?? {})) if (value !== undefined) url.searchParams.set(key, String(value))
    const response = await this.fetchImpl(url, { method, body: body === undefined ? undefined : JSON.stringify(body),
      headers: { Authorization: `Bearer ${this.token}`, Accept: 'application/json', 'Content-Type': 'application/json' },
      redirect: 'error', signal: AbortSignal.timeout(60_000) })
    if (!response.ok) throw new SkillHiveError(response.status, await errorMessage(response))
    const envelope = await response.json() as ApiEnvelope<unknown>
    if (envelope.code !== 0) throw new SkillHiveError(response.status, envelope.msg ?? 'Operation failed')
    return envelope.data
  }
}

async function errorMessage(response: Response): Promise<string> {
  let detail: string | undefined
  try {
    detail = ((await response.json()) as Partial<ApiEnvelope<unknown>>).msg
  } catch {
    // Non-JSON error bodies (e.g. from a proxy) carry nothing useful.
  }
  switch (response.status) {
    case 401:
      return 'SkillHive rejected the API token: it is invalid, expired or revoked, or its user is disabled'
    case 429:
      return 'SkillHive rate limit reached; wait a minute before retrying'
    default:
      return detail ? `${detail} (HTTP ${response.status})` : `SkillHive request failed with HTTP ${response.status}`
  }
}

/** Prefers the RFC 5987 filename* parameter, which carries non-ASCII names intact. */
export function fileNameFrom(contentDisposition: string | null): string | undefined {
  if (!contentDisposition) return undefined
  const extended = /filename\*\s*=\s*(?:UTF-8|utf-8)''([^;]+)/.exec(contentDisposition)
  if (extended) {
    try {
      return decodeURIComponent(extended[1].trim())
    } catch {
      // Fall through to the plain parameter.
    }
  }
  const plain = /filename\s*=\s*"([^"]*)"/.exec(contentDisposition)
  return plain?.[1]
}
