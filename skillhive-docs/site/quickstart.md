# 快速开始

本文介绍如何部署 SkillHive，并完成第一次使用：建立团队空间、发布技能、上传资料，以及让 AI 工具接入。

## 部署

SkillHive 所有服务都以容器运行：Web 与后端 API、技能扫描服务、PostgreSQL、Redis，以及本地磁盘 / S3 / MinIO 文件存储。

### 1. 构建镜像

SkillHive 尚未发布官方镜像，`.env.release.example` 中的默认镜像指向上游 SkillHub，并不包含 SkillHive 的功能。请从本仓库构建镜像并推送到自己的镜像仓库：

```bash
docker buildx build -t <registry>/skillhive-server:<tag> -f server/Dockerfile server --push
docker buildx build -t <registry>/skillhive-web:<tag> -f web/Dockerfile web --push
docker buildx build -t <registry>/skillhive-scanner:<tag> -f scanner/Dockerfile scanner --push
```

### 2. 准备配置

```bash
cp .env.release.example .env.release
```

至少修改以下配置：

| 变量 | 说明 |
|------|------|
| `SKILLHUB_SERVER_IMAGE` / `SKILLHUB_WEB_IMAGE` / `SKILLHUB_SCANNER_IMAGE` | 第 1 步推送的镜像（不含 tag） |
| `SKILLHUB_VERSION` | 镜像 tag |
| `SKILLHUB_PUBLIC_BASE_URL` | 最终访问地址，生产环境使用 HTTPS 域名，末尾不带 `/` |
| `POSTGRES_PASSWORD` | 数据库密码 |
| `BOOTSTRAP_ADMIN_PASSWORD` | 首个管理员密码，必须修改默认值 |
| `SKILLHUB_DOWNLOAD_ANON_COOKIE_SECRET` | 至少 32 个字符的随机值 |
| `SKILLHUB_STORAGE_PROVIDER` 及 `SKILLHUB_STORAGE_S3_*` | 文件存储，默认本地磁盘 |

完整说明见仓库中的 [部署架构与运维](https://github.com/kun001/skillhive/blob/main/skillhive-docs/09-deployment.md)。

### 3. 启动

```bash
make validate-release-config
docker compose --env-file .env.release -f compose.release.yml up -d
```

启动后访问 `SKILLHUB_PUBLIC_BASE_URL`。Kubernetes 部署见 [Kubernetes 部署](/guide/kubernetes)。

## 本地开发

```bash
make dev-all     # 启动 PostgreSQL、Redis、MinIO、扫描器，以及宿主机上的后端和前端
make dev-all-down
```

Web 在 `http://localhost:3000`，后端 API 在 `http://localhost:8080`。

## 登录

部署时默认创建首个管理员（`BOOTSTRAP_ADMIN_USERNAME`，默认 `admin`），使用你设置的 `BOOTSTRAP_ADMIN_PASSWORD` 登录。首次登录后请立即修改密码。也可以按部署文档接入 GitHub、GitLab、飞书、钉钉或 OIDC 登录。

## 第一次使用

### 1. 建立团队空间

平台管理员（`SKILL_ADMIN` 或 `SUPER_ADMIN`）在管理后台的「命名空间管理」创建团队空间，空间管理员再添加成员。可以为每位成员单独设置只读或可编辑，以及是否允许下载。详见 [命名空间与团队管理](/guide/namespace)。

### 2. 发布第一个技能

准备一个包含 `SKILL.md` 的目录：

```markdown
---
name: meeting-notes
description: 整理会议记录，提取讨论结论与后续待办。
---

# 会议记录整理

阅读输入的会议记录，按讨论主题整理结论，并列出待办事项、负责人和期限。
```

打包为 zip，在「技能库」点击「发布技能」，选择团队空间和可见范围后上传。技能经过校验、安全扫描和审核后发布。详见 [技能发布与版本管理](/guide/skill-publish)。

### 3. 上传资料到知识库

空间管理员在「知识库」中为团队空间新建知识库，成员即可建文件夹、上传文件。详见 [知识库](/guide/knowledge)。

### 4. 让 AI 工具接入（可选）

在「个人中心 → Token 凭证」创建一个只读 Token，然后配置本地 MCP 服务，AI 工具就能直接查阅你有权限的技能和资料。详见 [API Token 与 MCP](/guide/api-token-mcp)。

## 升级

重新构建并推送新版本镜像，修改 `.env.release` 中的 `SKILLHUB_VERSION`，然后执行：

```bash
docker compose --env-file .env.release -f compose.release.yml pull
docker compose --env-file .env.release -f compose.release.yml up -d
```

数据库迁移由 Flyway 在后端启动时自动执行。升级前请备份数据库和文件存储。
