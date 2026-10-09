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
