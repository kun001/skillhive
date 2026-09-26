# SkillHive 架构与本轮改造

## 产品边界

平台是单实例团队知识管理系统，团队空间 `namespace` 是第一层授权边界。资源分为 `SKILL` 和 `KNOWLEDGE_DOCUMENT` 两类。技能继续遵循 `SKILL.md` 包协议；知识库不依赖技能包格式、CLI 安装语义或套件组合语义。

知识库定位为团队文件中心：团队空间成员在知识库中按多层文件夹上传、预览、下载文件，并保留版本历史；上传即发布，暂不走审核。搜索只匹配标题、描述和文件名，**不做内容解析、分块、向量检索或 RAG 问答**。实现与权限细节见 [知识库文件中心实施计划](knowledge-file-hub-plan.md)。

## 领域分工

| 共用治理底座 | SKILL | 知识库 |
| --- | --- | --- |
| 用户、认证、namespace、成员角色、审核任务、审计、通知、对象存储 | `SKILL.md` 解析、包校验、版本、扫描、发布、下载 | `knowledge_base`、`knowledge_folder`、`knowledge_document`、`knowledge_document_version`（`knowledge_attachment` 暂未使用） |

知识版本的发布指针 `knowledge_document.published_version_id` 只允许指向同文档的 `PUBLISHED` 版本。草稿与待审版本不得进入普通搜索或下载；权限过滤必须先于结果返回。上传校验由 `KnowledgeFilePolicy` 负责（扩展名白名单、100 MB 上限、服务端判定内容类型），不复用技能包校验器；HTML、SVG 等可执行内容不允许上传，内联预览只对 PDF、位图和纯文本生效。

`review_task` 已有 `subject_type`、`subject_id`、`subject_version_id`。下一阶段将补充知识审核主体和专用状态变更服务，复用队列及审计，不让既有 `ReviewService` 直接处理知识文档。审核权限要显式决定是否允许团队管理员自审；建议默认禁止，管理员例外单独审计。

## 技能套件与 CLI 处理

本轮已从 Web 主入口退出套件功能，旧 Web 路由转到知识库；对应后端控制器仅在显式启用 `skillhive-legacy` profile 时注册。CLI 客户端、发布工作流及套件烟测脚本已移入 `legacy-upstream/`，默认构建不再调用。ClawHub 兼容端点和 Device Flow 控制器同样默认不注册；API Token 暂保留为平台级机器接入能力。

历史 Flyway 迁移和数据表保留，**不通过迁移清除套件数据**。后续确认数据为空或完成导出后，可单独清理残余服务、DTO、前端组件和构建测试；清理需覆盖依赖引用并通过编译回归。内置上游技能默认关闭，避免自动引入 CLI 助手。

当前检入的 OpenAPI 类型仍包含上游套件与 CLI 端点，作为存量前端代码的过渡依赖。部署后的实际接口由控制器 profile 决定；在完成残余前端代码清理并从独立 SkillHive 后端重新生成类型后，才能将契约文件同步收窄。

## 运行隔离

副本与原项目共享默认主机端口，但 Compose 项目名分别为 `skillhive` 和原项目名。新项目没有复制 `.dev` 进程状态、`node_modules` 或 Maven 构建产物。不能把原项目的 `/opt/skillhub-deps/start.sh` 当作本项目启动脚本：它固定指向旧源码路径。需要并行运行时，应先为 SkillHive 分配独立端口和后端配置。

## 本轮验证

- 前端类型检查、ESLint、生产构建通过；最终全量 Vitest 通过 213 个文件、847 个测试。
- 后端定向测试 `ResourceDiscoveryAppServiceTest` 通过 2 个用例、0 失败；测试在 WSL 本地临时源码副本运行，以避开 Windows 挂载目录的文件检查延迟。
- `V66__knowledge_foundation.sql` 已在独立的 PostgreSQL 16 临时容器中执行成功，确认生成 4 张知识领域表；该容器已停止。
- SkillHive 全栈已在本机启动，Web、API、扫描器健康检查返回 200；原 SkillHub 服务已关闭。知识上传与审核 API 尚未实现。
