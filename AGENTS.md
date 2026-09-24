# SkillHive 工作约定

这是 `E:\MyProjects\skillhive` 的独立项目副本。原 SkillHub 位于相邻目录，不能在此项目任务中修改原目录。

## 当前产品范围

- 保留 SKILL 技能上传、版本、扫描、团队空间权限、审核、通知及审计。
- 知识库拥有独立领域模型；当前 Web 页面保持空白，尚无知识上传/检索 API。
- 技能套件、CLI 与 ClawHub 兼容接口已从默认产品入口下线。历史实现仍有少量代码和迁移存在，只用于兼容与后续清理，不要重新接入新功能。
- `legacy-upstream/` 保存从副本退出的上游资料，不参与构建。

## 实现边界

- 后端仍是 Java 21 / Spring Boot / Maven 多模块。控制器只负责传输，应用服务编排，领域服务负责规则；数据库迁移只能新增 Flyway 文件，不改既有迁移。
- 知识库表与 `skill` / `skill_version` 分离；复用 `namespace`、用户、对象存储、审核任务和审计设施。
- 审核权限需按对象类型明确授权；公开读、搜索索引与下载只使用已发布版本。知识内容默认不匿名开放。
- 前端继续使用 React、TypeScript、TanStack Query 与生成的 OpenAPI 类型。后端控制器改变后运行 `make generate-api`。
- 不要手工修改 `web/src/api/generated/schema.d.ts`。
- 编译验证：`make test-backend-app`、`make typecheck-web`、`make lint-web`。发布前还需容器回归。

架构与状态说明见 `docs/skillhive-architecture.md`。上游规则存于 `legacy-upstream/AGENTS.md`，仅在维护尚未迁出的旧 SkillHub 代码时参考。
