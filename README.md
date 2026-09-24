# SkillHive

SkillHive 是基于 SkillHub 源码建立的团队知识与 SKILL 技能管理项目。当前版本保留用户、团队空间、权限、技能上传、安全扫描、审核、通知和审计能力；知识库页面是空白工作区，尚未开放知识上传或检索。

本机数据库已从原项目迁入 23 个已发布 Skill；CLI 教学技能按当前范围归档。数据库与存储文件不纳入源码仓库，克隆后需按[技能迁入说明](docs/skill-import-2026-09-24.md)准备数据。

## 当前边界

- Web 入口：SKILL 技能、空白知识库、团队空间、审核与管理。
- 技能套件及 CLI/ClawHub 接口默认不注册；旧 URL 由前端转向现有入口。
- CLI 客户端及其发布流程已移入本地 `legacy-upstream/` 归档，不参与构建或源码仓库发布。
- 旧数据库迁移保留，现有套件数据不会被自动删除；知识领域基础表由 `V66__knowledge_foundation.sql` 创建。
- 旧 SkillHub 文档保留在 `docs/`，作为上游参考；SkillHive 当前决策见 [架构说明](docs/skillhive-architecture.md)。

## 本地开发

与原 SkillHub 项目使用相同的默认监听端口。**两套应用不可直接同时按默认端口启动。** 新项目使用独立的 Docker Compose 项目名 `skillhive`，不会复用原项目的容器数据卷。

本机使用 [SkillHive 启停说明](LOCAL_WSL_START.md) 和专用脚本操作。首次启动前需为此副本安装前端依赖并构建后端；原项目的机器专用启动脚本指向旧路径，不适用于本项目。当前代码改造的编译与迁移验证记录见架构说明。

## 来源

从 [iflytek/skillhub](https://github.com/iflytek/skillhub) 的本地副本 `E:\MyProjects\skillhub` 复制创建，保留了复制时的未提交界面改动。原目录未被修改。SkillHive 使用独立 Git 历史和 GitHub 仓库；上游 Git 历史仅在本地归档。
