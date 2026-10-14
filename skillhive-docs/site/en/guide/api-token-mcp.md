# API Tokens & MCP

## Dedicated MCP permissions

New tokens use MCP by default. Grant skills and knowledge read, upload-new and archive permissions independently, choose allowed namespaces, and optionally restrict knowledge bases. “Skills: read” and “Knowledge: read” provide read-only access without a separate read-only mode.

New tools: `upload_skill`, `upload_document`, `delete_skill`, `delete_document`. Uploads only create new objects; no update, overwrite, new-version, move, rename, restore or permanent-purge tools are exposed. Skills follow scanning and review; knowledge files are published immediately.

Calls execute after server authorization without a separate website approval workflow. Clients decide their own tool approval policy. Writes require an `idempotencyKey`; files are base64 with a 10 MiB limit. Write tokens default to 7 days and expire within 30 days.

Legacy read-only tokens remain compatible. Ordinary API write tokens are not automatically upgraded. The read-only examples below describe legacy tokens; dedicated MCP tokens use the same connection format.

## API Tokens

An API token is a personal credential that lets scripts and AI tools access SkillHive as you. It follows the same permissions as web sign-in: it only reaches what you can access.

New tokens created under Profile → Tokens use the MCP grants above. Existing ordinary API tokens use these access profiles:

| Access | Scopes | What it allows |
|--------|--------|----------------|
| Skills · read-write | `skill:read`, `skill:publish`, `skill:delete` | View, download, publish and delete skills |
| Skills · read-only | `skill:read` | View and download skills, My Skills, stars and subscriptions |
| Knowledge (read-only) | `knowledge:read` | View knowledge bases, folders and files, and read file content (needs download permission) |

- Skill and knowledge access are kept apart: a knowledge-only token cannot read skills, and vice versa.
- Dedicated MCP tokens can upload new files and archive documents through the MCP tools. Ordinary knowledge editing and version-upload APIs do not accept tokens.
- Token access to the knowledge base is decided by team membership only; even an administrator's token cannot see teams the administrator is not in.
- The token value is shown only once, when it is created. Delete a token from the list to revoke it immediately if it leaks or is no longer needed.
- Reading knowledge files or skill files, or downloading skill packages, with a token is audit-logged with the token that was used.

Usage:

```bash
curl -H "Authorization: Bearer $SKILLHIVE_TOKEN" https://skillhive.example.com/api/v1/knowledge/bases
```

## MCP Server

### Remote connection

The backend includes a stateless Streamable HTTP endpoint at `/api/v1/mcp`. Once a version containing this feature is deployed, agents supporting custom authentication headers can connect without a local MCP process:

```json
{
  "mcpServers": {
    "skillhive": {
      "type": "http",
      "url": "https://skillhive.example.com/api/v1/mcp",
      "headers": { "Authorization": "Bearer <YOUR_READ_ONLY_TOKEN>" }
    }
  }
}
```

Copy the current instance configuration from Profile → Tokens. Select only the operations and resources your agent needs. Every call checks the token grants and current membership, visibility, ownership and download permissions. Revocation takes effect on subsequent calls. For local testing use `http://localhost:3000/api/v1/mcp`. Remote file reads are capped at 10 MiB.

This endpoint uses personal Bearer tokens and does not provide OAuth sign-in. Clients requiring OAuth without custom headers are not supported yet.

### Local stdio

`mcp/` in the repository is a local stdio MCP server that lets MCP clients such as Claude Code and Cursor read the skill library and knowledge base with your token:

- **Scoped access**: dedicated MCP tokens enable only the selected read, upload-new and archive tools. Legacy tokens must be read-only; ordinary publish or token-management credentials are rejected.
- **Tools follow the token**: it only offers tools for the areas the token can read.
- **Files are passed through unchanged**: text files come back as text, PDF, Office and other files as the original bytes, with no content parsing; the default per-file limit is 10 MB.

### Build

Requires Node.js 20+ and pnpm:

```bash
make build-mcp
```

### Configure Your Client

| Variable | Required | Description |
|----------|----------|-------------|
| `SKILLHIVE_URL` | Yes | SkillHive URL; must be `https://` (`http://localhost:8080` is allowed for local testing) |
| `SKILLHIVE_TOKEN` | Yes | Dedicated MCP token or legacy read-only API token |
| `SKILLHIVE_MCP_MAX_FILE_BYTES` | No | Per-file read limit, default 10485760 |

Claude Code:

```bash
claude mcp add skillhive \
  -e SKILLHIVE_URL=https://skillhive.example.com \
  -e SKILLHIVE_TOKEN=sk_xxx \
  -- node /path/to/skillhive/mcp/dist/index.js
```

Other clients (JSON config):

```json
{
  "mcpServers": {
    "skillhive": {
      "command": "node",
      "args": ["/path/to/skillhive/mcp/dist/index.js"],
      "env": {
        "SKILLHIVE_URL": "https://skillhive.example.com",
        "SKILLHIVE_TOKEN": "sk_xxx"
      }
    }
  }
}
```

### Tools

| Tool | Purpose | Requires |
|------|---------|----------|
| `whoami` | Current user, token scopes and teams | — |
| `search_skills` | Search skills | Skills read-only |
| `get_skill` / `list_skill_versions` | Skill details / versions | Skills read-only |
| `list_skill_files` / `read_skill_file` | Package file list / read a file | Skills read-only |
| `list_my_skills` | Skills you published, starred or subscribed to | Skills read-only |
| `list_knowledge_bases` / `browse_knowledge_base` | Knowledge bases / folders and files | Knowledge read-only |
| `search_knowledge` | Search files by title and description | Knowledge read-only |
| `get_document` / `read_document` | File details and versions / read the original file | Knowledge read-only |
| `upload_skill` / `delete_skill` | Upload a new skill ZIP / archive a skill | Skills upload-new / archive |
| `upload_document` / `delete_document` | Upload a new file / archive a file | Knowledge upload-new / archive |

File content is data and may contain prompt injection aimed at AI tools. The MCP server labels it as such before returning it; whether to act on it is up to you and your AI client.
