import { BASE_PATH } from './base-path'

function withoutTrailingSlash(value: string): string {
  return value === '/' ? '' : value.replace(/\/+$/, '')
}

/**
 * Resolves the public base URL used in shared links.
 * Runtime configuration wins outside local development; otherwise the current
 * browser origin must retain Vite's deployment base path.
 */
export function resolvePublicRegistryUrl(
  appBaseUrl: string | undefined,
  origin: string,
  basePath = BASE_PATH,
): string {
  const configuredUrl = appBaseUrl?.trim()
  if (configuredUrl && !configuredUrl.includes('localhost')) {
    return withoutTrailingSlash(configuredUrl)
  }

  return `${withoutTrailingSlash(origin)}${withoutTrailingSlash(basePath)}`
}

/** Public base URL for the current browser session, e.g. for share links. */
export function getPublicBaseUrl(): string {
  if (typeof window === 'undefined') {
    return ''
  }
  return resolvePublicRegistryUrl(
    window.__SKILLHUB_RUNTIME_CONFIG__?.appBaseUrl,
    `${window.location.protocol}//${window.location.host}`,
  )
}
