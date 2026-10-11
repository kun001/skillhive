import { describe, expect, it, vi } from 'vitest'
import { fileNameFrom, SkillHiveClient, SkillHiveError } from './client.js'

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), { status, headers: { 'content-type': 'application/json' } })
}

describe('SkillHiveClient', () => {
  it('sends the bearer token, builds the query and unwraps data', async () => {
    const fetchImpl = vi.fn(async () => json({ code: 0, msg: 'ok', data: { items: [] } }))
    const client = new SkillHiveClient('https://hive.example.com/sub', 'sk_test', fetchImpl as typeof fetch)

    await expect(client.getJson('/api/web/skills', { q: 'a b', label: ['x', 'y'], page: undefined }))
      .resolves.toEqual({ items: [] })

    const [url, init] = fetchImpl.mock.calls[0] as unknown as [URL, RequestInit]
    expect(url.toString()).toBe('https://hive.example.com/sub/api/web/skills?q=a+b&label=x&label=y')
    expect((init.headers as Record<string, string>).Authorization).toBe('Bearer sk_test')
    expect(init.redirect).toBe('error')
  })

  it('turns HTTP errors into readable messages', async () => {
    const client = (response: Response) =>
      new SkillHiveClient('https://h.example', 't', (async () => response) as typeof fetch)

    await expect(client(json({ code: 403, msg: 'Namespace membership required' }, 403)).getJson('/x'))
      .rejects.toThrow('Namespace membership required (HTTP 403)')
    await expect(client(json({ code: 401 }, 401)).getJson('/x')).rejects.toThrow(/invalid, expired or revoked/)
    await expect(client(new Response('busy', { status: 429 })).getJson('/x')).rejects.toThrow(/rate limit/)
    await expect(client(new Response('<html>', { status: 502 })).getJson('/x')).rejects.toBeInstanceOf(SkillHiveError)
  })

  it('returns file bytes, type and name', async () => {
    const fetchImpl = async () => new Response(new Uint8Array([1, 2, 3]), {
      headers: {
        'content-type': 'application/pdf',
        'content-disposition': 'attachment; filename="=?UTF-8?Q?=E8=AE=A1=E5=88=92.pdf?="; filename*=UTF-8\'\'%E8%AE%A1%E5%88%92.pdf',
      },
    })
    const file = await new SkillHiveClient('https://h.example', 't', fetchImpl as typeof fetch).getFile('/f')
    expect(Array.from(file.bytes)).toEqual([1, 2, 3])
    expect(file.contentType).toBe('application/pdf')
    expect(file.fileName).toBe('计划.pdf')
  })
})

describe('fileNameFrom', () => {
  it('falls back to the plain filename parameter', () => {
    expect(fileNameFrom('attachment; filename="notes.txt"')).toBe('notes.txt')
    expect(fileNameFrom(null)).toBeUndefined()
  })
})
