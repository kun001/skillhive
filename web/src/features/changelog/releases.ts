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
