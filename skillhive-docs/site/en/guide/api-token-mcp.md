# API Tokens & MCP

## API Tokens

An API token is a personal credential that lets scripts and AI tools access SkillHive as you. It follows the same permissions as web sign-in: it only reaches what you can access.

Create tokens under Profile → Tokens and choose their access:

| Access | Scopes | What it allows |
|--------|--------|----------------|
| Skills · read-write | `skill:read`, `skill:publish`, `skill:delete` | View, download, publish and delete skills |
| Skills · read-only | `skill:read` | View and download skills, My Skills, stars and subscriptions |
| Knowledge (read-only) | `knowledge:read` | View knowledge bases, folders and files, and read file content (needs download permission) |

- Skill and knowledge access are kept apart: a knowledge-only token cannot read skills, and vice versa.
- Uploading, editing and deleting knowledge content does not accept tokens; use the web for that.
- Token access to the knowledge base is decided by team membership only; even an administrator's token cannot see teams the administrator is not in.
- The token value is shown only once, when it is created. Delete a token from the list to revoke it immediately if it leaks or is no longer needed.
- Reading knowledge files or skill files, or downloading skill packages, with a token is audit-logged with the token that was used.

Usage:

```bash
curl -H "Authorization: Bearer $SKILLHIVE_TOKEN" https://skillhive.example.com/api/v1/knowledge/bases
```

## MCP Server

`mcp/` in the repository is a local stdio MCP server that lets MCP clients such as Claude Code and Cursor read the skill library and knowledge base with your token:

- **Read-only**: the server refuses to start with a token that can publish, delete or manage tokens. Create a token with Skills · read-only and/or Knowledge (read-only).
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
| `SKILLHIVE_TOKEN` | Yes | Read-only API token |
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

File content is data and may contain prompt injection aimed at AI tools. The MCP server labels it as such before returning it; whether to act on it is up to you and your AI client.
