# SkillHive 架构与本轮改造

## 产品边界

平台是单实例团队知识管理系统，团队空间 `namespace` 是第一层授权边界。资源分为 `SKILL` 和 `KNOWLEDGE_DOCUMENT` 两类。技能继续遵循 `SKILL.md` 包协议；知识库不依赖技能包格式、CLI 安装语义或套件组合语义。

知识库定位为团队文件中心：团队空间成员在知识库中按多层文件夹上传、预览、下载文件，并保留版本历史；上传即发布，暂不走审核。搜索只匹配标题、描述和文件名，**不做内容解析、分块、向量检索或 RAG 问答**。实现与权限细节见 [知识库文件中心实施计划](knowledge-file-hub-plan.md)。

技能库分为公共技能库和团队技能库。公共技能库承载 Global 空间中已发布、未隐藏的 `PUBLIC` 技能，所有人无需登录即可浏览、查看已发布版本并下载。团队技能库只展示所属团队的共享技能，登录后按团队成员权限访问；团队中的历史 `PUBLIC` 标记也不绕过团队授权。知识库仍是团队文件中心，不提供匿名访问开关。

Global 是平台共用空间，不为每个用户创建副本。普通成员的空间列表和知识库列表不展示 Global，直接访问其管理空间或知识资料也会拒绝。平台管理员（`SUPER_ADMIN`、`SKILL_ADMIN`、`USER_ADMIN`）可查看 Global；公共技能读取与 Global 管理入口分别授权，隐藏管理入口不影响公共技能使用。

仓库根目录的 `mcp/` 是本地 stdio MCP 服务，让 AI 客户端用用户自己的只读 API Token 读取技能库和知识库。它只调用现有 `/api/v1` 接口、原样传递文件，不自建权限；设计与权限模型见 [MCP 服务设计](26-mcp-server.md)。

管理员在团队成员管理中为普通成员设置“只读 / 可编辑”，并单独设置“允许下载”。只读可以浏览、搜索与预览，可编辑在原有归属规则内上传、维护文件和技能；下载开关控制原文件、技能包及其历史版本下载。团队管理员和所有者保留完整权限。后端同时保护 Web 与 API v1 接口，每次请求读取当前成员权限；撤销下载不影响合法的预览读取。

V70 增加成员编辑和下载权限，原有成员默认保留已有能力。V69、V70 迁移保留。V71 将 Global 中已发布、正常展示的 `NAMESPACE_ONLY` 技能恢复为 `PUBLIC`，并同步搜索索引；团队技能、私有技能和知识资料不变。技能查询使用 `library=public|team`，范围过滤先于分页与总数计算；切换技能库时清空分页并保留搜索、标签和排序，缓存占位数据不跨技能库复用。管理员发布到 Global 时可选择公开，团队发布仍使用团队可见或私有。

## 已冻结约束

- 用户身份主键全链路使用字符串（`userId`），覆盖认证主体、API 出入参、权限判定、审计和所有 owner/creator/reviewer/actor 字段，不使用整型用户标识。外部 SSO / OAuth / OIDC 的 UID 通常是字符串，不先压成本地自增整数再传播。
- 技能坐标为 `@{namespace_slug}/{skill_slug}`；`@global` 是平台公共空间，团队空间是协作与治理边界，不是租户。
- namespace 与 skill slug 规则：`[a-z0-9]([a-z0-9-]*[a-z0-9])?`，不得包含连续的 `--`（早期坐标映射遗留的约束，继续保留以免历史坐标产生歧义）。

## 领域分工

| 共用治理底座 | SKILL | 知识库 |
| --- | --- | --- |
| 用户、认证、namespace、成员角色、审核任务、审计、通知、对象存储 | `SKILL.md` 解析、包校验、版本、扫描、发布、下载 | `knowledge_base`、`knowledge_folder`、`knowledge_document`、`knowledge_document_version`（`knowledge_attachment` 暂未使用） |

知识版本的发布指针 `knowledge_document.published_version_id` 只允许指向同文档的 `PUBLISHED` 版本。草稿与待审版本不得进入普通搜索或下载；权限过滤必须先于结果返回。上传校验由 `KnowledgeFilePolicy` 负责（扩展名白名单、100 MB 上限、服务端判定内容类型），不复用技能包校验器；HTML、SVG 等可执行内容不允许上传，内联预览只对 PDF、位图和纯文本生效。

`review_task` 已有 `subject_type`、`subject_id`、`subject_version_id`。下一阶段将补充知识审核主体和专用状态变更服务，复用队列及审计，不让既有 `ReviewService` 直接处理知识文档。审核权限要显式决定是否允许团队管理员自审；建议默认禁止，管理员例外单独审计。

## 命名空间删除

团队命名空间只有 OWNER 可以永久删除；Global 与个人空间仍不可删除。空间中存在技能、治理记录或未删除的知识文件时，列表不提供删除操作，直接调用删除接口也会返回业务错误。隐藏的文件和已归档知识库中的未删除文件仍算依赖。

知识文件的普通删除仍为归档。OWNER 确认删除已清空文件的命名空间时，系统在同一事务中清理知识库、文件夹、已归档文件、全部版本和附件记录，再删除空间及成员关系；并发上传不能进入正在删除的空间。对象存储清理写入现有持久化重试队列，只在事务提交后执行；数据库回滚不会删除存储文件。

运行 `scripts/namespace-knowledge-delete-smoke-test.py <base-url>` 可验证实际接口流程。它使用 `SMOKE_ADMIN_USERNAME` / `SMOKE_ADMIN_PASSWORD` 登录，创建唯一命名的测试空间，并检查未删除文件阻止删除、文件归档后可删除空间及其他空间的资料不受影响。只在授权的测试环境中运行。

## 已删除的上游能力

技能套件（含 Suite Bundle）、CLI 接口（`/api/cli/v1`）、ClawHub 兼容层（`/api/v1/search`、`/api/v1/resolve`、`/api/v1/download` 等）、设备码登录（`/api/v1/auth/device`）和 `/.well-known/clawhub.json` 发现端点的后端、前端代码与测试均已删除，`skillhive-legacy` profile 不再存在。API Token 保留为平台级机器接入能力：持有 `skill:read` 可访问 `/api/v1` 与 `/api/web` 下的 Skill 读取接口，持有 `knowledge:read` 可只读访问 `/api/v1` 下的知识库接口，两个领域互相隔离；知识库写操作和 `/api/web/knowledge/**` 不接受 Token。

历史 Flyway 迁移和数据表保留，**不通过迁移清除套件数据**。`review_task` 中可能残留 `SUITE_VERSION` 类型的旧审核记录：它们不会出现在审核列表、审核进度或详情中，按“不存在”处理。`ReviewSubjectType.SUITE_VERSION` 枚举值仅为读取这些旧行而保留。内置技能清单中已移除 CLI 助手 `skillhub-cli`。

前端类型统一由 `make generate-api` 从当前后端生成到 `web/src/api/generated/schema.d.ts`。

## 运行隔离

副本与原项目共享默认主机端口，但 Compose 项目名分别为 `skillhive` 和原项目名。新项目没有复制 `.dev` 进程状态、`node_modules` 或 Maven 构建产物。不能把原项目的 `/opt/skillhub-deps/start.sh` 当作本项目启动脚本：它固定指向旧源码路径。需要并行运行时，应先为 SkillHive 分配独立端口和后端配置。

## 文档导航

| 主题 | 文档 |
| --- | --- |
| 系统架构、模块边界 | [01-system-architecture.md](01-system-architecture.md) |
| 领域模型与数据表 | [02-domain-model.md](02-domain-model.md) |
| 认证、授权与 API Token | [03-authentication-design.md](03-authentication-design.md) |
| 技能搜索 | [04-search-architecture.md](04-search-architecture.md) |
| 技能发布、下载等业务流 | [05-business-flows.md](05-business-flows.md)、[14-skill-lifecycle.md](14-skill-lifecycle.md) |
| API 约定 | [06-api-design.md](06-api-design.md) |
| 技能包协议 | [07-skill-protocol.md](07-skill-protocol.md) |
| 前端架构 | [08-frontend-architecture.md](08-frontend-architecture.md) |
| 部署与运维 | [09-deployment.md](09-deployment.md) |
| 知识库 | [knowledge-file-hub-plan.md](knowledge-file-hub-plan.md) |
| MCP 服务 | [26-mcp-server.md](26-mcp-server.md) |
| 隐私、内容安全 | [PRIVACY_AND_DATA_GOVERNANCE.md](PRIVACY_AND_DATA_GOVERNANCE.md)、[CONTENT_SAFETY.md](CONTENT_SAFETY.md) |
| 用户文档站点（VitePress） | [site/](site/) |
