# Skill 搜索与发现

## 功能描述

SkillHive 提供了强大的全文搜索功能，让用户可以快速找到需要的技能包。

搜索不仅支持关键词匹配，还支持按命名空间、标签、下载量、评分等多维度过滤和排序。

**核心特性**：

- **全文搜索**：搜索技能包名称、描述、标签、作者
- **智能过滤**：按命名空间、标签、可见性过滤
- **多种排序**：按相关性、下载量、评分、更新时间排序
- **权限感知**：只显示用户有权访问的技能包
- **实时更新**：新发布的技能包立即出现在搜索结果中

**搜索算法**：

SkillHive 使用 PostgreSQL 全文搜索，支持：
- 中英文分词
- 模糊匹配
- 权重排序（标题权重 > 描述权重 > 标签权重）

## 使用场景

**场景一：新成员探索**

新加入团队的开发者想了解团队已有哪些可用的技能包。

**场景二：按需查找**

开发者需要一个处理 PDF 的技能包，搜索 "pdf" 关键词。

**场景三：浏览热门**

查看团队内下载量最高、评分最好的技能包，学习最佳实践。

**场景四：按标签过滤**

只查看 `data-processing` 标签的技能包。

## 使用步骤

### 在 AI 工具中搜索（MCP）

配置好 [SkillHive MCP 服务](/guide/api-token-mcp) 后，可以直接让 AI 工具调用 `search_skills` 搜索技能，再用 `read_skill_file` 读取 `SKILL.md` 和包内文件。

### 使用 Web UI 搜索

1. **访问搜索页面**

打开「技能库」页面（`/skills`），在「公共技能库」和「团队技能库」之间切换，或使用顶部搜索框。

2. **输入关键词**

在搜索框输入关键词，例如 "pdf parser"。

3. **应用过滤器**

- 选择命名空间（例如只看 `ai-team` 命名空间）
- 选择标签（例如 `data-processing`）
- 选择排序方式（例如按下载量降序）

4. **查看结果**

搜索结果会实时更新，显示匹配的技能包列表。

5. **查看详情**

点击技能包卡片，查看详细信息、版本历史、文件列表。

6. **下载使用**

找到合适的技能包后，点击「下载」获取 zip 包，解压到 AI 工具的技能目录（如 `.claude/skills/`）；也可以通过 MCP 让 AI 工具直接读取。

## API 接口

**搜索技能包**：
```bash
GET /api/web/skills?q=pdf&namespace=ai-team&label=data-processing&sort=downloads&page=0&size=20
```

**参数说明**：
| 参数 | 类型 | 说明 |
|------|------|------|
| q | string | 搜索关键词（可选） |
| namespace | string | 命名空间过滤（可选） |
| label | string[] | 标签过滤（可选，可多选） |
| sort | enum | 排序方式：relevance（相关性）、downloads（下载量）、rating（评分）、updated（更新时间） |
| page | number | 页码（从 0 开始） |
| size | number | 每页数量（默认 20，最大 100） |

**响应示例**：
```json
{
  "content": [
    {
      "id": "skill-123",
      "namespace": "ai-team",
      "slug": "pdf-parser",
      "name": "PDF Parser",
      "description": "Extract text and metadata from PDF files",
      "downloads": 1234,
      "rating": 4.5,
      "starCount": 56,
      "latestVersion": "1.2.3",
      "updatedAt": "2026-03-15T10:30:00Z",
      "labels": ["data-processing", "pdf"]
    }
  ],
  "totalElements": 42,
  "totalPages": 3,
  "number": 0,
  "size": 20
}
```

## 注意事项

> **权限控制**：搜索结果会根据用户权限自动过滤。PRIVATE 技能包只对命名空间成员可见，INTERNAL 技能包只对登录用户可见。

- **搜索性能**：SkillHive 使用 PostgreSQL 全文搜索，支持中英文分词
- **实时更新**：新发布的技能包会立即出现在搜索结果中
- **标签规范**：建议使用统一的标签命名规范，便于过滤
- **搜索提示**：支持搜索建议和自动补全（前端实现）
