type LocalizedText = { zh: string; en: string }

export interface ReleaseNote {
  id: string
  version: string
  date: string
  dateEnd?: string
  title: LocalizedText
  summary: LocalizedText
  changes: LocalizedText[]
}

// Entries ship with their release image, newest first. No draft or upcoming entries.
// Confirm the date and image tag when deploying; Git submission is not deployment.
export const releases: ReleaseNote[] = [
  {
    id: '20261015-mcp-knowledge-crud',
    version: 'v0.4.1',
    date: '2026-10-15',
    title: { zh: 'MCP 支持知识库增删查改与文件版本更新', en: 'MCP knowledge management and file version updates' },
    summary: { zh: 'Agent 可通过远程 HTTP 或本地 stdio 管理知识库和目录、修改文件信息并上传新版本，新增操作分别授权。', en: 'Agents can manage knowledge bases and folders, edit file metadata and upload new versions over remote HTTP or local stdio, with separate grants for the new operations.' },
    changes: [
      { zh: '新增知识库详情、新建、修改和删除工具，以及目录的新建、修改和删除工具；知识库管理要求团队所有者或管理员权限，目录操作沿用现有业务权限。', en: 'Added tools to inspect, create, update and delete knowledge bases, and create, update and delete folders. Base management requires team owner or administrator permissions; folder operations follow existing business permissions.' },
      { zh: '支持修改文件标题、描述和所在目录，以及上传新版本；版本上传须匹配当前版本，保留历史文件，并发修改不会静默覆盖。', en: 'Edit file titles, descriptions and folders, and upload new versions. Uploads must match the current version, retain historical files and reject conflicting concurrent changes.' },
      { zh: 'Token 新增独立的“更新知识文件”和“管理知识库与目录”授权，可在创建和查看权限面板中设置与核对；旧 Token 不会自动获得新权限。', en: 'Tokens gain independent grants for updating knowledge files and managing bases and folders, shown in both creation and permission panels. Existing tokens do not gain new permissions automatically.' },
      { zh: '写入继续检查资源范围和实时业务权限，支持幂等与审计；非空目录不能删除，知识库须清空有效文件后才能归档，保留历史版本。', en: 'Writes continue to check resource ceilings and current business permissions, with idempotency and auditing. Nonempty folders cannot be deleted; bases can be archived only after active files are removed, retaining version history.' },
      { zh: '更新首页间距、文件摘要标签与中英文 README，补充 MCP 使用文档和可复用回归脚本。', en: 'Refined homepage spacing, file summary labels and bilingual READMEs, with updated MCP documentation and a reusable regression script.' },
    ],
  },
  {
    id: '20261015-agent-context-center',
    version: 'v0.4.0',
    date: '2026-10-15',
    title: { zh: '团队 Agent 上下文中心：新首页、文档文本提取、摘要、全文检索与团队说明书', en: 'Team agent context center: new homepage, document text extraction, summaries, full-text search and team guides' },
    summary: { zh: 'SkillHive 定位为团队 Agent 的私有上下文中心；知识文件自动转为 Agent 可读的 Markdown，跨知识库搜索覆盖正文，团队空间新增说明书。', en: 'SkillHive is now the private context center for team agents. Knowledge files become agent-readable Markdown, cross knowledge base search covers file text, and team spaces gain guides.' },
    changes: [
      { zh: '首页重新设计：Agent 调用演示、上下文流向、治理与审计、与网盘和 RAG 平台的对比，以及可复制的 MCP 配置；网页默认深色主题，已选择浅色的浏览器保持原选择。', en: 'Redesigned homepage with an agent transcript demo, context flow, governance and audit, a comparison with drives and RAG platforms, and a copyable MCP configuration. The web app defaults to dark theme; browsers that chose light keep it.' },
      { zh: 'Word、PowerPoint、Excel、PDF 和文本文件在服务端提取 Markdown 文本，扫描件和不支持的格式跳过，已有文件自动补齐；MCP read_document 默认返回 Markdown，format=original 返回原文件。', en: 'Word, PowerPoint, Excel, PDF and text files are extracted to Markdown on the server; scans and unsupported formats are skipped and existing files are backfilled. MCP read_document returns Markdown by default; format=original returns the file.' },
      { zh: '可选大模型摘要与关键词（默认关闭，启用后文件文本会发送给配置的 OpenAI 兼容接口）。', en: 'Optional model summaries and keywords (off by default; enabling them sends file text to the configured OpenAI-compatible endpoint).' },
      { zh: '跨知识库搜索覆盖标题、描述、摘要与正文，中文按二元切分建全文索引，结果附摘要和命中片段；读取正文需要下载权限并记录审计。', en: 'Cross knowledge base search covers titles, descriptions, summaries and text with a CJK bigram full-text index, showing summaries and excerpts. Reading text requires download permission and is audited.' },
      { zh: '新增团队说明书：所有者和管理员可在团队空间页编辑，Agent 通过 MCP get_team_guide 读取。', en: 'Team guides: owners and admins edit them on the team space page, and agents read them through MCP get_team_guide.' },
    ],
  },
  {
    id: '20261014-mcp-scoped-writes',
    version: 'v0.3.0',
    date: '2026-10-14',
    title: { zh: 'MCP 新增上传、归档与资源范围授权', en: 'MCP uploads, archiving and resource grants' },
    summary: { zh: '可分别授权 AI 工具读取、上传或删除技能与知识文件，并限定团队空间和知识库；个人中心可查看每个 Token 的完整权限。', en: 'Grant AI tools separate read, upload and delete permissions for skills and knowledge files, limited to selected spaces and knowledge bases. Inspect each token’s full permissions in your profile.' },
    changes: [
      { zh: '技能与知识文件各提供读取、新增上传和删除三项独立权限；Token 可限定团队空间与知识库，加入新团队不会自动扩大原授权。', en: 'Skills and knowledge files each have independent read, upload and delete permissions. Tokens can be restricted to selected spaces and knowledge bases; joining a new team does not expand existing grants.' },
      { zh: '远程 HTTP 与本地 stdio 均支持上传新技能 ZIP、上传新知识文件及归档；同名内容拒绝覆盖，技能上传继续扫描和审核，知识文件上传即发布。', en: 'Remote HTTP and local stdio support new skill ZIP uploads, new knowledge file uploads and archiving. Existing names cannot be overwritten. Skills still pass scanning and review; knowledge files publish on upload.' },
      { zh: '写入要求有效成员身份和业务权限，管理员 Token 也不能跨团队；写入凭证默认 7 天、最长 30 天，撤销或权限变更对后续调用生效。', en: 'Writes require current membership and business permissions, including administrator tokens. Write tokens default to seven days and expire within thirty days. Revocation and permission changes affect subsequent calls.' },
      { zh: '写入要求幂等键，重复请求不会重复创建；删除按现有规则归档，保留历史版本与文件，写入操作记录审计。旧只读 Token 继续可用。', en: 'Writes require an idempotency key, so retries do not create duplicates. Deletions archive content under existing rules, retaining versions and files, and writes are audited. Existing read-only tokens remain supported.' },
      { zh: 'Token 页面新增权限查看面板，统一 MCP 文案；文件夹上传和 Markdown / MDX 配图提示更清晰，技能库移除重复说明。', en: 'The Tokens page adds a permissions panel and consistent MCP wording. Folder upload and Markdown / MDX image guidance is clearer, and duplicate skill library copy has been removed.' },
    ],
  },
  {
    id: '20261013-mdx-folder-remote-mcp',
    version: 'v0.2.0',
    date: '2026-10-13',
    title: { zh: 'MDX 受控预览、文件夹上传与远程 MCP', en: 'Controlled MDX previews, folder uploads and remote MCP' },
    summary: { zh: '知识库支持带配图的 MDX 内置组件预览，上传文件夹可保留目录并二次确认；Agent 可以通过实例地址和个人只读 Token 接入 MCP。', en: 'Knowledge bases support MDX previews with built-in components and local images. Folder uploads preserve directories after confirmation, and agents can connect to MCP using the instance URL and a personal read-only token.' },
    changes: [
      { zh: 'MDX 支持 Markdown、提示卡片、折叠区、标签页、步骤、静态卡片和图注，可切换源码；JavaScript、模块导入和自定义组件不会执行，不支持的语法保留为源码。', en: 'MDX previews support Markdown, callouts, accordions, tabs, steps, static cards and figures, with a source toggle. JavaScript, imports and custom components never execute; unsupported syntax stays as source.' },
      { zh: 'MDX 配图支持随文档上传的 PNG、JPEG、GIF 和 WebP，沿用文档版本、团队成员与下载权限；远程图片不加载，无下载权限无法读取正文、配图路径或图片内容。', en: 'Local PNG, JPEG, GIF and WebP attachments follow document versions, team membership and download permission. Remote images are not loaded, and members without download permission cannot read source, image paths or image content.' },
      { zh: '选择文件夹后先查看目标位置、文件数量与目录结构，再确认上传；保留顶层文件夹及子目录，同名目标目录不会自动合并，部分失败可重试。浏览器无法上传空文件夹。', en: 'Review the destination, file count and directory tree before confirming folder uploads. Top-level folders and subdirectories are retained, existing folders are not merged, and failed files can be retried. Browsers cannot upload empty folders.' },
      { zh: '新增远程只读 MCP 接口 /api/v1/mcp，Token 页面可复制连接配置；仅接受技能只读与知识库只读范围，每次调用检查成员、可见版本和下载权限，管理员 Token 也不能越过团队边界。', en: 'The new read-only /api/v1/mcp endpoint provides remote access, with copyable configuration on the Tokens page. Only skill-read and knowledge-read scopes are accepted. Every call checks membership, visible versions and download permission, including administrator tokens.' },
      { zh: '补充中英文 README、MCP 使用方式、组件与配图说明，以及可直接上传的 MDX 和 PNG 示例。', en: 'Updated the Chinese and English READMEs, MCP setup guide, component and image documentation, and an uploadable MDX example with a PNG image.' },
    ],
  },
  {
    id: '20261012-namespace-knowledge-delete',
    version: 'v0.1.1',
    date: '2026-10-12',
    title: { zh: '修复清空知识文件后删除团队空间失败', en: 'Delete empty team spaces after removing knowledge files' },
    summary: { zh: '知识文件全部删除后，空间所有者可以删除团队空间；存在未删除文件时会明确阻止删除。', en: 'Space owners can delete team spaces after deleting all knowledge files. Spaces with undeleted files remain protected.' },
    changes: [
      { zh: '修复知识库记录导致删除命名空间出现“服务器内部错误”的问题。', en: 'Fixed an internal server error caused by knowledge base records when deleting a namespace.' },
      { zh: '删除空间会同时清理知识库、文件夹及已删除文件的版本历史和附件，并重试存储文件清理。', en: 'Deleting a space also removes its knowledge bases, folders, and deleted file history and attachments, with retryable storage cleanup.' },
      { zh: '存在未删除的知识文件时禁止删除空间；其他空间的文件和权限不受影响。', en: 'Undeleted knowledge files prevent space deletion. Other spaces retain their files and permissions.' },
    ],
  },
  {
    id: '20261011-token-scopes',
    version: 'cloud-20261011-mcp-readonly',
    date: '2026-10-11',
    title: { zh: 'API Token 分为技能库与知识库访问范围，支持 MCP 只读接入', en: 'Separate skill and knowledge token access, with read-only MCP access' },
    summary: { zh: '创建 API Token 时可以分别设置技能库（读写 / 只读）和知识库（只读）的访问范围。配合只读 Token，AI 工具可以通过 SkillHive MCP 服务读取你有权查看的技能和知识库文件，读取记录会写入审计日志。', en: 'When creating an API token you can now set skill access (read-write or read-only) and read-only knowledge access separately. With a read-only token, AI tools can read the skills and knowledge files you can see through the SkillHive MCP server, and reads are recorded in the audit log.' },
    changes: [
      { zh: '个人中心开放“Token 凭证”页面，可以创建、查看访问范围、修改过期时间和删除 API Token。', en: 'The Tokens page in your profile is now available: create API tokens, see their access, change their expiry and delete them.' },
      { zh: '技能库访问可选“读写”“只读”或“不授予”，新增“知识库（只读）”访问范围。已有 Token 和未开启知识库的新 Token 不能访问知识库。', en: 'Skill access can be read-write, read-only or none, and there is a new read-only knowledge access. Existing tokens and new tokens without knowledge access cannot reach the knowledge base.' },
      { zh: '技能库与知识库互不相通：只有知识库权限的 Token 不能读取技能、我的技能、收藏和订阅；只有技能库权限的 Token 也读不到知识库。', en: 'Skill and knowledge access are kept apart: a knowledge-only token cannot read skills, My Skills, favorites or subscriptions, and a skills-only token cannot read the knowledge base.' },
      { zh: 'Token 列表新增“访问范围”一列，显示每个 Token 能访问的范围。', en: 'The token list now has an Access column showing what each token can reach.' },
      { zh: '新增本地 MCP 服务（仓库 mcp/ 目录），只接受只读 Token，让 AI 工具搜索和读取技能与知识库文件；PDF、Office 等文件原样传递。', en: 'A new local MCP server (the mcp/ directory) accepts read-only tokens only and lets AI tools search and read skills and knowledge files; PDF, Office and other files are passed through unchanged.' },
      { zh: '审计：知识库文件的每次下载和预览都会记录来自网页还是哪个 Token；通过 Token 读取技能文件或下载技能包也会记录，网页和匿名访问不记录。', en: 'Audit: every knowledge file download and preview records whether it came from the web or which token, and skill file reads and package downloads made with a token are recorded too; web and anonymous skill reads are not.' },
      { zh: '隐私政策和服务条款按 SkillHive 的产品定位重写，补充了知识库、成员权限、API Token、MCP 接入和审计记录的说明。', en: 'The Privacy Policy and Terms of Service were rewritten for SkillHive, covering the knowledge base, member permissions, API tokens, MCP access and audit records.' },
      { zh: 'Token 与网页登录使用同一套权限：只能看到自己所在团队的知识库，读取文件内容同样需要下载权限。平台管理员的 Token 也只能读取自己所在团队的知识库。知识库的新建、上传、修改和删除仍不接受 Token。', en: 'Tokens follow the same permissions as web sign-in: they only see your own teams\' knowledge bases and reading file content still requires download permission. Even platform administrators\' tokens only read their own teams\' knowledge. Creating, uploading, editing and deleting knowledge content still does not accept tokens.' },
    ],
  },
  {
    id: '20261011-preview-requires-download',
    version: 'cloud-20261011-preview-download',
    date: '2026-10-11',
    title: { zh: '预览与下载权限统一', en: 'Preview now follows download permission' },
    summary: { zh: '在线预览会把原文件交给浏览器，现在预览与下载使用同一项权限：能下载才能预览，成员默认允许下载。', en: 'Online previews hand the original file to the browser, so preview and download now share one permission: members who can download can preview, and members can download by default.' },
    changes: [
      { zh: '团队技能的文件预览、SKILL.md 文档和版本对比，以及知识库的 PDF、图片、文本、Markdown 和 Word / PPT 预览，都需要下载权限；网页和 API 两条通道同样生效。', en: 'Team skill file previews, SKILL.md documentation and version comparisons, plus knowledge base PDF, image, text, Markdown and Word / PowerPoint previews, now require download permission on both the web and API surfaces.' },
      { zh: '没有下载权限的成员会看到“无下载权限，无法预览”的提示，不再出现加载失败或空白预览；技能简介、文件列表和文档信息照常显示。', en: 'Members without download permission see a clear "No download permission, preview unavailable" message instead of a failed or blank preview. Skill summaries, file lists and document details remain visible.' },
      { zh: '新成员默认允许下载，已有成员的下载设置保持不变；管理员仍可在成员管理中按人关闭下载。', en: 'New members can download by default and existing member settings are unchanged. Admins can still turn off downloads per member in member management.' },
    ],
  },
  {
    id: '20261010-security-ux',
    version: 'cloud-20261010-security-ux',
    date: '2026-10-10',
    title: { zh: '会话安全加固与体验修复', en: 'Session security and experience fixes' },
    summary: { zh: '撤权或改密后旧会话立即失效；升级服务端组件并加强网页安全头；知识库上传筛选、我的技能发布入口和技能内 Office 预览也一并修好。', en: 'Old sessions end right away after role revoke or password change. Server components and web security headers are hardened. Knowledge upload filters, the My Skills publish entry, and in-skill Office preview are fixed.' },
    changes: [
      { zh: '调整管理员角色、禁用账号、修改或找回密码后，旧登录会话立即失效；改密时当前浏览器保持登录，其他设备需重新登录。', en: 'After changing admin roles, disabling an account, or changing or resetting a password, older sessions end immediately. The browser that changed the password stays signed in; other devices must sign in again.' },
      { zh: '升级 Spring Boot 与内嵌 Tomcat，并为网页响应增加内容安全策略、防点击劫持和强制 HTTPS 等安全头。', en: 'Upgraded Spring Boot and embedded Tomcat, and added Content-Security-Policy, clickjacking protection, HSTS and related security headers on web responses.' },
      { zh: '上传知识文件后按新文件类型自动调整筛选；我的技能空状态可直接发布；技能包内的 .docx / .pptx 可在浏览器中预览。', en: 'After uploading knowledge files, filters follow the new file types. The empty My Skills state offers a publish action. .docx and .pptx templates inside skills preview in the browser.' },
    ],
  },
  {
    id: '20261010-skill-search',
    version: 'cloud-20261010-skill-search',
    date: '2026-10-10',
    title: { zh: '技能搜索支持简介关键词', en: 'Skill search matches descriptions' },
    summary: { zh: '搜索技能时可以直接输入简介里的中文或英文词语；清空搜索框后立即恢复完整列表。', en: 'Search now finds skills by words in their Chinese or English descriptions, and clearing the search box restores the full list right away.' },
    changes: [
      { zh: '搜索会匹配技能中英文功能描述和简介中的关键词，中文短语无需与完整词语一致也能找到。', en: 'Search matches keywords in a skill\'s Chinese and English descriptions and summary, including Chinese phrases inside longer sentences.' },
      { zh: '清空搜索框后立即显示全部技能，无需再点击“搜索”；按回车或点击“搜索”的方式保持不变。', en: 'Clearing the search box shows all skills immediately without clicking Search. Pressing Enter or clicking Search works as before.' },
    ],
  },
  {
    id: '20261009-skill-introductions',
    version: 'cloud-20261009-skill-introductions',
    date: '2026-10-09',
    title: { zh: '技能说明更易读，中英文展示更一致', en: 'Clearer skill introductions in Chinese and English' },
    summary: { zh: '新上传的技能版本自动生成中英文功能描述和使用方法，帮助你理解技能用途、向 AI 提出具体需求。', en: 'New skill versions automatically receive Chinese and English descriptions and usage guidance, helping you understand each skill and give AI a concrete request.' },
    changes: [
      { zh: '技能详情新增“功能描述”和“使用方法”，附有可直接发给 AI 的示例请求；原简介和技能文档继续保留。', en: 'Skill details now include What it does and How to use it, with an example request you can send to AI. Original summaries and skill documentation remain available.' },
      { zh: '技能库、首页、搜索、团队空间以及我的技能、收藏和订阅列表，优先显示当前可见版本已生成的功能描述，并随中英文切换；完整悬停提示同步切换。', en: 'Skill libraries, homepage cards, search, team spaces and personal skill, favorite and subscription lists prefer the generated description of the visible version. Descriptions and full hover text follow the selected Chinese or English language.' },
      { zh: '说明按技能版本保存。生成期间或失败时保留原简介，不阻断上传、扫描和审核；已有版本不会自动批量生成说明。', en: 'Introductions belong to each skill version. Original summaries remain visible while generation is pending or fails, without blocking uploads, scans or reviews. Existing versions are not automatically processed in bulk.' },
      { zh: '移除技能评分和评论入口，保留收藏与订阅，让页面更聚焦于技能内容和团队使用。', en: 'Removed rating and review controls while keeping favorites and subscriptions, so pages focus on skill content and team use.' },
      { zh: '语言切换保留中文和英文，精简不适用的语言选项及重复展示文案。', en: 'The language switcher now offers Chinese and English, with unused language options and redundant copy removed.' },
    ],
  },
  {
    id: '20261008-browser-office-preview',
    version: 'cloud-20261008-browser-preview',
    date: '2026-10-08',
    title: { zh: 'Word 和 PPT 直接在浏览器中预览', en: 'Word and PowerPoint previews in the browser' },
    summary: { zh: '知识库中的 .docx 和 .pptx 文件直接在浏览器内渲染预览，无需等待服务器转换。', en: 'Knowledge base .docx and .pptx files now render directly in the browser, without waiting for a server-side conversion.' },
    changes: [
      { zh: '.docx 文档和 .pptx 演示文稿在浏览器中完整预览，打开更快。', en: '.docx documents and .pptx presentations get full previews in the browser and open faster.' },
      { zh: '旧版 .doc 和 .ppt 文件仍可上传和下载，但不再提供在线预览。', en: 'Legacy .doc and .ppt files can still be uploaded and downloaded, but no longer have online previews.' },
      { zh: '移除服务器端的 Office 转换服务，部署更轻量。', en: 'Removed the server-side Office conversion service for a lighter deployment.' },
    ],
  },
  {
    id: '20261006-public-team-skills',
    version: 'cloud-20261006-public-team-skills',
    date: '2026-10-06',
    title: { zh: '新增公共技能库与团队技能库', en: 'Separate public and team skill libraries' },
    summary: { zh: '公共技能无需登录即可浏览和下载；团队技能按成员权限访问。', en: 'Browse and download public skills without signing in; team skills follow member access permissions.' },
    changes: [
      { zh: '技能库新增“公共技能库”和“团队技能库”两个入口，分别查看公共技能和你加入的团队共享技能。', en: 'The skill library now has Public Skills and Team Skills views for public skills and skills shared by teams you belong to.' },
      { zh: '所有人都可查看和下载已发布的公共技能，无需登录；团队技能继续按成员权限控制浏览、编辑和下载。', en: 'Everyone can view and download published public skills without signing in. Browsing, editing and downloading team skills still follow member permissions.' },
      { zh: 'Global 空间及其知识资料仅向平台管理员显示，普通成员不再看到管理入口；公共技能仍可正常使用。', en: 'The Global space and its knowledge resources are visible only to platform administrators. Ordinary members no longer see its management entry, while public skills remain accessible.' },
      { zh: '切换技能库时保留搜索、标签和排序条件，并从第一页开始展示。', en: 'Switching libraries keeps the search, label and sort filters and returns to the first page.' },
    ],
  },
  {
    id: '20261006-register-username',
    version: 'cloud-20261006-register-username',
    date: '2026-10-06',
    title: { zh: '支持中文用户名，登录注册更简洁', en: 'Chinese usernames and a simpler sign-in' },
    summary: { zh: '注册时可以使用中文用户名；登录和注册页只保留账号密码方式。', en: 'Usernames can now be Chinese, and the sign-in and registration pages offer username and password only.' },
    changes: [
      { zh: '用户名支持中文、字母、数字或下划线，长度 2-64 位；用户 ID 仍随机分配。', en: 'Usernames accept Chinese characters, letters, digits or underscores, 2-64 characters long; user IDs are still assigned at random.' },
      { zh: '登录页和注册页移除 OAuth 入口，只保留账号密码表单。', en: 'Removed the OAuth option from the sign-in and registration pages, leaving the username and password form.' },
    ],
  },
  {
    id: '20261006-namespace-knowledge',
    version: 'cloud-20261006-namespace-knowledge',
    date: '2026-10-06',
    title: { zh: '团队空间汇集技能与知识库', en: 'Team spaces bring skills and knowledge together' },
    summary: { zh: '在团队空间里同时查看技能和知识库，并可跨知识库搜索文件。', en: 'See a team space’s skills and knowledge bases in one place, and search files across knowledge bases.' },
    changes: [
      { zh: '团队空间页分为“技能”和“知识库”两个标签，知识库标签列出该空间的知识库，并可在空间内搜索文件；非成员会看到仅限成员访问的提示。', en: 'Team space pages have Skills and Knowledge tabs. The knowledge tab lists the space’s knowledge bases and searches files within the space; non-members see that knowledge is for members only.' },
      { zh: '知识库首页的搜索同时查找知识库和所有可访问知识库中的文件，结果标明所属团队空间和知识库。', en: 'Search on the knowledge home finds knowledge bases and files across every accessible knowledge base, showing the team space and knowledge base of each result.' },
      { zh: '知识库卡片标明所属团队空间，可按团队空间筛选；知识库页面的路径可返回所属团队空间。', en: 'Knowledge base cards show their team space and can be filtered by space; the knowledge base breadcrumb links back to its team space.' },
      { zh: '文件列表在手机和窄屏上完整显示，长文件名自动截断，文件大小和更新时间显示在文件名下方。', en: 'File lists fit phones and narrow screens: long names are truncated, with size and update time shown beneath the name.' },
      { zh: '精简首页首屏，移除搜索框和技能数量统计。', en: 'Simplified the homepage hero by removing the search box and skill count.' },
    ],
  },
  {
    id: '20261006-member-permissions',
    version: 'cloud-20261006-member-permissions',
    date: '2026-10-06',
    title: { zh: '按成员管理团队资源权限', en: 'Manage team resources by member' },
    summary: { zh: '技能与工作资料回归团队内共享，管理员可以为每位成员配置编辑和下载权限。', en: 'Skills and work resources are shared within teams, with editing and download permissions configured for each member.' },
    changes: [
      { zh: '技能库与知识库统一要求登录，仅向所属团队成员展示可访问的内容。演示账号使用普通成员身份登录。', en: 'Skills and knowledge libraries require sign-in and show accessible resources to team members. Demo accounts sign in as ordinary members.' },
      { zh: '成员管理新增只读与可编辑设置，下载权限单独控制；调整后立即对已登录的成员生效。', en: 'Member management now supports read-only or editable access, with downloads controlled independently. Changes take effect for existing sessions immediately.' },
      { zh: '只读成员可以浏览资料、查看版本历史和预览文件；未获下载权限时，原文件、技能包及历史版本均不可下载。', en: 'Read-only members can browse, inspect version history and preview files. Original files, skill packages and historical downloads require download permission.' },
      { zh: '新增更新日志页面，按版本展示更新内容；入口位于导航右侧，无需登录即可查看。', en: 'Added a version-based changelog, accessible without sign-in from the right side of the navigation.' },
    ],
  },
  {
    id: '20261005-source-link',
    version: 'cloud-20261005-source-link',
    date: '2026-10-05',
    title: { zh: '找到 SkillHive 的源码', en: 'Find the SkillHive source code' },
    summary: { zh: '从首页直接进入当前项目仓库，方便了解项目和跟进开发。', en: 'The homepage now links directly to the current project repository.' },
    changes: [
      { zh: '修正首页源码链接，指向 SkillHive 的 GitHub 仓库。', en: 'Corrected the homepage source link to point to the SkillHive GitHub repository.' },
    ],
  },
  {
    id: '20261004-launch',
    version: 'cloud-20261004',
    date: '2026-10-04',
    dateEnd: '2026-10-05',
    title: { zh: 'SkillHive 正式上线', en: 'SkillHive is live' },
    summary: { zh: '技能仓库与团队文件中心上线，集中管理可复用的方法和工作资料。', en: 'The skill repository and team file hub are live, bringing reusable workflows and work resources together.' },
    changes: [
      { zh: '技能库支持技能包上传、版本管理、安全扫描和审核，访客可查看已发布的公开技能。', en: 'Upload skill packages, manage versions, scan and review them. Visitors can browse published public skills.' },
      { zh: '团队知识库支持多层文件夹、上传、下载与版本历史，并提供 Word / PowerPoint 的前五页图片预览。', en: 'Team knowledge libraries support nested folders, uploads, downloads and version history, with previews of the first five Word / PowerPoint pages.' },
      { zh: '启用 skillhive.team 域名和 HTTPS 访问，统一站点入口。', en: 'Enabled skillhive.team and HTTPS as the main site address.' },
    ],
  },
]
