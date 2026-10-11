import { describe, expect, it } from 'vitest'
import { ConfigError, DEFAULT_MAX_FILE_BYTES, loadConfig } from './config.js'

describe('loadConfig', () => {
  it('reads url, token and the default size limit', () => {
    expect(loadConfig({ SKILLHIVE_URL: 'https://hive.example.com/', SKILLHIVE_TOKEN: ' sk_abc ' })).toEqual({
      baseUrl: 'https://hive.example.com',
      token: 'sk_abc',
      maxFileBytes: DEFAULT_MAX_FILE_BYTES,
    })
  })

  it('keeps a sub-path deployment prefix', () => {
    expect(loadConfig({ SKILLHIVE_URL: 'https://example.com/skillhive', SKILLHIVE_TOKEN: 't' }).baseUrl)
      .toBe('https://example.com/skillhive')
  })

  it('allows plain http only for localhost', () => {
    expect(loadConfig({ SKILLHIVE_URL: 'http://localhost:8080', SKILLHIVE_TOKEN: 't' }).baseUrl).toBe('http://localhost:8080')
    expect(loadConfig({ SKILLHIVE_URL: 'http://127.0.0.1:8080', SKILLHIVE_TOKEN: 't' }).baseUrl).toBe('http://127.0.0.1:8080')
    expect(() => loadConfig({ SKILLHIVE_URL: 'http://hive.example.com', SKILLHIVE_TOKEN: 't' })).toThrow(ConfigError)
  })

  it('requires url and token', () => {
    expect(() => loadConfig({ SKILLHIVE_TOKEN: 't' })).toThrow(/SKILLHIVE_URL/)
    expect(() => loadConfig({ SKILLHIVE_URL: 'https://hive.example.com' })).toThrow(/SKILLHIVE_TOKEN/)
    expect(() => loadConfig({ SKILLHIVE_URL: 'not a url', SKILLHIVE_TOKEN: 't' })).toThrow(/not a valid URL/)
  })

  it('validates the size limit', () => {
    expect(loadConfig({ SKILLHIVE_URL: 'https://h.example', SKILLHIVE_TOKEN: 't', SKILLHIVE_MCP_MAX_FILE_BYTES: '2048' }).maxFileBytes)
      .toBe(2048)
    expect(() => loadConfig({ SKILLHIVE_URL: 'https://h.example', SKILLHIVE_TOKEN: 't', SKILLHIVE_MCP_MAX_FILE_BYTES: '-1' }))
      .toThrow(ConfigError)
  })
})
