#!/usr/bin/env node
import { McpServer } from '@modelcontextprotocol/sdk/server/mcp.js'
import { StdioServerTransport } from '@modelcontextprotocol/sdk/server/stdio.js'
import { SkillHiveClient } from './client.js'
import { loadConfig } from './config.js'
import { checkTokenScopes } from './scopes.js'
import { registerTools } from './tools.js'

const VERSION = '0.1.0'

// stdout carries the MCP protocol, so every log line goes to stderr.
function log(message: string): void {
  process.stderr.write(`[skillhive-mcp] ${message}\n`)
}

async function main(): Promise<void> {
  const config = loadConfig(process.env)
  const client = new SkillHiveClient(config.baseUrl, config.token)

  const me = await client.getJson<{ userId: string; oauthProvider?: string; tokenScopes?: string[] | null }>('/api/v1/auth/me')
  const access = checkTokenScopes(me.oauthProvider, me.tokenScopes)

  const server = new McpServer({ name: 'skillhive', version: VERSION })
  registerTools(server, client, { access, maxFileBytes: config.maxFileBytes })
  await server.connect(new StdioServerTransport())

  const domains = [access.skills && 'skills', access.knowledge && 'knowledge'].filter(Boolean).join(' + ')
  log(`connected to ${config.baseUrl} as ${me.userId}; read access: ${domains}`)
}

main().catch((error: unknown) => {
  log(error instanceof Error ? error.message : String(error))
  process.exit(1)
})
