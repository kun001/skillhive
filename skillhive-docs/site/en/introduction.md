# Introduction

SkillHive is the private context center for your team’s agents. It runs on your own server and hands the team’s practices and files to the agents members already use (Claude Code, Cursor, your own agents or any other MCP client), within their permissions. It keeps two kinds of team assets in the same team space:

- **Skill library**: reusable AI skill packages in the `SKILL.md` format. Each upload goes through structure validation, security scanning and human review before release, so risky scripts do not slip into the team.
- **Knowledge base**: the team's file center. Templates, handbooks and project files are organized in folders, previewed in the browser and versioned; they are visible to permitted members as soon as they are uploaded.

Opening a team space shows both how to do the work (skills) and what to work with (files). Agents reach the same content over MCP with the member’s own token; permissions are checked on every call and reads and writes are audited. All data stays on your own servers.

## Highlights

| Feature | Description |
|---------|-------------|
| Self-hosted | Deploy with Docker Compose or Kubernetes on your own servers or intranet; store files on local disk, S3 or MinIO |
| Review before release | Skills are validated, scanned and reviewed before release; search and downloads use published versions only |
| Fine-grained permissions | On top of space roles (owner / admin / member), set read-only, edit and download permission per member; changes apply immediately |
| Preview and history | Preview PDF, images, Markdown, Word and PowerPoint in the browser; every update keeps a version you can download or restore |
| Works with any AI tool | Skills use the common `SKILL.md` format; download them into your AI tool, or let it read them through the read-only MCP server |
| Auditable | Publishing, reviews, permission changes, knowledge file downloads and previews, and skill reads through tokens are audit-logged |

## Scope

- SkillHive does not run skill scripts.
- Knowledge search matches titles, descriptions, AI summaries and extracted text; there is no vector search, RAG Q&A or OCR for scans.
- Knowledge content is never open to anonymous users; public skills in the public library can be browsed and downloaded anonymously.

## Tech Stack

| Layer | Technology |
|-------|------------|
| Frontend | React 19, TypeScript, Vite, TanStack Router / Query |
| Backend | Java 21, Spring Boot, Maven multi-module |
| Data | PostgreSQL 16, Redis 7 |
| File storage | Local disk, S3 or MinIO |
| Security scanning | Python (Cisco AI Skill Scanner) |
| MCP server | TypeScript (local stdio) |

## Guides

| Guide | Description |
|-------|-------------|
| [Skill Publishing & Versioning](/en/guide/skill-publish) | Upload skill packages, versions, compliance declarations |
| [Skill Search & Discovery](/en/guide/skill-discovery) | Public and team libraries, search and filters |
| [Knowledge Base](/en/guide/knowledge) | Folders, uploads, preview, versions |
| [Namespace & Team Management](/en/guide/namespace) | Team spaces, members and permissions |
| [Review & Governance](/en/guide/review) | Review workflow, reports |
| [Security Scanning](/en/guide/scanner) | Skill package scanning |
| [Social & Interaction](/en/guide/social) | Stars, ratings, subscriptions, notifications |
| [API Tokens & MCP](/en/guide/api-token-mcp) | Personal tokens and read-only AI tool access |
