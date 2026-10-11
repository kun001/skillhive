/** Runtime settings, read from the environment the MCP client starts this process with. */
export interface Config {
  baseUrl: string
  token: string
  maxFileBytes: number
}

export const DEFAULT_MAX_FILE_BYTES = 10 * 1024 * 1024

const LOOPBACK_HOSTS = new Set(['localhost', '127.0.0.1', '[::1]'])

export class ConfigError extends Error {}

export function loadConfig(env: NodeJS.ProcessEnv): Config {
  const rawUrl = env.SKILLHIVE_URL?.trim()
  if (!rawUrl) {
    throw new ConfigError('SKILLHIVE_URL is required, e.g. https://skillhive.example.com')
  }
  let url: URL
  try {
    url = new URL(rawUrl)
  } catch {
    throw new ConfigError(`SKILLHIVE_URL is not a valid URL: ${rawUrl}`)
  }
  // The token travels on every request, so plain HTTP is only allowed to this machine.
  if (url.protocol !== 'https:' && !(url.protocol === 'http:' && LOOPBACK_HOSTS.has(url.hostname))) {
    throw new ConfigError('SKILLHIVE_URL must use https:// (http:// is only allowed for localhost)')
  }

  const token = env.SKILLHIVE_TOKEN?.trim()
  if (!token) {
    throw new ConfigError('SKILLHIVE_TOKEN is required: create a read-only API token in SkillHive under Profile → Token')
  }

  let maxFileBytes = DEFAULT_MAX_FILE_BYTES
  const rawMax = env.SKILLHIVE_MCP_MAX_FILE_BYTES?.trim()
  if (rawMax) {
    maxFileBytes = Number(rawMax)
    if (!Number.isSafeInteger(maxFileBytes) || maxFileBytes <= 0) {
      throw new ConfigError(`SKILLHIVE_MCP_MAX_FILE_BYTES must be a positive integer, got: ${rawMax}`)
    }
  }

  return { baseUrl: url.toString().replace(/\/+$/, ''), token, maxFileBytes }
}
