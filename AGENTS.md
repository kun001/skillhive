# SkillHive 工作约定

这是 `E:\MyProjects\skillhive` 的独立项目副本。原 SkillHub 位于相邻目录，不能在此项目任务中修改原目录。

## 当前产品范围

- 保留 SKILL 技能上传、版本、扫描、团队空间权限、审核、通知及审计。
- 知识库拥有独立领域模型，定位为团队文件中心：多层文件夹、上传/预览/下载、版本历史，上传即发布。不做内容解析、向量检索或 RAG 问答。
- 技能套件、CLI、ClawHub 兼容接口、设备码登录和 `.well-known` 发现端点已删除。相关历史 Flyway 迁移和数据表保留（既有迁移不可修改），不要重新接入。
- `legacy-upstream/` 保存从副本退出的上游资料，不参与构建。

## 实现边界

- 后端仍是 Java 21 / Spring Boot / Maven 多模块。控制器只负责传输，应用服务编排，领域服务负责规则；数据库迁移只能新增 Flyway 文件，不改既有迁移。
- 知识库表与 `skill` / `skill_version` 分离；复用 `namespace`、用户、对象存储、审核任务和审计设施。
- 审核权限需按对象类型明确授权；公开读、搜索索引与下载只使用已发布版本。知识内容默认不匿名开放。
- 前端继续使用 React、TypeScript、TanStack Query 与生成的 OpenAPI 类型。后端控制器改变后运行 `make generate-api`。
- 不要手工修改 `web/src/api/generated/schema.d.ts`。
- 编译验证：`make test-backend-app`、`make typecheck-web`、`make lint-web`。发布前还需容器回归。

## 发布与部署

生产环境（https://skillhive.team，宝塔服务器 `/www/skillhive`）只能按下面的流程发布，任何工具都不要自行设计其他部署方式。

- 镜像只用 GitHub Actions 构建的 `ghcr.io/kun001/skillhive-{server,web,scanner}`（`.github/workflows/publish-images.yml`）。不要在本地或服务器上构建镜像，不要 `docker save` 打包、上传镜像，也不要新写一次性的发版脚本。
- 发布版本按 `skillhive-docs/09-deployment.md` 的「6.1 发布新版本」：先提 PR 把 `charts/skillhub/Chart.yaml` 的 `appVersion` / `version` 改为新版本号并合并，再在 GitHub 创建 `vX.Y.Z` Release（同时创建标签），确认三个镜像都有该标签。版本号只用 `vX.Y.Z`；已发布的标签不移动、不删除，有问题发新的补丁版本。
- 部署只运行 GitHub Actions 的 “Deploy production”（`.github/workflows/deploy-production.yml`，命令行：`gh workflow run deploy-production.yml -f version=vX.Y.Z`）。它在服务器上执行 `deploy/baota/deploy.sh`，负责校验镜像、备份、切换和失败回退。没有 GitHub 权限时，按 `deploy/baota/README.md` 在服务器上手动执行同一个脚本。
- 不要直接修改服务器上的 `compose.yml`、`.env` 或容器；需要改部署方式时先改仓库里的脚本和文档，经 PR 合并后再用。回退用部署输出的备份目录，步骤见 `deploy/baota/README.md`。
- 部署后按 `skillhive-docs/changelog-maintenance.md` 记录实际上线的日期和版本。
- 私钥、API Token、密码等凭据只放在 GitHub Secrets 或服务器上，不写进仓库、提交信息或 PR。

架构与状态说明见 `skillhive-docs/skillhive-architecture.md`。上游规则存于 `legacy-upstream/AGENTS.md`，仅在维护尚未迁出的旧 SkillHub 代码时参考。
