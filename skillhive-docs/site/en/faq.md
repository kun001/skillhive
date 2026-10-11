# FAQ

## Q: How is SkillHive related to SkillHub?

A: SkillHive is built on the open-source [SkillHub](https://github.com/iflytek/skillhub). It keeps skill upload, versions, security scanning, team space permissions, reviews, notifications and audit logs; adds the knowledge base (a team file center), per-member edit and download permissions and a read-only MCP server; and removes the CLI, the ClawHub-compatible API, skill suites and device-code sign-in. Environment variables, the Helm chart and backend modules still use the `skillhub` prefix.

## Q: What is the recommended deployment method?

A: Build the images from this repository and deploy with `compose.release.yml`; see [Quick Start](/en/quickstart). The default images in `.env.release.example` point to upstream SkillHub and do not contain SkillHive's features, so replace them with your own builds.

## Q: How do I back up data?

A: Data lives in PostgreSQL and file storage; back up both regularly:

```bash
# Database (default name: skillhub)
docker compose --env-file .env.release -f compose.release.yml exec postgres \
  pg_dump -U skillhub skillhub > backup.sql
```

For file storage, back up the volume or directory when using local disk, or use your S3 / MinIO backup tooling.

## Q: Which sign-in methods are supported?

A: Local accounts (username and password, with an optional first administrator created on deployment), plus GitHub, GitLab, Feishu, DingTalk and generic OIDC. Private SSO can be connected through session bootstrap or direct authentication. See [Deployment](https://github.com/kun001/skillhive/blob/main/skillhive-docs/09-deployment.md) and [Authentication Design](https://github.com/kun001/skillhive/blob/main/skillhive-docs/03-authentication-design.md) in the repository.

## Q: What are the requirements and limits for skill packages?

A: The package root must contain `SKILL.md` with `name` and `description` in its frontmatter. By default a package may contain up to 100 files, each up to 10MB, and up to 100MB in total; file extensions must be on the allowlist.

## Q: How many files can a skill package contain? What if I hit the file-count limit?

A: The default limit is **100 files** (this is separate from the 100MB size limit). To raise it, change the `skillhub.publish.max-file-count` setting, or override it via an environment variable at deploy time:

```bash
SKILLHUB_PUBLISH_MAX_FILE_COUNT=500
```

Recreate the containers for the change to take effect; `restart` alone does not re-inject environment variables.

## Q: Publishing fails with "package validation failed / malformed input" — what do I do?

A: This error occurs while unzipping and reading file names, usually because the archive is not UTF-8 encoded (e.g. created with the built-in Windows compression tool) or contains Chinese/non-ASCII paths. Repackage using UTF-8 encoding and avoid Chinese / special-character paths.

## Q: Publishing a skill fails with 400 — how do I troubleshoot it?

A: A 400 usually means backend validation failed. Common causes:

- `SKILL.md` is not at the package root, or its frontmatter is missing `name` / `description` or malformed;
- Name conflict (`error.skill.publish.nameConflict`: a published skill with the same name already exists in that space) — change `name`, use another space, or ask an admin to handle the existing skill;
- The target space does not exist, or you are not a member / lack edit permission;
- You chose Public or Team visibility while security scanning is disabled (without scanning, only private skills can be published);
- File types, sizes or paths are not allowed.

Check the server logs:

```bash
docker compose --env-file .env.release -f compose.release.yml logs --tail=300 server \
  | grep -Ei 'publish|SKILL.md|namespace|BadRequest'
```

## Q: Can unreviewed skills be downloaded?

A: Regular members can only browse and download published versions. Authors can preview and download their own unpublished versions to check them before review.

## Q: Can a skill have a Chinese name?

A: The `name` in `SKILL.md` becomes the skill identifier (slug) and allows only lowercase letters, digits and hyphens. Display names and descriptions can be in any language.

## Q: Which files does the knowledge base support? Can I search file contents?

A: PDF, images, Markdown / text, Word / PowerPoint (including legacy formats), spreadsheets / data files and archives, up to 100 MiB per file. Search matches file titles and descriptions only (the title defaults to the file name); it does not index contents and offers no document parsing or RAG Q&A. See [Knowledge Base](/en/guide/knowledge).

## Q: How can AI tools such as Claude Code or Cursor use SkillHive content?

A: Either:

- Download a skill's zip package on the web and unzip it into your AI tool's skills directory (such as `.claude/skills/`); or
- Create a read-only API token and configure the local MCP server so your AI tool can search and read the skills and knowledge files you have access to. See [API Tokens & MCP](/en/guide/api-token-mcp).

## Q: I'm a platform administrator — why can't my token see other teams' knowledge bases?

A: By design. Token access to the knowledge base is decided by team membership only; platform administrator roles never widen what a token can read, so the whole platform's files are never handed to an AI tool. Use the web session when you need a platform-wide view.

## Q: How do I configure HTTPS?

A: Put a reverse proxy such as Nginx or Traefik with your certificate in front of the web container and forward all requests to it (the web container forwards `/api/*`, `/oauth2/*` and similar paths to the backend). Set `SKILLHUB_PUBLIC_BASE_URL` to the final HTTPS URL.

```nginx
server {
    listen 443 ssl;
    server_name skillhive.example.com;
    ssl_certificate /path/to/cert.pem;
    ssl_certificate_key /path/to/key.pem;

    location / {
        proxy_pass http://127.0.0.1:80;
        proxy_set_header Host $host;
        proxy_set_header X-Forwarded-Proto https;
    }
}
```

## Q: How do I monitor SkillHive?

A: Backend health: `GET /actuator/health`; scanner health: `GET http://<scanner>:8000/health`. Prometheus metrics at `/actuator/prometheus` require `SUPER_ADMIN` or `AUDITOR`. Key operations are recorded in the admin audit log. Logging and tracing setup is covered in the deployment doc.

## Q: Does it support multi-tenancy?

A: SkillHive is a single-instance team platform. Team spaces are collaboration and permission boundaries, not tenant isolation; deploy separate instances when you need physical isolation.

## Q: How do I upgrade?

A: Build and push new images, update `SKILLHUB_VERSION` in `.env.release`, then:

```bash
docker compose --env-file .env.release -f compose.release.yml pull
docker compose --env-file .env.release -f compose.release.yml up -d
```

Flyway migrates the database on startup without clearing existing data. Back up the database and file storage first.

## Q: How do I change the admin password?

A: Sign in and change it under Settings → Security. `BOOTSTRAP_ADMIN_PASSWORD` only applies when the first administrator is created; changing the variable later does not change an existing account. A forgotten password can be reset with an email code once SMTP is configured; see [SMTP setup](https://github.com/kun001/skillhive/blob/main/skillhive-docs/19-smtp-password-reset-email-setup.md).

## Q: How do I hide or remove third-party SSO login options on the login page?

A: Login entries are config-driven: `/api/v1/auth/methods` only returns registrations that have a real client id. When a client id is empty or contains `placeholder`, that entry never reaches the login page.

So there are two ways to hide one:

- Leave the matching environment variable unset (for example, omit `OAUTH2_FEISHU_CLIENT_ID`). No config file change needed.
- Or edit `application.yml` and comment out or delete the relevant registration block (`github`, `gitlab`, `feishu`, `dingtalk`) under `spring.security.oauth2.client.registration`, along with its `provider` section. Spring Boot then won't create that registration at startup.

## Q: What powers security scanning?

A: Scanner integration, task orchestration and auditing are implemented in this project; the scanning engine is Cisco's [cisco-ai-skill-scanner](https://github.com/cisco-ai-defense/skill-scanner) (Apache License 2.0). `scanner/Dockerfile` installs it without a pinned version, so image builds pull the latest release from PyPI; pin it yourself if needed.

## Q: Is MySQL supported?

A: No. Only PostgreSQL is supported.

## Q: The page loads, but the login / register APIs return 502?

A: The page is served by the `web` container, while login, register and other APIs are proxied by `web` to `server` (default `SKILLHUB_API_UPSTREAM=http://server:8080`). When the page works but the API returns 502, check whether `server` started correctly first; a wrong upstream, DNS, or container-network problem can also produce a 502.

Troubleshooting order:

```bash
# 1. Check whether server is running
docker compose --env-file .env.release -f compose.release.yml ps

# 2. Look at the first error in the server startup log
docker compose --env-file .env.release -f compose.release.yml logs server | head -50
```

One common startup failure is:

```
SKILLHUB_DOWNLOAD_ANON_COOKIE_SECRET must not use the default placeholder
```

This means `server` still reads the placeholder from the template. Replace it in `.env.release` with your own random string (**at least 32 characters**) and recreate the containers:

```bash
SKILLHUB_DOWNLOAD_ANON_COOKIE_SECRET=<your own random string, at least 32 characters>
```

Running `make validate-release-config` before startup validates `.env.release` and surfaces placeholders and missing values early.

## Q: Why doesn't my configuration change take effect?

A: Two common causes:

1. **Edited the wrong file**: `.env.release.example` is only a template; Compose reads the file passed via `--env-file`, i.e. `.env.release`. Run `cp .env.release.example .env.release` first, then edit `.env.release`.
2. **Restarted instead of recreated**: environment variables are injected when the container is created, and `restart` does not re-inject them. Recreate the containers after a config change:

```bash
docker compose --env-file .env.release -f compose.release.yml up -d --force-recreate
```

## Q: What external dependencies are required at runtime?

A: PostgreSQL and Redis are required. File storage supports `local` and S3, selected by `SKILLHUB_STORAGE_PROVIDER`. `.env.release.example` sets `local` explicitly, but if the variable is missing entirely, `compose.release.yml` falls back to `s3`, so always set it. The release Compose file includes PostgreSQL and Redis bound to `127.0.0.1` by default.

## Q: What should I do when PostgreSQL reports `operation not permitted` while writing `postmaster.pid` or `pg_wal`?

A: SkillHive's default Compose and `the release Compose file` use a Docker named volume (`postgres_data`), so host-directory permissions normally do not need manual changes. This error is more common after replacing that volume with a host bind mount, such as `/data/skillhive/postgres:/var/lib/postgresql/data`.

Check the following in order:

1. Prefer switching back to a Docker named volume, or use the official `the release Compose file` to avoid missing permission settings in a hand-written Compose file.
2. If a bind mount is required, first identify the effective `POSTGRES_IMAGE` selected by `.env.release` or the `the release Compose file` options. Export that value in the current shell, then run `docker run --rm "$POSTGRES_IMAGE" id postgres`. Change the data-directory owner to the reported UID/GID, for example `chown -R <uid>:<gid> <data-dir>`. Do not assume the image is `postgres:16-alpine`, or that every environment uses `999:999`.
3. Check SELinux on RHEL/CentOS. With AppArmor, rootless Docker, NFS, CIFS, or NAS storage, also verify that the host filesystem permits PostgreSQL to write, lock files, and change permissions.
4. Avoid placing PostgreSQL `PGDATA` on network filesystems without full POSIX permission semantics. For production, prefer local disks, Docker named volumes, block storage, or an external PostgreSQL service.

## Q: How does an account created through OAuth (GitHub / GitLab, etc.) get admin rights?

A: The first OAuth login creates a regular user. An existing `SUPER_ADMIN` (for example the bootstrap admin created during initialization) has to promote it from the admin console.

A `USER_ADMIN` can manage user status and assign platform roles other than `SUPER_ADMIN`, but cannot grant `SUPER_ADMIN` to any account or change the role of an existing `SUPER_ADMIN`. Only a `SUPER_ADMIN` can perform those two operations.

## Q: What should I do if local development fails to start?

A: When `make dev-all` fails to start the backend, detailed error messages will be displayed. Common issues:

### 1. Maven dependency download failed (network timeout)

**Symptoms**: Backend logs show `Could not transfer artifact` or connection timeout

**Solution**: Configure Aliyun mirror

```bash
# Copy the project's built-in mirror configuration to user directory
mkdir -p ~/.m2
cp server/.mvn/settings.xml ~/.m2/settings.xml
```

Or manually create `~/.m2/settings.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<settings>
  <mirrors>
    <mirror>
      <id>aliyun</id>
      <url>https://maven.aliyun.com/repository/public</url>
      <mirrorOf>central</mirrorOf>
    </mirror>
  </mirrors>
</settings>
```

Reference: [Aliyun Maven Mirror Configuration Guide](https://maven.aliyun.com/mvn/guide)

### 2. Java version mismatch

**Symptoms**: `Unsupported class file major version` or `java.lang.NoSuchMethodError`

**Solution**: Install Java 21+

```bash
# macOS
brew install openjdk@21

# Verify version
java -version
```

### 3. Port already in use

**Symptoms**: `Port 8080 already in use`

**Solution**:

```bash
# Find the process using the port
lsof -i :8080

# Terminate the process
kill -9 <PID>
```

### 4. View detailed logs

If the above solutions don't help, check the backend logs:

```bash
make dev-logs SERVICE=backend
# Or view directly
cat .dev/server.log
```

## Q: What should I do if I run into a problem?

A: Check this page and the design docs in the repository (`skillhive-docs/`). If that does not help, open an issue on [GitHub Issues](https://github.com/kun001/skillhive/issues) with the version, deployment method and relevant logs (remove passwords, tokens and other secrets).
