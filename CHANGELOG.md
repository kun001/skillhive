# 更新记录 / Changelog

这里记录尚未发布的开发改动。实际已上线的版本、日期和历史内容见 [站点更新日志](https://skillhive.team/changelog)，其源文件为 [releases.ts](web/src/features/changelog/releases.ts)。提交或本地验证不代表上线。

## 待发布 / Unreleased

记录日期：2026-10-13。以下功能已在开发分支完成实现和本地验证，尚未发布到线上；发布时再确定正式版本号与上线日期。

### 中文

- **支持 MDX 文件**：知识库可上传、下载和管理 `.mdx` 版本；网页安全展示源文本，不执行 JSX、JavaScript 或导入模块。
- **文件夹上传保留层级**：选择文件夹后保留顶层目录和子目录，确认目标位置、文件数量及目录预览后才开始上传；浏览器无法上传空目录。
- **同名目录与失败重试**：不自动合并目标位置已有的同名目录；部分失败时保留成功文件和已创建目录，可在当前弹窗重试失败文件。
- **新增远程只读 MCP**：后端提供 `/api/v1/mcp` Streamable HTTP 入口，Agent 可用实例地址和个人只读 Token 连接；本地 stdio 接入继续可用。
- **复制 MCP 配置**：个人中心的 Token 页面提供连接地址和配置复制；创建只读 Token 后可复制包含该 Token 的配置。
- **权限逐次校验**：只接受 `skill:read` 和 / 或 `knowledge:read`，按作用域提供工具；团队成员、可见版本和下载权限在每次读取时检查，平台管理员 Token 也不能绕过团队读取权限。成员、下载权限或 Token 撤销后，后续调用立即生效。

### English

Recorded on 2026-10-13. Implemented and verified locally; these changes are awaiting deployment. The release version and deployment date will be set when published.

- **MDX support**: upload, download and manage `.mdx` versions in knowledge bases. Browser previews display source text without executing JSX, JavaScript or imports.
- **Folder uploads preserve the hierarchy**: keep the selected top folder and subdirectories. Review the destination, file count and directory preview before confirming the upload. Empty directories cannot be uploaded through the browser.
- **Name collisions and retries**: existing folders with the same name are not merged. Successful files and created folders are retained after partial failures; retry failed files in the same dialog.
- **Remote read-only MCP**: the backend provides `/api/v1/mcp` over Streamable HTTP. Agents connect using the instance URL and a personal read-only token. Local stdio access remains available.
- **Copy MCP configuration**: the Tokens page provides the connection URL and a copy action. After creating a read-only token, copy a configuration containing that token.
- **Permissions checked on every read**: accept only `skill:read` and/or `knowledge:read` and expose tools by scope. Every read checks membership, version visibility and download permission, including platform administrator tokens. Revocation affects subsequent calls.

配置与使用方式见 [MCP 服务说明](mcp/README.md)；上传行为与限制见 [文件夹上传与 MDX](skillhive-docs/folder-upload-mdx.md)。
