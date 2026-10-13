# MDX 受控预览

知识库中的 `.mdx` 可在「预览 / 源码」间切换。预览将 Markdown 和下面的静态组件转换为平台界面，适合团队指南、操作步骤、FAQ、产品文档和带图说明。它支持一部分常见 MDX 写法，不承诺还原任意文档站的主题或 React 组件。

## 支持范围

| 内容 | 支持的名称 | 静态属性 / 说明 |
| --- | --- | --- |
| Markdown | 标题、段落、列表、引用、代码块、链接、图片、GFM 表格 / 任务列表 | 代码块只显示代码；Mermaid 在 MDX 中也只显示代码 |
| 提示 | `Callout`、`Admonition` | `title`、`type="note\|info\|tip\|warning\|danger"` |
| 提示快捷写法 | `Note`、`Info`、`Tip`、`Warning`、`Caution`、`Danger` | `title`；默认类型按组件名确定 |
| 折叠内容 | `Accordion`、`Details`、`AccordionGroup` | 单项 `title`，默认收起；组不接收属性 |
| 标签页 | `Tabs` + `Tab` / `TabItem` | 子项 `label`、`title`、`value`，显示名优先使用 label；支持方向键、Home、End |
| 步骤 | `Steps` + `Step` | 子项 `title`，按正文顺序编号 |
| 卡片 | `Cards` / `CardGroup` + `Card` | 子项 `title`、`description`、`href`；窄屏单列、宽屏双列 |
| 图注 | `Figure` | `caption`，内部使用 Markdown 图片或 Image |
| 图片 | `Image` / `img` | `src`、`alt`、`title`；兼容静态 `width`、`height` 属性，但按容器自动缩放，不应用文档指定尺寸 |

组件写成独立的块，内部 Markdown 与组件标签之间留空行。属性只接受引号包裹的字面文本，例如 `title="提示"`；表达式属性 `title={value}`、布尔简写和属性展开不在支持范围内。标签页中使用 Tab 子项，步骤中使用 Step 子项。

```mdx
<Callout type="warning" title="上传前检查">

确认资料适合在当前团队空间共享。

</Callout>

<Figure caption="操作流程">
<Image src="images/workflow.png" alt="上传、确认、预览流程" />
</Figure>
```

完整示例见 [团队使用指南](../examples/mdx/团队使用指南.mdx) 与 [配图](../examples/mdx/images/workflow.png)。选择这两个文件一起上传，或选择包含它们的 `mdx` 文件夹，再确认上传。示例包含所有组件和不支持语法的占位效果。

## 配图规则

- 支持 PNG、JPEG（`.jpg` / `.jpeg`）、GIF、WebP，适合截图、流程图、示意图和照片。SVG、远程图片、Data URL、Blob URL 和动态图片表达式不加载。
- 图片必须与文档上传并成为该文档版本的附件。路径按源文档目录解析，支持 `images/a.png`、`../images/a.png` 和 URL 编码的中文文件名；不可越出上传包的逻辑根目录。
- 普通文件选择器按唯一文件名匹配配图；同名图片有歧义时提示错误。文件夹上传按相对路径保存，同时保留图片作为独立文件。
- 缺少配图、图片类型不支持或图片路径没有绑定到当前版本时显示提示，不尝试访问原始地址。
- 每个文档最多 100 张配图，正文与配图合计不超过 100 MB。新版本继承未替换的配图；恢复版本复用原版本配图。

## 安全边界与降级

- 文档只做语法解析，不编译、执行、导入 JavaScript；不提供 `eval`、动态函数、模块加载或任意 React 组件执行入口。
- `import` / `export`、表达式、自定义组件、内联 JSX、HTML 标签（除静态 `img`）和含事件、样式、动态属性的组件保留为源码占位。完整源码随时可查看和下载。
- 展示组件只使用平台定义的属性，不将文档属性直接展开到 DOM。不支持 iframe、script、表单、SVG、CSS 或任意依赖。
- 图片只指向当前文档已授权的版本附件接口。正文、附件元数据和图片内容继续校验团队成员、发布版本与下载权限；无下载权限不会启动正文或配图请求。
- 链接只开放 HTTP、HTTPS、mailto 和页面片段；外链由用户点击并在新窗口打开，使用 `noopener noreferrer`。不自动请求链接，不开放脚本协议、站内相对 API 链接或带账号密码的 URL。
- 文本获取沿用 2 MiB 预览上限。受控语法解析限制为 131,072 个 JavaScript 字符单位、5,000 个语法节点及 40 层嵌套；超过限制或语法错误时显示源码。属性文本最多使用前 2,000 字符。
- `.mdx` 内容接口仍返回 `text/plain`，配图沿用固定图片 MIME、`nosniff` 与现有安全响应头，不把上传文档直接作为网页执行。

本功能仅提供展示能力，不新增内容检索、向量索引或 RAG。MCP 仍返回可读源码与已有文件接口；不会执行 MDX 内的组件或程序。
