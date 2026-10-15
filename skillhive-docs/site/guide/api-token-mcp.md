# API Token 与 MCP

## MCP 专用授权

新建 Token 默认用于 MCP，分别授予技能/知识库的读取、新增上传、归档删除，并选择允许访问的命名空间。知识库可再限定到指定库。「技能：读取」「知识库：读取」即对应的只读权限，不另设只读模式。

上传工具 `upload_skill`、`upload_document` 只新建，技能继续扫描和审核，知识文件上传即发布。文件更新使用独立的 `knowledge:update`，提供 `update_document` 和 `upload_document_version`；知识库和目录管理使用 `knowledge:manage`。旧 Token 不自动获得新增权限。文件和知识库删除执行归档，目录仅允许删除空目录。

新版本上传前先读取 `get_document`，将 `detail.document.currentVersion` 作为 `expectedVersion`，保留历史版本，版本变化时拒绝更新。知识库新建、修改和归档要求团队管理员；新建还要求不限定已有知识库 ID。归档知识库前须先归档全部文件。元数据更新为完整替换：省略描述会清空，省略目录位置会移至根目录。

调用直接按 Token 与业务权限校验后执行，不要求回网站逐次确认。客户端决定工具确认策略。写入需要 `idempotencyKey`，文件以 base64 提交，最多 10 MiB；同键不同内容拒绝。写入 Token 默认 7 天，最长 30 天。

旧只读 Token 继续可用；旧的普通读写 Token 不自动获得 MCP 写入能力。下文只读配置适用于旧 Token；新 MCP Token 同样可复制连接配置。

完整规则见[权限与写入实施方案](https://github.com/kun001/skillhive/blob/main/skillhive-docs/27-mcp-permissions-and-writes.md)。

## API Token

API Token 是个人凭证，用于脚本和 AI 工具以你的身份访问 SkillHive。它与网页登录走同一套权限：只能访问你本人有权访问的内容。

网页「个人中心 → Token 凭证」新建统一使用上面的 MCP 授权。旧版普通 API Token 的访问范围如下：

| 范围 | 作用域 | 能做什么 |
|------|--------|----------|
| 技能库 · 读写 | `skill:read`、`skill:publish`、`skill:delete` | 查看、下载、发布、删除技能 |
| 技能库 · 只读 | `skill:read` | 查看和下载技能、我的技能、收藏与订阅 |
| 知识库（只读） | `knowledge:read` | 查看知识库、文件夹和文件，读取文件内容（需要下载权限） |

- 技能库与知识库互不相通：只有知识库权限的 Token 读不到技能，反之亦然。
- 旧知识库 API 仅对 Token 开放读取。MCP 专用 Token 通过受限入口执行明确授权的新增、修改、新版本和归档操作。
- 通过 Token 访问知识库时只按团队成员身份判断，平台管理员的 Token 也看不到自己不在的团队。
- Token 明文只在创建时显示一次。泄露或不再使用时，在 Token 列表中删除即可立即吊销。
- 通过 Token 读取知识库文件、技能文件或下载技能包都会写入审计日志，并记录是哪个 Token。

调用方式：

```bash
curl -H "Authorization: Bearer $SKILLHIVE_TOKEN" https://skillhive.example.com/api/v1/knowledge/bases
```

## MCP 服务

### 远程连接

后端提供 `/api/v1/mcp` Streamable HTTP 入口，随现有后端版本发布。部署包含该功能的版本后，支持自定义 Header 的 Agent 无需安装本地 MCP 程序：

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

在 Token 页面可复制当前实例地址的配置。旧 Token 仅接受 `skill:read` 和 / 或 `knowledge:read`；新 MCP Token 按上文独立授权。读取仍受团队成员、可见版本和下载权限控制，超级管理员 Token 也不能绕过团队边界。权限撤销和 Token 吊销会影响下一次调用。本地验证地址为 `http://localhost:3000/api/v1/mcp`。单文件读取与新增上传上限为 10 MiB。

远程入口使用个人 Bearer Token，不提供 OAuth 登录；只支持 OAuth 授权且不能设置 Header 的客户端暂不适用。

### 本地 stdio

仓库中的 `mcp/` 是一个本地 stdio MCP 服务，让 Claude Code、Cursor 等 MCP 客户端用你的 Token 只读访问技能库和知识库：

- **按授权操作**：支持旧只读 Token 和新 MCP 专用 Token；普通发布、删除或 Token 管理凭证仍被拒绝。
- **按权限提供工具**：Token 能读哪个领域，就只提供哪个领域的工具。
- **优先返回 Markdown**：Word、PowerPoint、Excel、PDF 和文本文件返回服务端提取的 Markdown（`format=original` 取原文件），其他文件原样返回；单个文件默认上限 10 MB。
- **团队说明书**：`get_team_guide` 返回团队空间的说明书，建议 Agent 开始工作前先读。

### 构建

需要 Node.js 20+ 与 pnpm：

```bash
make build-mcp
```

### 配置客户端

| 环境变量 | 必填 | 说明 |
|----------|------|------|
| `SKILLHIVE_URL` | 是 | SkillHive 地址，必须是 `https://`；本机调试可用 `http://localhost:8080` |
| `SKILLHIVE_TOKEN` | 是 | 只读 API Token |
| `SKILLHIVE_MCP_MAX_FILE_BYTES` | 否 | 单文件读取上限，默认 10485760 |

Claude Code：

```bash
claude mcp add skillhive \
  -e SKILLHIVE_URL=https://skillhive.example.com \
  -e SKILLHIVE_TOKEN=sk_xxx \
  -- node /path/to/skillhive/mcp/dist/index.js
```

其他客户端的 JSON 配置：

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

### 工具

| 工具 | 作用 | 需要 |
|------|------|------|
| `whoami` | 当前用户、Token 作用域、所属团队 | — |
| `search_skills` | 搜索技能 | 技能库只读 |
| `get_skill` / `list_skill_versions` | 技能详情 / 版本 | 技能库只读 |
| `list_skill_files` / `read_skill_file` | 技能包文件列表 / 读取文件 | 技能库只读 |
| `list_my_skills` | 我发布的、收藏的、订阅的技能 | 技能库只读 |
| `list_knowledge_bases` / `browse_knowledge_base` | 知识库列表 / 文件夹与文件 | 知识库只读 |
| `search_knowledge` | 按标题和描述搜索文件 | 知识库只读 |
| `get_document` / `read_document` | 文件详情与版本 / 读取原文件 | 知识库只读 |
| `upload_skill` / `delete_skill` | 新增技能 ZIP / 归档技能 | 技能新增上传 / 归档删除 |
| `upload_document` / `delete_document` | 新增文件 / 归档文件 | 知识库新增上传 / 归档删除 |

文件内容属于数据，可能包含针对 AI 的提示词注入，MCP 服务会在返回内容前加上说明，是否执行由你和 AI 客户端决定。
