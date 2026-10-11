# SkillHive 技能包协议

## 8.1 技能包与客户端的边界

SkillHive 存储、校验、审核和分发技能包，不执行技能，也不提供安装客户端（原 SkillHub CLI 与 ClawHub 兼容层已删除）。

- 技能包遵循 Agent Skills / OpenSkills 的 `SKILL.md` 格式和目录结构，下载后可直接放进 Claude Code 等兼容客户端的技能目录（如 `.claude/skills/<slug>/`、`.agents/skills/<slug>/`）使用
- 获取方式：网页下载 zip 包；脚本用持有 `skill:read` 的 API Token 调用 `/api/v1/skills/{namespace}/{slug}/download`；AI 客户端通过本地 MCP 服务的 `list_skill_files` / `read_skill_file` 逐个读取文件（见 [MCP 服务设计](26-mcp-server.md)）
- 服务端返回技能元数据（name、description、version）与包内文件，不生成或修改客户端的 `AGENTS.md`、安装目录或本地元数据

## 8.2 SKILL.md 规范

服务端必须兼容的格式：

```yaml
---
name: my-skill              # 必需，kebab-case
description: When to use    # 必需，1-2 句话
---

# Markdown 正文（技能指令内容）
```

解析规则：
- `name` 和 `description` 为必需字段，缺失则校验失败
- `name` 映射为 `skill.slug`（首次发布时），后续版本不可变更
- `description` 映射为 `skill.summary`
- frontmatter 完整解析结果存入 `skill_version.parsed_metadata_json`

平台扩展字段（可选，`x-astron-` 前缀）：

```yaml
---
name: my-skill
description: When to use
x-astron-category: code-review
x-astron-runtime: claude-code        # 预留
x-astron-min-version: "1.0"          # 预留
x-astron-compliance:                 # 可选，平台私有合规元数据
  - standard: mitre-attack
    version: "v19.1"
    controlId: T1059
    title: Command and Scripting Interpreter
    evidence:
      - type: packaged-file
        path: references/standards.md
---
```

> 合规元数据按平台私有扩展实现，沿用上游的 `x-astron-` 前缀，字段名为 `x-astron-compliance`。
> 当前支持发布校验、版本级 `complianceSnapshot` 固化、详情展示、审核 diff 和轻量搜索投影。
> 这些信息表示“技能作者声明的合规映射”，SkillHive 校验证据引用的格式和可追溯性，
> 但不等同于第三方认证或平台背书。设计边界、分阶段实现和 Runtime 职责划分见
> [24-compliance-metadata-design.md](24-compliance-metadata-design.md)。

`x-astron-compliance` 的稳定字段如下：

| 字段 | 必填 | 说明 |
|------|------|------|
| `standard` | 是 | 合规标准、框架或知识库标识，例如 `mitre-attack`、`nist-csf`、`soc2` |
| `version` | 是 | 标准版本或适用版本，例如 `v19.1`、`2.0` |
| `controlId` | 是 | 控制项、技术编号或条款 ID，例如 `T1059`、`PR.AA-01` |
| `title` | 否 | 人类可读的控制项名称 |
| `evidence` | 否 | 证据列表，指向包内文件或外部 URL |

`evidence` 支持两类：

| `type` | 字段 | 说明 |
|--------|------|------|
| `packaged-file` | `path` | 指向技能包内的证据文件。路径必须在包内，不能路径逃逸。 |
| `external-url` | `url` | 指向外部证据材料。URL 必须使用允许的安全 scheme。 |

发布校验规则：

- 没有 `x-astron-compliance` 的旧技能继续正常发布。
- `x-astron-compliance` 存在时必须是数组。
- `standard`、`version`、`controlId` 必填。
- 同一技能版本内不允许重复 `standard + version + controlId`。
- `packaged-file.path` 必须存在于上传包内，且不能使用 `../` 等方式逃逸包目录。
- 合法合规声明会被规范化为版本级 `complianceSnapshot`，并生成稳定 `digest`。

Runtime 集成边界：

- SkillHive 是技能元数据和版本级 `complianceSnapshot` 的权威源。
- Agent Runtime 是执行 trace 的权威源。
- Runtime 如需在执行链路中记录合规上下文，应引用 SkillHive 返回的不可变版本 `id`
  和 `complianceSnapshot.digest`，而不是复制或改写 SkillHive 的声明内容。
- SkillHive 当前不记录 Agent 执行输入输出、Runtime trace 或实际调用结果。

## 8.3 技能包目录结构

```
my-skill/
├── SKILL.md              # 主入口文件（必需）
├── references/           # 参考资料（可选）
├── scripts/              # 脚本（可选）
└── assets/               # 静态资源（可选）
```

校验规则：
- 根目录必须包含规范入口文件 `SKILL.md`；上传时服务端兼容 `skill.md`、`Skill.md` 等大小写变体，并在内部归一化为 `SKILL.md`
- 文件类型白名单：以 `SkillPackagePolicy.ALLOWED_EXTENSIONS` 为准，覆盖文档（`.md`、`.txt`、`.json`、`.yaml`、`.pdf` 等）、配置（`.toml`、`.xml`、`.ini` 等）、脚本与源码（`.js`、`.ts`、`.py`、`.sh`、`.go`、`.java` 等）、图片（`.png`、`.jpg`、`.svg`、`.webp` 等）和 Office 文档；可用 `SKILLHUB_PUBLISH_ALLOWED_FILE_EXTENSIONS` 覆盖
- 单文件大小限制：10MB（`skillhub.publish.max-single-file-size`）
- 总包大小限制：100MB（`skillhub.publish.max-package-size`）
- 文件数量限制：100 个（`skillhub.publish.max-file-count`）

## 8.4 版本解析规则

技能坐标为 `@{namespace_slug}/{skill_slug}`，`@global` 是平台公共空间。下载与解析接口按以下规则选择版本：

| 请求 | 解析到 |
|------|--------|
| `GET /api/v1/skills/{ns}/{slug}/download` | 最新已发布版本（`latest_version_id` / published pointer） |
| `GET /api/v1/skills/{ns}/{slug}/versions/{version}/download` | 精确版本 |
| `GET /api/v1/skills/{ns}/{slug}/tags/{tag}/download` | 自定义标签（如 `beta`）指向的版本 |
| `GET /api/v1/skills/{ns}/{slug}/resolve?version=&tag=&hash=` | 只解析，不下载，返回精确版本信息 |

不同 namespace 下可以有同名技能；客户端把技能放进本地目录时以 `skill.slug` 为目录名，同名冲突由使用者自行处理。
