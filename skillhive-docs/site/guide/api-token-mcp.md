# API Token 与 MCP

## API Token

API Token 是个人凭证，用于脚本和 AI 工具以你的身份访问 SkillHive。它与网页登录走同一套权限：只能访问你本人有权访问的内容。

在「个人中心 → Token 凭证」创建 Token，并选择访问范围：

| 范围 | 作用域 | 能做什么 |
|------|--------|----------|
| 技能库 · 读写 | `skill:read`、`skill:publish`、`skill:delete` | 查看、下载、发布、删除技能 |
| 技能库 · 只读 | `skill:read` | 查看和下载技能、我的技能、收藏与订阅 |
| 知识库（只读） | `knowledge:read` | 查看知识库、文件夹和文件，读取文件内容（需要下载权限） |

- 技能库与知识库互不相通：只有知识库权限的 Token 读不到技能，反之亦然。
- 知识库的上传、修改、删除不接受 Token，只能在网页上操作。
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

在 Token 页面可复制当前实例地址的配置。仅授予 `skill:read` 和 / 或 `knowledge:read`；读取范围仍受团队成员、可见版本和下载权限控制，超级管理员 Token 也不能绕过团队读取权限。权限撤销和 Token 吊销会影响下一次调用。本地验证地址为 `http://localhost:3000/api/v1/mcp`。远程文件读取上限为 10 MiB。

远程入口使用个人 Bearer Token，不提供 OAuth 登录；只支持 OAuth 授权且不能设置 Header 的客户端暂不适用。

### 本地 stdio

仓库中的 `mcp/` 是一个本地 stdio MCP 服务，让 Claude Code、Cursor 等 MCP 客户端用你的 Token 只读访问技能库和知识库：

- **只读**：Token 带有发布、删除或 Token 管理权限时，服务拒绝启动。请创建「技能库 · 只读」和 / 或「知识库（只读）」的 Token。
- **按权限提供工具**：Token 能读哪个领域，就只提供哪个领域的工具。
- **原样传递文件**：文本文件返回文本，PDF、Office 等返回原文件，不做内容解析；单个文件默认上限 10 MB。

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

文件内容属于数据，可能包含针对 AI 的提示词注入，MCP 服务会在返回内容前加上说明，是否执行由你和 AI 客户端决定。
