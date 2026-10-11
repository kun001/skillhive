# Quick Start

This page shows how to deploy SkillHive and get started: create a team space, publish a skill, upload files and connect an AI tool.

## Deploy

Every SkillHive service runs in a container: the web app and backend API, the skill scanner, PostgreSQL, Redis, and file storage on local disk, S3 or MinIO.

### 1. Choose the images

By default SkillHive uses the images published by this repository's GitHub Actions:

| Component | Image |
|-----------|-------|
| Backend | `ghcr.io/kun001/skillhive-server` |
| Frontend | `ghcr.io/kun001/skillhive-web` |
| Scanner | `ghcr.io/kun001/skillhive-scanner` |

Pushes to `main` publish `edge`; `vX.Y.Z` tags publish that version and `latest`. You can also build from this repository and push to your own registry:

```bash
docker buildx build -t <registry>/skillhive-server:<tag> -f server/Dockerfile server --push
docker buildx build -t <registry>/skillhive-web:<tag> -f web/Dockerfile web --push
docker buildx build -t <registry>/skillhive-scanner:<tag> -f scanner/Dockerfile scanner --push
```

### 2. Prepare the configuration

```bash
cp .env.release.example .env.release
```

Change at least:

| Variable | Description |
|----------|-------------|
| `SKILLHUB_VERSION` | Image tag: `latest` (latest release), `edge` (latest `main` build) or a fixed `vX.Y.Z` |
| `SKILLHUB_SERVER_IMAGE` / `SKILLHUB_WEB_IMAGE` / `SKILLHUB_SCANNER_IMAGE` | Change only when using your own builds (without tag) |
| `SKILLHUB_PUBLIC_BASE_URL` | Final public URL; use an HTTPS domain in production, no trailing `/` |
| `POSTGRES_PASSWORD` | Database password |
| `BOOTSTRAP_ADMIN_PASSWORD` | First administrator's password; the default must be changed |
| `SKILLHUB_DOWNLOAD_ANON_COOKIE_SECRET` | Random value of at least 32 characters |
| `SKILLHUB_STORAGE_PROVIDER` and `SKILLHUB_STORAGE_S3_*` | File storage; local disk by default |

See [Deployment](https://github.com/kun001/skillhive/blob/main/skillhive-docs/09-deployment.md) in the repository for details.

### 3. Start

```bash
make validate-release-config
docker compose --env-file .env.release -f compose.release.yml up -d
```

Then open `SKILLHUB_PUBLIC_BASE_URL`. For Kubernetes, see [Kubernetes Deployment](/en/guide/kubernetes).

## Local Development

```bash
make dev-all     # PostgreSQL, Redis, MinIO, scanner, plus the backend and frontend on the host
make dev-all-down
```

The web app runs at `http://localhost:3000` and the backend API at `http://localhost:8080`.

## Sign In

The first administrator (`BOOTSTRAP_ADMIN_USERNAME`, `admin` by default) is created on first start; sign in with the `BOOTSTRAP_ADMIN_PASSWORD` you set and change the password right away. GitHub, GitLab, Feishu, DingTalk and OIDC sign-in can be enabled as described in the deployment doc.

## First Steps

### 1. Create a team space

A platform administrator (`SKILL_ADMIN` or `SUPER_ADMIN`) creates the team space under Namespace Management in the admin area, and the space admin adds members. Each member can be set to read-only or editable, with or without download permission. See [Namespace & Team Management](/en/guide/namespace).

### 2. Publish your first skill

Prepare a directory that contains `SKILL.md`:

```markdown
---
name: meeting-notes
description: Turn meeting notes into decisions and follow-up actions.
---

# Meeting Notes

Read the meeting notes, summarize the decisions by topic, and list action items with owners and due dates.
```

Zip it, click "Publish skill" on the Skills page, choose the team space and visibility, and upload. The skill is released after validation, security scanning and review. See [Skill Publishing & Versioning](/en/guide/skill-publish).

### 3. Upload files to the knowledge base

A space admin creates a knowledge base for the team space under Knowledge; members can then create folders and upload files. See [Knowledge Base](/en/guide/knowledge).

### 4. Connect an AI tool (optional)

Create a read-only token under Profile → Tokens and configure the local MCP server; your AI tool can then read the skills and files you have access to. See [API Tokens & MCP](/en/guide/api-token-mcp).

## Upgrade

Update `SKILLHUB_VERSION` in `.env.release` (not needed for `latest` or `edge`), then run:

```bash
docker compose --env-file .env.release -f compose.release.yml pull
docker compose --env-file .env.release -f compose.release.yml up -d
```

Flyway runs database migrations automatically on startup. Back up the database and file storage before upgrading.
