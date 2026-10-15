# SkillHive MCP 服务

本地 stdio MCP 服务：让 AI 客户端用你自己的 Token 读取技能和知识文件，或在明确授权范围内新增上传、归档删除。当前权限模型见 [MCP 权限与写入方案](../skillhive-docs/27-mcp-permissions-and-writes.md)。

新建 MCP Token 时分别选择读取、新增上传、归档删除与目标命名空间，可进一步限定知识库。没有修改、覆盖、追加版本、移动或永久清除工具，不需要回网站逐次确认。写入工具要求 `idempotencyKey`，文件为 base64，最多 10 MiB；写入 Token 默认 7 天、最长 30 天。

旧只读 Token 的配置继续可用。以下只读示例适用于旧 Token；新 MCP Token 也可使用相同连接方式。

## 远程连接（无需本地安装）

后端包含无状态 Streamable HTTP 入口 `/api/v1/mcp`，随现有 server 镜像发布，无需额外 MCP 容器或共享服务 Token。以下配置在包含此功能的版本部署后生效：

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

本地验证可将 URL 改为 `http://localhost:3000/api/v1/mcp`。个人中心的 Token 页面可复制当前实例的配置；创建只读 Token 后可复制包含该 Token 的配置。配置中含凭据，请只保存到客户端私有配置，不提交到仓库。

服务每次请求验证个人 Token，按作用域显示工具。工具参数不能指定用户、Token 或后端地址；后端地址固定为本实例的 loopback。读取通过原有 GET 接口，写入通过受限 MCP 接口，均再次检查操作权限、资源范围、团队成员身份、生命周期、文件所有权与下载权限。Token 吊销、成员移除或下载权限撤销会影响后续调用。超级管理员 Token 的知识、团队技能访问同样受成员边界约束。

协议支持 `2025-03-26`、`2025-06-18`、`2025-11-25`，以 JSON 返回 POST 请求，通知返回 202；不提供长连接 SSE 或服务器会话，根入口 GET/DELETE 返回 405。单个文件上限 10 MiB，超限请通过网页下载。旧 Token 只接受 `skill:read`、`knowledge:read`；MCP 专用 Token 支持六项操作权限与内部 `mcp:access` 标记，拒绝普通发布或 Token 管理作用域。

代理已通过现有 `/api/` 路由转发该入口。生产发布仍按项目规定的 GitHub Actions 镜像和版本部署流程执行；此功能不需要修改生产容器或服务器配置。带 `Origin` 的请求仅允许 `SKILLHUB_PUBLIC_BASE_URL` 对应的源（本地默认 `http://localhost:3000`）。无 Origin 的原生 Agent 客户端仍需 Bearer 认证。

- 默认只读；新 MCP Token 可独立授予新增上传与归档删除。
- 权限与网页一致：只能看到你所在团队的内容，读取知识库文件需要该团队的下载权限。
- 文件优先返回 Markdown：Word、PowerPoint、Excel、PDF 和文本文件返回服务端提取的 Markdown（`format=original` 取原文件），其他文件原样返回。
- `get_team_guide` 返回团队说明书。
- 可追溯：通过它读取的知识库文件和技能文件都会写入 SkillHive 审计日志。

## 1. 创建 Token

在 SkillHive 网页的「个人中心 → Token 凭证 → 创建新 Token」中设置访问范围：

- 新建 Token 默认用于 MCP，分别选择需要的读取、新增上传和归档删除；读取选项即对应的只读权限。
- 显式选择允许访问的命名空间；需要时进一步限定知识库。
- 只读为默认选项。开放写入后有效期默认 7 天，最长 30 天。

旧只读 Token 仍可连接。普通 API 的「技能库读写」Token 不能用于 MCP。Token 明文只显示一次，请妥善保存；不用时在网页上删除即可吊销。

## 2. 构建

需要 Node.js 20+ 和 pnpm：

```bash
make build-mcp        # 等价于 cd mcp && pnpm install --frozen-lockfile && pnpm run build
```

产物在 `mcp/dist/index.js`。

## 3. 配置 MCP 客户端

| 环境变量 | 必填 | 说明 |
| --- | --- | --- |
| `SKILLHIVE_URL` | 是 | SkillHive 地址，必须是 `https://`；本机调试可用 `http://localhost:8080` |
| `SKILLHIVE_TOKEN` | 是 | MCP 专用 Token 或旧只读 Token |
| `SKILLHIVE_MCP_MAX_FILE_BYTES` | 否 | 单个文件读取上限，默认 10485760（10 MB），超过时提示到网页下载 |

Claude Code：

```bash
claude mcp add skillhive \
  -e SKILLHIVE_URL=https://skillhive.example.com \
  -e SKILLHIVE_TOKEN=sk_xxx \
  -- node /path/to/skillhive/mcp/dist/index.js
```

其他客户端（Cursor、Claude Desktop 等）的 JSON 配置：

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

Token 写在本机配置文件里，注意不要提交到代码仓库或分享给他人。

## 工具

Token 有哪些作用域，就只注册对应领域的工具。

| 工具 | 作用 | 需要 |
| --- | --- | --- |
| `whoami` | 当前用户、Token 作用域、所属团队 | — |
| `search_skills` | 搜索技能 | `skill:read` |
| `get_skill` | 技能详情 | `skill:read` |
| `list_skill_versions` | 版本列表 | `skill:read` |
| `list_skill_files` | 技能包文件列表 | `skill:read` |
| `read_skill_file` | 读取技能包内文件（写审计） | `skill:read` |
| `list_my_skills` | 我发布的 / 收藏的 / 订阅的技能 | `skill:read` |
| `list_knowledge_bases` | 我能看到的知识库 | `knowledge:read` |
| `browse_knowledge_base` | 文件夹树与文件列表 | `knowledge:read` |
| `search_knowledge` | 按标题和描述搜索文件 | `knowledge:read` |
| `get_document` | 文件详情与版本历史 | `knowledge:read` |
| `read_document` | 读取原文件（需下载权限，写审计） | `knowledge:read` |
| `upload_skill` | 新增技能 ZIP，不覆盖或追加版本 | `skill:upload` |
| `delete_skill` | 归档技能，保留文件 | `skill:delete` |
| `upload_document` | 新增知识文件，不覆盖同目录同名文件 | `knowledge:upload` |
| `delete_document` | 归档知识文件，保留历史版本 | `knowledge:delete` |

## 常见问题

- **启动即退出，提示 write scopes**：普通 API Token 的发布/删除权限不能用于 MCP。改用旧只读 Token，或创建资源范围明确的 MCP 专用 Token。
- **提示 Token invalid, expired or revoked**：Token 已过期、被删除，或账号被禁用。
- **读取文件返回 403**：你不在该团队，或团队管理员没有给你下载权限。
- **管理员看不到其他团队的知识库**：这是有意的设计，Token 只按团队成员身份访问知识库；需要全局查看时请使用网页。

## 开发

```bash
cd mcp
pnpm install
pnpm run typecheck
pnpm test
pnpm run build
```
