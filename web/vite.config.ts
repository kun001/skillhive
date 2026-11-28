import { defineConfig, type Plugin } from 'vite'
import react from '@vitejs/plugin-react'
import path from 'path'
import { validateBasePath } from './base-path-config'

const JS_BUILD_TARGET = 'es2020'
const LEGACY_BROWSER_TARGETS = ['chrome83', 'edge83', 'firefox78', 'safari14']
const basePath = validateBasePath(process.env.VITE_BASE_PATH ?? '/')
// Lets a second dev server target another backend, e.g. SKILLHUB_API_PROXY=http://localhost:8081.
const apiProxyTarget = process.env.SKILLHUB_API_PROXY ?? 'http://localhost:8080'

const preloadApp: Plugin = {
  name: 'preload-app',
  apply: 'build',
  transformIndexHtml: {
    order: 'post',
    handler(_html, { bundle }) {
      const mainModule = path.resolve(__dirname, 'src/main.tsx')
      const main = Object.values(bundle ?? {}).find(
        (entry) => entry.type === 'chunk' && entry.moduleIds.includes(mainModule),
      )
      // Download both in parallel, but let bootstrap evaluate the app after config is ready.
      return [
        { tag: 'link', attrs: { rel: 'preload', as: 'script', href: `${basePath}runtime-config.js` }, injectTo: 'head-prepend' },
        ...(main ? [{
          tag: 'link',
          attrs: { rel: 'modulepreload', crossorigin: true, href: `${basePath}${main.fileName}` },
          injectTo: 'head-prepend' as const,
        }] : []),
      ]
    },
  },
}

export default defineConfig({
  base: basePath,
  plugins: [react(), preloadApp],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src'),
    },
  },
  build: {
    target: JS_BUILD_TARGET,
    cssTarget: LEGACY_BROWSER_TARGETS,
  },
  optimizeDeps: {
    esbuildOptions: {
      target: JS_BUILD_TARGET,
    },
  },
  test: {
    exclude: ['**/node_modules/**', '**/e2e/**'],
    testTimeout: 30000,
    hookTimeout: 30000,
  },
  server: {
    port: 3000,
    watch: {
      usePolling: true,
      interval: 150,
    },
    proxy: {
      '/api': {
        target: apiProxyTarget,
        changeOrigin: true,
      },
      '/oauth2': {
        target: apiProxyTarget,
        changeOrigin: true,
      },
      '/login/oauth2': {
        target: apiProxyTarget,
        // Preserve the browser-facing localhost:3000 host so Spring's
        // post-login redirect does not send the SPA to localhost:8080.
        changeOrigin: false,
      },
    },
  },
})
