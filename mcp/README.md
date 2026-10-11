# SkillHive MCP 服务

本地 stdio MCP 服务：让 Claude Code、Cursor 等 MCP 客户端用你自己的 API Token 只读访问 SkillHive 的技能库和知识库。设计与权限模型见 [skillhub-docs/26-mcp-server.md](../skillhub-docs/26-mcp-server.md)。

- 只读：不能发布、修改或删除任何内容。
- 权限与网页一致：只能看到你所在团队的内容，读取知识库文件需要该团队的下载权限。
- 文件原样返回：文本文件返回文本，PDF、DOCX 等返回原文件，不做内容解析。
- 可追溯：通过它读取的知识库文件和技能文件都会写入 SkillHive 审计日志。

## 1. 创建只读 Token

在 SkillHive 网页的「个人中心 → Token 凭证 → 创建新 Token」中设置访问范围：

- 技能库：选「只读」（不需要技能时选「不授予」）。
- 知识库（只读）：需要读知识库时勾选。

**带写入权限的 Token 无法启动 MCP 服务**，所以技能库不要选「读写」。Token 明文只显示一次，请妥善保存；不用时在网页上删除即可吊销。

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
| `SKILLHIVE_TOKEN` | 是 | 第 1 步创建的只读 Token |
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

## 常见问题

- **启动即退出，提示 write scopes**：Token 带有 `skill:publish`、`skill:delete` 或 `token:manage`，换一个只读 Token。
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
