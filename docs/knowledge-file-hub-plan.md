# 知识库：团队文件中心实施计划

分支：`claude/knowledge-file-hub`

## 目标与边界

知识库是团队的文件聚合中心：组员在团队空间下的知识库中上传、查看（在线预览）和下载文件，并保留每个文件的版本历史。

明确不做：RAG 问答、向量检索、文档分块解析、AI 摘要、Wiki/知识图谱。搜索只匹配标题、描述和文件名，不检索文件内容。

## 已确认的产品决策

| 问题 | 决策 |
| --- | --- |
| 上传后是否审核 | 第一版直接发布：上传即生成 `PUBLISHED` 版本。`PENDING_REVIEW` 状态保留给以后按需开启的审核流。 |
| 文件夹层级 | 支持多层文件夹（语雀式目录树），移动时校验不能形成环。 |
| 在线预览格式 | PDF、图片（png/jpg/jpeg/gif/webp）、Markdown、TXT；Office 等其他格式只提供下载。 |
| 知识库与团队空间 | 一个团队空间可以有多个知识库，只能建在 `TEAM` 类型空间下。 |

## 权限

| 操作 | 允许的人 |
| --- | --- |
| 查看知识库、文件列表、预览、下载 | 团队空间成员（任意角色）、平台超级管理员 |
| 上传文件、上传新版本、新建文件夹 | 团队空间成员（空间和知识库都必须为 ACTIVE） |
| 编辑/移动/删除文件、恢复历史版本 | 文件上传者本人、空间 OWNER/ADMIN、平台超级管理员 |
| 重命名/移动/删除文件夹 | 文件夹创建者、空间 OWNER/ADMIN、平台超级管理员；只能删除空文件夹 |
| 创建/编辑知识库 | 空间 OWNER/ADMIN、平台超级管理员 |

知识内容不开放给匿名用户：接口不在 `RouteSecurityPolicyRegistry` 的 permitAll 列表中，走默认的登录校验。上传、新版本、编辑、删除、恢复、文件夹和知识库变更都写入审计日志。

## 数据库（新增 `V67__knowledge_file_hub.sql`，不修改 V66）

- 新表 `knowledge_folder`：`knowledge_base_id`、`parent_id`（自引用）、`name`、`created_by`、时间戳。
- `knowledge_document` 新增：`folder_id`（外键，文件夹删除时置空）、`description`、`file_extension`（当前版本的扩展名，用于类型筛选）。
- `knowledge_document_version` 新增：`change_note`。
- `knowledge_attachment` 暂不使用：一个文档对应一个文件，文件对象在版本表的 `content_object_key`。

对象存储键：`knowledge/{baseId}/{documentId}/v{n}/{uuid}`，不包含原始文件名。

## 后端 API（`/api/web/knowledge`，同时映射 `/api/v1/knowledge`）

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/bases` | 我可见的知识库列表（按团队空间分组所需字段、文件数、更新时间、是否可管理） |
| POST | `/bases` | 新建知识库 |
| GET / PATCH | `/bases/{namespace}/{base}` | 知识库详情 / 修改名称和描述 |
| GET / POST | `/bases/{namespace}/{base}/folders` | 文件夹平铺列表（含文件数）/ 新建文件夹 |
| PATCH / DELETE | `/bases/{namespace}/{base}/folders/{folderId}` | 重命名、移动 / 删除空文件夹 |
| GET | `/bases/{namespace}/{base}/documents` | 分页文件列表：`folderId`、`q`、`extensions`、`uploader`、`updatedFrom`、`updatedTo`、`sort` |
| POST | `/bases/{namespace}/{base}/documents` | 上传文件（multipart：`file`、`folderId`、`title`、`description`） |
| GET / PATCH / DELETE | `/documents/{documentId}` | 文件详情 / 修改标题、描述、所在文件夹 / 删除（归档，保留对象） |
| GET / POST | `/documents/{documentId}/versions` | 版本历史 / 上传新版本（multipart：`file`、`changeNote`） |
| POST | `/documents/{documentId}/versions/{version}/restore` | 以历史版本内容生成一个新版本并设为当前版本 |
| GET | `/documents/{documentId}/content` | 下载当前或指定版本（`version`）；`disposition=inline` 仅对安全预览类型生效 |

安全要点：
- 上传校验扩展名白名单与大小上限（默认 100MB，与 multipart 限制一致），拒绝空文件和非法文件名。
- `inline` 预览只对 PDF、位图和纯文本生效；Markdown/TXT 以 `text/plain; charset=UTF-8` 返回；统一加 `X-Content-Type-Options: nosniff`。HTML、SVG 等可执行内容一律按附件下载。
- `Content-Disposition` 使用 RFC 5987 编码，支持中文文件名。

分层：`KnowledgeController` 只做传输；`KnowledgeAppService` 编排存储、DTO 和审计；领域层 `domain/knowledge` 放实体、仓储接口、权限规则 `KnowledgeAccessPolicy` 和文件规则 `KnowledgeFilePolicy`。

## 前端

路由：

- `/knowledge`：按团队空间分组的知识库卡片，空间管理员可以新建知识库。
- `/knowledge/$namespace/$base`：左侧目录树（全部文件 + 多层文件夹）；右侧搜索、类型、上传人、时间筛选，列表/卡片视图切换，拖拽上传区，行内下载和更多菜单（上传新版本、编辑、移动、删除）。
- `/knowledge/$namespace/$base/$documentId`：左侧预览区（PDF/图片/Markdown/TXT），其他格式显示文件图标和下载按钮；右侧信息面板；版本历史抽屉（下载任意版本、恢复为当前版本）。

组件：上传弹窗支持多文件、选择目标文件夹、单文件进度（XHR 上传）与失败重试。视觉沿用 SkillHive 现有的品牌样式，所有文案走 i18n（中/英）。

## 实施步骤

1. [x] 新增 V67 迁移，补齐领域实体和仓储（domain + infra）。
2. [x] 领域规则：`KnowledgeAccessPolicy`、`KnowledgeFilePolicy`、`KnowledgeFolderTree` 及单元测试。
3. [x] 应用服务 `KnowledgeAppService`：知识库、文件夹、文件、版本、下载，含审计和单元测试。
4. [x] `KnowledgeController` 与 DTO。
5. [x] 启动后端验证 V67 迁移与表结构校验，生成前端类型（见下方“API 类型生成”）。
6. [x] 前端数据层：`api/knowledge-types.ts`、`knowledgeApi`（XHR 上传进度）、`features/knowledge` hooks。
7. [x] 前端页面：知识库列表、知识库工作区、文件详情与版本抽屉、上传弹窗。
8. [x] i18n 文案（中/英，俄语回退英文）、路由注册、单元测试。
9. [x] 验证：后端测试、前端 typecheck / lint / 全量 Vitest，以及浏览器端到端流程（新建知识库、拖拽批量上传、文件夹筛选、类型筛选、卡片视图、PDF/图片/Markdown/TXT 预览、Office 下载兜底、上传新版本、版本历史与恢复、编辑、非成员 404、匿名 401）。

## API 类型生成

`web/src/api/generated/schema.d.ts` 仍包含后端已不再提供的上游套件/CLI 契约（即使启用 `skillhive-legacy` profile），整体重新生成会让存量套件页面编译失败。因此知识库接口的类型单独生成到 `web/src/api/generated/knowledge.d.ts`：

```bash
# 后端运行在 :8080 时
cd web && pnpm run generate-api:knowledge
```

脚本 `web/scripts/generate-knowledge-api.mjs` 只保留 `/api/web/knowledge` 路径及其引用的 schema，再交给 openapi-typescript 生成。两份生成文件都不要手工修改。待存量套件前端清理后，可恢复为单一的 `make generate-api`。

## 实现中的注意点

- PDF 预览通过 `blob:` URL 放在 iframe 中（后端对所有响应设置了 `X-Frame-Options: DENY`），因此 `web/index.html` 的 CSP 增加了 `frame-src 'self' blob:`。
- 打开对话框的下拉菜单使用 `modal={false}`，且对话框保持挂载、只切换 `open`，避免 Radix 在 `<body>` 上残留 `pointer-events: none`。
- 上传时先单独读一遍文件计算 SHA-256，再写入对象存储，摘要不依赖存储实现是否读完整个流。

## 以后再做

- 可选的审核流（复用 `review_task` 的 `KNOWLEDGE_DOCUMENT_VERSION` 主体）。
- Office 文档在线预览（需要转换服务）。
- 知识库归档与删除、回收站、批量操作、下载统计。
