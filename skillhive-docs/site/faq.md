# 常见问题

## Q: SkillHive 和 SkillHub 是什么关系？

A: SkillHive 基于开源的 [SkillHub](https://github.com/iflytek/skillhub) 构建，保留了技能上传、版本、安全扫描、团队空间权限、审核、通知与审计，新增了知识库（团队文件中心）、成员级读写与下载权限、只读 MCP 服务等能力，并删除了 CLI、ClawHub 兼容接口、技能套件和设备码登录。环境变量、Helm chart 和后端模块名仍沿用 `skillhub` 前缀。

## Q: 推荐的部署方式是什么？

A: 使用 `compose.release.yml` 部署，步骤见 [快速开始](/quickstart)。默认镜像是本仓库发布到 GHCR 的 `ghcr.io/kun001/skillhive-*`；也可以替换为自建镜像。

## Q: 如何备份数据？

A: 数据存放在 PostgreSQL 和文件存储中，定期备份这两部分即可：

```bash
# 数据库（默认库名 skillhub）
docker compose --env-file .env.release -f compose.release.yml exec postgres \
  pg_dump -U skillhub skillhub > backup.sql
```

文件存储：使用本地磁盘时备份对应数据卷或目录；使用 S3 / MinIO 时使用对应的备份工具。

## Q: 支持哪些登录方式？

A: 本地账号（用户名密码，部署时可初始化首个管理员），以及 GitHub、GitLab、飞书、钉钉和通用 OIDC 登录；私有 SSO 可通过会话引导或直连认证扩展接入。配置方法见仓库中的 [部署架构与运维](https://github.com/kun001/skillhive/blob/main/skillhive-docs/09-deployment.md) 和 [认证与授权设计](https://github.com/kun001/skillhive/blob/main/skillhive-docs/03-authentication-design.md)。

## Q: 技能包有什么要求和限制？

A: 根目录必须包含 `SKILL.md`，其 frontmatter 需要 `name` 和 `description`。默认最多 100 个文件，单个文件不超过 10MB，整包不超过 100MB，文件扩展名需在白名单内。

## Q: 技能包能包含多少个文件？提示文件数超限怎么办？

A: 默认上限为 **100 个文件**（这与单文件 10MB、整包 100MB 的大小限制是两回事）。如需放宽，修改配置项 `skillhub.publish.max-file-count`，或在部署时用环境变量覆盖：

```bash
SKILLHUB_PUBLISH_MAX_FILE_COUNT=500
```

修改后需重新创建容器才会生效；仅执行 `restart` 不会重新注入环境变量。注意 `compose.release.yml` 中也需引用该变量。

## Q: 发布时报“技能包校验失败 / malformed input”怎么办？

A: 该错误发生在 zip 解包读取文件名阶段，通常是压缩包不是 UTF-8 编码（例如用 Windows 自带压缩工具生成）或包内含中文路径导致。请使用 UTF-8 编码重新打包，并避免中文 / 特殊字符路径。

## Q: 发布技能时报错 400 怎么排查？

A: 400 通常表示后端校验未通过。常见原因：

- `SKILL.md` 不在技能包根目录，或 frontmatter 缺少 `name` / `description`、格式错误；
- 名称冲突（`error.skill.publish.nameConflict`，该空间下已存在同名的已发布技能）：修改 `SKILL.md` 里的 `name`、换一个空间，或让管理员处理已有技能；
- 目标空间不存在，或你不是该空间的成员 / 没有编辑权限；
- 选择「公开」或「团队可见」时未启用安全扫描（关闭扫描后只能发布私有技能）；
- 文件类型、大小或路径不合规。

可查看服务端日志定位：

```bash
docker compose --env-file .env.release -f compose.release.yml logs --tail=300 server \
  | grep -Ei 'publish|SKILL.md|namespace|BadRequest'
```

## Q: 未审核的技能可以下载吗？

A: 普通成员只能浏览和下载已发布版本。作者本人可以预览、下载自己尚未发布的版本，用于提交审核前自查。

## Q: 技能可以起中文名吗？

A: `SKILL.md` 的 `name` 会作为技能标识（slug），只支持小写字母、数字和连字符。展示名称和说明可以使用中文。

## Q: 知识库支持哪些文件？能搜索文件内容吗？

A: 支持 PDF、图片、Markdown / 文本、Word / PowerPoint（含旧版格式）、表格 / 数据文件和压缩包，单个文件上限 100 MiB。搜索只匹配文件标题和描述（标题默认取自文件名），不检索正文，也不提供文档解析或 RAG 问答。详见 [知识库](/guide/knowledge)。

## Q: 如何让 Claude Code、Cursor 等 AI 工具使用 SkillHive 的内容？

A: 两种方式：

- 在网页下载技能 zip 包，解压到 AI 工具的技能目录（如 `.claude/skills/`）；
- 创建一个只读 API Token，配置本地 MCP 服务，让 AI 工具直接搜索、读取你有权限的技能和知识库文件。见 [API Token 与 MCP](/guide/api-token-mcp)。

## Q: 我是平台管理员，为什么用 Token 看不到其他团队的知识库？

A: 这是有意的设计。通过 API Token 访问知识库时只按团队成员身份判断，平台管理员角色不扩大 Token 的访问范围，避免把全平台资料交给 AI 工具。需要全局查看时请使用网页登录。

## Q: 如何配置 HTTPS？

A: 在 Web 容器前放置 Nginx、Traefik 等反向代理并配置证书，把所有请求转发到 Web 容器即可（Web 容器会把 `/api/*`、`/oauth2/*` 等转发给后端）。同时把 `SKILLHUB_PUBLIC_BASE_URL` 设为最终的 HTTPS 地址。

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

## Q: 如何监控运行状态？

A: 后端健康检查 `GET /actuator/health`，扫描器健康检查 `GET http://<scanner>:8000/health`；Prometheus 指标 `/actuator/prometheus` 需要 `SUPER_ADMIN` 或 `AUDITOR` 身份。关键操作记录在管理后台的审计日志中。日志与链路追踪配置见仓库中的部署文档。

## Q: 支持多租户吗？

A: SkillHive 是单实例的团队平台。团队空间是协作与授权边界，但不是租户隔离；需要物理隔离时，请为每个组织部署独立实例。

## Q: 如何升级？

A: 修改 `.env.release` 中的 `SKILLHUB_VERSION`（使用 `latest` 或 `edge` 时无需修改），然后：

```bash
docker compose --env-file .env.release -f compose.release.yml pull
docker compose --env-file .env.release -f compose.release.yml up -d
```

数据库迁移由 Flyway 在启动时自动执行，不会清空已有数据。升级前请备份数据库和文件存储。

## Q: 如何修改 admin 密码？

A: 登录后在「个人设置 → 安全设置」中修改。`BOOTSTRAP_ADMIN_PASSWORD` 只在首次初始化管理员时生效，之后修改该环境变量不会改变已有账号的密码。忘记密码时可以通过邮箱验证码找回，需要先配置 SMTP，见仓库中的 [SMTP 邮箱配置指南](https://github.com/kun001/skillhive/blob/main/skillhive-docs/19-smtp-password-reset-email-setup.md)。

## Q: 如何隐藏或删除登录页的第三方 SSO 登录方式？

A: 登录入口是配置驱动的：`/api/v1/auth/methods` 只返回配置了真实 client id 的
注册，client id 为空或包含 `placeholder` 时该入口不会出现在登录页。

所以隐藏某个入口有两种方式：

- 留空对应的环境变量即可（例如不设置 `OAUTH2_FEISHU_CLIENT_ID`），无需改动配置文件。
- 或修改 `application.yml`，注释/删除 `spring.security.oauth2.client.registration`
  下对应的注册块（`github`、`gitlab`、`feishu`、`dingtalk`）以及对应的 `provider` 段，
  Spring Boot 启动时便不会创建该注册。

## Q: 安全扫描使用什么？

A: 扫描接入、任务编排和审计由本项目实现；底层扫描服务使用 Cisco 的 [cisco-ai-skill-scanner](https://github.com/cisco-ai-defense/skill-scanner)（Apache License 2.0）。`scanner/Dockerfile` 通过构建参数 `SKILL_SCANNER_VERSION` 固定版本（当前为 `1.0.2`），并应用了仓库内的兼容补丁；升级时请同步检查 `scanner/backports/`。

## Q: 支持 MySQL 吗？

A: 不支持，数据库仅支持 PostgreSQL。

## Q: 页面能打开，但登录 / 注册接口返回 502？

A: 页面由 `web` 容器提供，登录、注册等接口由 `web` 转发给 `server`（默认 `SKILLHUB_API_UPSTREAM=http://server:8080`）。出现「页面正常但 API 502」时，通常先检查 `server` 是否正常启动；upstream 配置、DNS 或容器网络异常也可能返回 502。

排查顺序：

```bash
# 1. 看 server 是否处于运行状态
docker compose --env-file .env.release -f compose.release.yml ps

# 2. 看 server 启动日志中的第一条错误
docker compose --env-file .env.release -f compose.release.yml logs server | head -50
```

一条常见的启动失败日志是：

```
SKILLHUB_DOWNLOAD_ANON_COOKIE_SECRET must not use the default placeholder
```

说明 `server` 读到的仍是模板里的占位值。在 `.env.release` 中改成自己的随机字符串（**至少 32 个字符**）后重建容器即可：

```bash
SKILLHUB_DOWNLOAD_ANON_COOKIE_SECRET=<替换成你自己的随机字符串，至少 32 个字符>
```

启动前可以先执行 `make validate-release-config`，它会校验 `.env.release`，提前暴露这类占位值和缺失项。

## Q: 改了配置为什么不生效？

A: 两个高频原因：

1. **改错了文件**：`.env.release.example` 只是模板，Compose 实际读取的是 `--env-file` 指定的 `.env.release`。请先 `cp .env.release.example .env.release`，然后修改 `.env.release`。
2. **只重启没重建**：环境变量在容器创建时注入，`restart` 不会重新注入。改完配置需要重建容器：

```bash
docker compose --env-file .env.release -f compose.release.yml up -d --force-recreate
```

## Q: 运行时需要哪些外部依赖？

A: 必需 PostgreSQL 和 Redis；文件存储支持 `local` 与 S3 两种模式，由 `SKILLHUB_STORAGE_PROVIDER` 控制。`.env.release.example` 显式配置为 `local`，但如果使用 `compose.release.yml` 时完全没有设置该变量，Compose 的回退值是 `s3`，建议始终显式设置。发布版 Compose 已内置 PostgreSQL 与 Redis，默认只绑定在 `127.0.0.1`。

## Q: PostgreSQL 容器写入 `postmaster.pid` 或 `pg_wal` 时报告 `operation not permitted` 怎么办？

A: SkillHive 默认的 Compose 使用 Docker named volume（`postgres_data`），通常不需要手工处理宿主机目录权限。这个错误更常见于将 PostgreSQL 数据目录改成宿主机 bind mount，例如 `/data/skillhive/postgres:/var/lib/postgresql/data`。

按以下顺序排查：

1. 优先恢复为 Docker named volume，避免手写 Compose 时漏配权限。
2. 如果必须使用 bind mount，先确认 `.env.release` 最终选择的 `POSTGRES_IMAGE`，将该值导出到当前 shell 后运行 `docker run --rm "$POSTGRES_IMAGE" id postgres`。再按输出的实际 UID/GID 调整数据目录属主，例如 `chown -R <uid>:<gid> <数据目录>`。不要固定假设镜像是 `postgres:16-alpine`，也不要假设所有环境都是 `999:999`。
3. 在 RHEL/CentOS 上检查 SELinux；使用 AppArmor、rootless Docker、NFS、CIFS 或 NAS 时，也要确认宿主文件系统允许 PostgreSQL 写入、加锁和更改权限。
4. 不建议把 PostgreSQL `PGDATA` 放在缺少完整 POSIX 权限语义的网络文件系统上。生产环境优先使用本地盘、Docker named volume、块存储或外部 PostgreSQL。

## Q: 通过 OAuth（GitHub / GitLab 等）登录的账号，如何取得管理员权限？

A: OAuth 首次登录创建的是普通用户。需要由已有的 `SUPER_ADMIN`（例如初始化时的 bootstrap admin）在后台将其提升为管理员。

`USER_ADMIN` 可以管理用户状态，并分配除 `SUPER_ADMIN` 之外的平台角色；但不能向任何账号授予 `SUPER_ADMIN`，也不能修改已有 `SUPER_ADMIN` 账号的角色。这两类操作只有 `SUPER_ADMIN` 可以执行。

## Q: 本地开发启动失败怎么办？

A: `make dev-all` 后端启动失败时，会显示详细的错误提示。常见问题：

### 1. Maven 依赖下载失败（网络超时）

**症状**：后端日志显示 `Could not transfer artifact` 或连接超时

**解决方案**：配置阿里云镜像

```bash
# 复制项目内置的镜像配置到用户目录
mkdir -p ~/.m2
cp server/.mvn/settings.xml ~/.m2/settings.xml
```

或手动创建 `~/.m2/settings.xml`：

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

参考：[阿里云 Maven 镜像配置指南](https://maven.aliyun.com/mvn/guide)

### 2. Java 版本不匹配

**症状**：`Unsupported class file major version` 或 `java.lang.NoSuchMethodError`

**解决方案**：安装 Java 21+

```bash
# macOS
brew install openjdk@21

# 验证版本
java -version
```

### 3. 端口被占用

**症状**：`Port 8080 already in use`

**解决方案**：

```bash
# 查看占用端口的进程
lsof -i :8080

# 终止进程
kill -9 <PID>
```

### 4. 查看详细日志

如果以上方案无法解决，查看后端日志：

```bash
make dev-logs SERVICE=backend
# 或直接查看
cat .dev/server.log
```

## Q: 遇到问题怎么办？

A: 先查看本页和仓库中的设计文档（`skillhive-docs/`），仍无法解决时在 [GitHub Issues](https://github.com/kun001/skillhive/issues) 提交问题，并附上版本、部署方式和相关日志（注意去掉密码、Token 等敏感信息）。
