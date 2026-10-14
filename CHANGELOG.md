# 更新记录 / Changelog

这里记录版本改动；未发布的开发内容另列在“待发布 / Unreleased”章节。实际已上线的版本、日期和历史内容见 [站点更新日志](https://skillhive.team/changelog)，其源文件为 [releases.ts](web/src/features/changelog/releases.ts)。提交或本地验证不代表上线。

## v0.3.0 — 2026-10-14

### 中文

- MCP 新增技能与知识文件上传及归档工具，读取、上传、删除分别授权，可限定团队空间和知识库。
- 写入默认 7 天、最长 30 天有效期，逐次检查成员和业务权限；幂等重试不重复创建，同名内容不覆盖，删除保留历史版本与文件并记录审计。
- Token 页面新增完整权限查看面板，统一 MCP 文案，完善文件夹上传和 Markdown / MDX 配图提示。

### English

- MCP adds skill and knowledge file uploads and archiving, with separate read, upload and delete grants restricted to selected spaces and knowledge bases.
- Write tokens default to seven days and expire within thirty days. Every call checks membership and business permissions; idempotent retries prevent duplicates, existing names cannot be overwritten, and archiving retains history and files with audit records.
- The Tokens page adds a full permissions panel, consistent MCP copy and clearer folder upload and Markdown / MDX image guidance.

See [MCP permissions and writes](skillhive-docs/27-mcp-permissions-and-writes.md) and the [site changelog](https://skillhive.team/changelog).

## v0.2.0 — 2026-10-13

本版本包含 MDX 受控预览、文件夹上传和远程 MCP。线上部署情况以站点更新日志为准。

### 中文

- **支持 MDX 文件**：知识库可上传、下载和管理 `.mdx` 版本；网页支持受控 Markdown、提示、折叠、标签页、步骤、卡片与图注组件，可切换源码；PNG/JPEG/GIF/WebP 配图沿用版本与下载权限。不执行 JavaScript、导入模块或任意 JSX，自定义语法显示为源码占位。
- **文件夹上传保留层级**：选择文件夹后保留顶层目录和子目录，确认目标位置、文件数量及目录预览后才开始上传；浏览器无法上传空目录。
- **同名目录与失败重试**：不自动合并目标位置已有的同名目录；部分失败时保留成功文件和已创建目录，可在当前弹窗重试失败文件。
- **新增远程只读 MCP**：后端提供 `/api/v1/mcp` Streamable HTTP 入口，Agent 可用实例地址和个人只读 Token 连接；本地 stdio 接入继续可用。
- **复制 MCP 配置**：个人中心的 Token 页面提供连接地址和配置复制；创建只读 Token 后可复制包含该 Token 的配置。
- **权限逐次校验**：只接受 `skill:read` 和 / 或 `knowledge:read`，按作用域提供工具；团队成员、可见版本和下载权限在每次读取时检查，平台管理员 Token 也不能绕过团队读取权限。成员、下载权限或 Token 撤销后，后续调用立即生效。

### English

Deployed on 2026-10-13 as v0.2.0.

- **MDX support**: upload, download and manage `.mdx` versions in knowledge bases. Controlled previews support Markdown, callouts, accordions, tabs, steps, cards and figures with a source toggle. Local raster images retain version and download permission checks. JavaScript, imports and arbitrary JSX are never executed; unsupported syntax remains inert source.
- **Folder uploads preserve the hierarchy**: keep the selected top folder and subdirectories. Review the destination, file count and directory preview before confirming the upload. Empty directories cannot be uploaded through the browser.
- **Name collisions and retries**: existing folders with the same name are not merged. Successful files and created folders are retained after partial failures; retry failed files in the same dialog.
- **Remote read-only MCP**: the backend provides `/api/v1/mcp` over Streamable HTTP. Agents connect using the instance URL and a personal read-only token. Local stdio access remains available.
- **Copy MCP configuration**: the Tokens page provides the connection URL and a copy action. After creating a read-only token, copy a configuration containing that token.
- **Permissions checked on every read**: accept only `skill:read` and/or `knowledge:read` and expose tools by scope. Every read checks membership, version visibility and download permission, including platform administrator tokens. Revocation affects subsequent calls.

配置与使用方式见 [MCP 服务说明](mcp/README.md)；上传行为与限制见 [文件夹上传与 MDX](skillhive-docs/folder-upload-mdx.md)；组件、配图与安全边界见 [MDX 受控预览](skillhive-docs/mdx-preview.md)。
