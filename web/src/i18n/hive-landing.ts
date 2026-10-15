// Homepage copy: SkillHive as the private context center for a team's agents.
// Shared shell keys (homeAria, footerDescription, skillLibraryLabel, knowledgeLabel,
// knowledgeDescription) are also read by the layout, user menu and dashboard.
export const hiveLandingZh = {
  homeAria: 'SkillHive 首页',
  skillLibraryLabel: '技能库',
  knowledgeLabel: '知识库',
  knowledgeDescription: '模板、规范、手册和复盘按团队与文件夹组织，成员和 Agent 都能按权限取用。',
  footerDescription: '团队 Agent 的私有上下文中心。',

  hero: {
    eyebrow: '团队 Agent 的私有上下文中心',
    titleFirst: '让每个 Agent，',
    titleSecond: '都懂你的团队。',
    description: '把团队沉淀的做法（技能）和资料（知识）交给你正在用的每一个 Agent。按成员权限取用，每次读写都记入审计，数据始终留在你自己的服务器上。',
    primaryCta: '连接你的 Agent',
    secondaryCta: '浏览技能库',
    badges: { selfHosted: '私有部署', openSource: '开源 · Apache-2.0', mcp: '标准 MCP 接入' },
    worksWith: '适用于',
    clients: ['Claude Code', 'Cursor', '自建 Agent', '任何支持 MCP 的客户端'],
  },

  console: {
    label: 'Agent 调用 SkillHive 的示意',
    windowTitle: 'agent · 已连接 skillhive',
    illustrative: '示意',
    scenarioLabel: '示例场景',
    scenarios: {
      report: {
        tab: '写周报',
        prompt: '按团队的周报写法，结合本周的迭代复盘，起草一份周报。',
        steps: [
          { tool: 'get_skill', detail: '@研发中心/weekly-report · v1.4.0' },
          { tool: 'search_knowledge', detail: '“迭代复盘 W41” · 命中 3 个文件' },
          { tool: 'read_document', detail: '迭代复盘-W41.md' },
        ],
        result: '已按团队模板起草周报 · 读取 3 项，均已记入审计',
      },
      incident: {
        tab: '排查故障',
        prompt: '订单服务 5xx 升高了，按我们的排障手册一步步查。',
        steps: [
          { tool: 'get_skill', detail: '@平台组/incident-triage · v2.1.0' },
          { tool: 'read_document', detail: '订单服务-运行手册.pdf' },
          { tool: 'read_document', detail: '事故复盘-0912.md' },
        ],
        result: '已按排障手册列出 6 步检查清单，并附上相似的历史事故',
      },
      onboarding: {
        tab: '新人入职',
        prompt: '我刚加入前端组，帮我快速熟悉团队的规范和常用做法。',
        steps: [
          { tool: 'list_knowledge_bases', detail: '前端组 · 3 个知识库' },
          { tool: 'search_skills', detail: '前端组可用技能 · 5 个' },
          { tool: 'read_document', detail: '前端代码规范.md' },
        ],
        result: '已整理入职清单：团队规范、常用技能与第一周任务',
      },
      writeBack: {
        tab: '写回资料',
        prompt: '把这次评审的结论整理成纪要，存进项目知识库。',
        steps: [
          { tool: 'browse_knowledge_base', detail: '项目资料 / 评审记录' },
          { tool: 'upload_document', detail: '评审纪要-1014.md · 新文件' },
        ],
        result: '已上传到授权的知识库 · 同名不覆盖，操作已记入审计',
      },
    },
  },

  problem: {
    eyebrow: '问题',
    title: 'Agent 很聪明，\n但它不认识你的团队。',
    description: '模型知道世界上的大多数事，却不知道你们的规范、模板，以及上周刚定下的结论。于是每次对话，都要从头交代一遍。',
    items: {
      paste: { title: '靠复制粘贴的上下文', description: '资料从网盘下载，再粘进对话框。换个工具、换个同事，就得重来一遍。' },
      scattered: { title: '散落各处的好做法', description: '好用的提示词和工作流躺在个人笔记和聊天记录里，没人知道哪一版最新、是否可靠。' },
      risk: { title: '不敢交出去的资料', description: '把内部文档传到第三方 AI 平台，安全和合规这一关很难过。' },
    },
  },

  layers: {
    eyebrow: '一个中心，三层上下文',
    title: '做法、资料和边界，\n一次交给 Agent。',
    description: 'SkillHive 部署在你自己的服务器上，位于团队和 Agent 之间：一侧汇集团队的技能与资料，另一侧通过 MCP 按权限交给 Agent。',
    flowLabel: 'SkillHive 连接团队与 Agent 的示意图',
    flow: {
      teamTitle: '你的团队',
      agentTitle: '你在用的 Agent',
      sources: { skills: '技能 · 做法', knowledge: '知识 · 资料', members: '成员与权限' },
      hubTitle: 'SkillHive',
      hubSubtitle: '你的服务器',
      gate: 'MCP · 按权限放行',
      agents: ['Claude Code', 'Cursor', '自建 Agent', '其他 MCP 客户端'],
    },
    items: {
      skills: {
        kicker: 'Skills · 怎么做',
        title: '团队技能库',
        description: '用通用的 SKILL.md 格式沉淀做法。上传后经过结构校验、安全扫描和人工审核才发布，Agent 拿到的都是可信版本。',
        points: ['版本可追溯', '安全扫描 + 审核', '不绑定任何 AI 工具'],
      },
      knowledge: {
        kicker: 'Knowledge · 用什么做',
        title: '团队知识库',
        description: '模板、规范、手册和复盘按团队与文件夹组织，上传即可用。Word、PPT、Excel、PDF 自动转为 Markdown 并生成摘要，Agent 搜得到、读得懂。',
        points: ['自动转 Markdown', 'AI 摘要与全文检索', '版本历史'],
      },
      guardrails: {
        kicker: 'Guardrails · 能碰什么',
        title: '可控的 Agent 权限',
        description: 'Token 按操作、团队空间和知识库分别授权。每次调用都重新校验成员与下载权限，读取和写入都有记录。',
        points: ['最小权限 Token', '逐次校验', '全程审计'],
      },
    },
    skillSample: {
      file: 'SKILL.md',
      name: 'weekly-report',
      description: '按团队模板起草周报',
      heading: '# 周报写法',
      body: ['1. 先读本周的迭代复盘', '2. 按「进展 / 风险 / 下周」组织', '3. 结论放在第一段'],
      badges: { scanned: '已扫描', reviewed: '已审核' },
    },
    knowledgeSample: {
      root: '研发中心 / 项目资料',
      files: [
        { name: '周报模板.docx', meta: 'v3' },
        { name: '迭代复盘-W41.md', meta: 'v1' },
        { name: '订单服务-运行手册.pdf', meta: 'v7' },
      ],
    },
    guardrailSample: {
      token: 'Token · 周报助手',
      columns: { read: '读取', upload: '上传', delete: '删除' },
      rows: { skills: '技能', knowledge: '知识' },
      scope: '范围：研发中心 · 2 个知识库',
      granted: '已授权',
      notGranted: '未授权',
    },
  },

  governance: {
    eyebrow: '治理',
    title: 'Agent 进团队，\n也要守团队的规矩。',
    description: '让 AI 用上内部资料，难点从来不是接入，而是放心。SkillHive 把权限、审核和留痕做在服务端，不依赖客户端自觉。',
    items: {
      scope: { title: '最小权限 Token', description: '读取、上传、删除分别授权，可限定到具体团队空间和知识库。默认只读。' },
      recheck: { title: '每次调用都重新校验', description: '撤销 Token、移出团队或关闭下载权限后，下一次调用立即按新权限执行。' },
      noBypass: { title: '管理员也不越界', description: '平台管理员的 Token 同样受团队成员身份和下载权限约束。' },
      audit: { title: '读写全程留痕', description: '记下是哪个 Token、在什么时间，读取或改动了哪个文件。' },
      safeWrite: { title: '写入可控、可回溯', description: '写入 Token 最长 30 天；同名不覆盖，重试不重复，删除只归档并保留历史。' },
      review: { title: '技能先审后用', description: '技能包经过结构校验、安全扫描和人工审核，通过后才会发布。' },
    },
    auditTitle: '审计记录',
    auditLive: '示意',
    audit: [
      { time: '10:42:07', action: 'read_document', target: '迭代复盘-W41.md', token: '周报助手' },
      { time: '10:42:05', action: 'search_knowledge', target: '“迭代复盘 W41”', token: '周报助手' },
      { time: '10:41:58', action: 'get_skill', target: '@研发中心/weekly-report', token: '周报助手' },
      { time: '10:31:18', action: 'upload_document', target: '评审纪要-1014.md', token: '项目助手' },
      { time: '10:28:44', action: 'read_skill_file', target: 'incident-triage/SKILL.md', token: '值班助手' },
      { time: '10:12:03', action: 'delete_document', target: '旧版接口说明.md · 已归档', token: '项目助手' },
    ],
  },

  compare: {
    eyebrow: '定位',
    title: '不是又一个网盘，\n也不是又一个问答机器人。',
    columns: {
      drive: {
        name: '在线文档与网盘',
        tag: '为人阅读而设计',
        points: ['协作编辑与分享很成熟', 'Agent 要用，只能下载后粘贴', '好做法没有统一的沉淀格式'],
      },
      rag: {
        name: 'RAG 知识库平台',
        tag: '在平台里另建一个机器人',
        points: ['需要切分、向量化和反复调优', '答案出自平台自己的机器人', '和团队日常用的 Agent 是两套东西'],
      },
      hive: {
        name: 'SkillHive',
        tag: '给你已在用的 Agent 提供上下文',
        points: ['技能、资料、权限，一次接入', '文件转为 Markdown，Agent 自己阅读和推理', '私有部署，数据不出你的服务器'],
      },
    },
    note: '继续用你习惯的文档工具协作；需要交给 Agent 的做法和资料，放进 SkillHive。',
  },

  library: {
    eyebrow: '技能库',
    title: '已经沉淀下来的做法，\nAgent 随时取用。',
    sortLabel: '技能排序方式',
    popular: '热门下载',
    recent: '最近发布',
    viewAll: '查看全部技能',
    loading: '正在加载技能…',
    loadError: '技能暂时无法加载，请稍后重试。',
    empty: '还没有可展示的技能。',
    noDescription: '暂无简介',
    versionPending: '版本待确认',
    downloads: '{{count}} 次下载',
  },

  connect: {
    eyebrow: '接入',
    title: '三步，让 Agent\n用上团队上下文。',
    steps: {
      token: { title: '创建 MCP Token', description: '选择需要的操作和允许访问的团队空间，可进一步限定知识库。默认只读。' },
      config: { title: '填入 Agent 配置', description: '把服务地址和 Token 填进 Agent 的 MCP 设置，远程接入无需额外部署。' },
      use: { title: '用自然语言提要求', description: '让 Agent 查找技能、读取资料，或在授权范围内上传和归档。' },
    },
    cta: '创建 MCP Token',
    codeTitle: 'MCP 配置',
    copy: '复制配置',
    copied: '已复制',
    tokenPlaceholder: '<你的 Token>',
    note: '支持 Streamable HTTP 和自定义请求头的 MCP 客户端可直接连接，也可以使用本地 stdio。',
  },

  closing: {
    title: '把团队的上下文，\n留在自己手里。',
    description: '开源，可私有部署。用 Docker Compose 部署在你的服务器或内网，文件可存放在本地磁盘、S3 或 MinIO。',
    source: '查看项目源码',
    connect: '连接你的 Agent',
  },
}

export const hiveLandingEn: typeof hiveLandingZh = {
  homeAria: 'SkillHive home',
  skillLibraryLabel: 'Skill library',
  knowledgeLabel: 'Knowledge base',
  knowledgeDescription: 'Templates, standards, runbooks and retrospectives organized by team and folder, available to members and agents within their permissions.',
  footerDescription: 'The private context center for your team’s agents.',

  hero: {
    eyebrow: 'The private context center for team agents',
    titleFirst: 'Every agent, ',
    titleSecond: 'fluent in your team.',
    description: 'Hand your team’s proven practices (skills) and working files (knowledge) to every agent you already use. Access follows member permissions, every read and write is audited, and your data stays on your own server.',
    primaryCta: 'Connect your agent',
    secondaryCta: 'Browse skills',
    badges: { selfHosted: 'Self-hosted', openSource: 'Open source · Apache-2.0', mcp: 'Standard MCP' },
    worksWith: 'Works with',
    clients: ['Claude Code', 'Cursor', 'Your own agents', 'Any MCP client'],
  },

  console: {
    label: 'Illustration of an agent calling SkillHive',
    windowTitle: 'agent · connected to skillhive',
    illustrative: 'Illustrative',
    scenarioLabel: 'Example scenarios',
    scenarios: {
      report: {
        tab: 'Weekly report',
        prompt: 'Draft this week’s report the way our team writes them, using the sprint retrospective.',
        steps: [
          { tool: 'get_skill', detail: '@engineering/weekly-report · v1.4.0' },
          { tool: 'search_knowledge', detail: '“sprint retro W41” · 3 files' },
          { tool: 'read_document', detail: 'sprint-retro-W41.md' },
        ],
        result: 'Report drafted from the team template · 3 reads, all audited',
      },
      incident: {
        tab: 'Incident',
        prompt: 'Order service 5xx errors are rising. Walk through our triage runbook.',
        steps: [
          { tool: 'get_skill', detail: '@platform/incident-triage · v2.1.0' },
          { tool: 'read_document', detail: 'order-service-runbook.pdf' },
          { tool: 'read_document', detail: 'postmortem-0912.md' },
        ],
        result: 'Six-step checklist from the runbook, with similar past incidents',
      },
      onboarding: {
        tab: 'Onboarding',
        prompt: 'I just joined the frontend team. Help me learn our standards and habits.',
        steps: [
          { tool: 'list_knowledge_bases', detail: 'frontend · 3 knowledge bases' },
          { tool: 'search_skills', detail: 'frontend skills · 5 available' },
          { tool: 'read_document', detail: 'frontend-code-style.md' },
        ],
        result: 'Onboarding list ready: standards, common skills and first-week tasks',
      },
      writeBack: {
        tab: 'Write back',
        prompt: 'Turn today’s review decisions into notes and save them to the project knowledge base.',
        steps: [
          { tool: 'browse_knowledge_base', detail: 'project-docs / reviews' },
          { tool: 'upload_document', detail: 'review-notes-1014.md · new file' },
        ],
        result: 'Uploaded to the granted knowledge base · no overwrites, audited',
      },
    },
  },

  problem: {
    eyebrow: 'The problem',
    title: 'Agents are brilliant. They just don’t know your team.',
    description: 'Models know most things about the world, but not your standards, your templates, or the decision you made last week. So every conversation starts from scratch.',
    items: {
      paste: { title: 'Copy-paste context', description: 'Download from the drive, paste into the chat. Switch tools or teammates and you do it all again.' },
      scattered: { title: 'Scattered good practice', description: 'The prompts and workflows that work live in personal notes and chat threads. Nobody knows which version is current or safe.' },
      risk: { title: 'Files you can’t hand over', description: 'Uploading internal documents to a third-party AI platform rarely survives a security or compliance review.' },
    },
  },

  layers: {
    eyebrow: 'One center, three layers of context',
    title: 'Practices, files and boundaries, delivered to agents together.',
    description: 'SkillHive runs on your own server, between your team and your agents. It gathers skills and files on one side and serves them to agents over MCP, within permissions, on the other.',
    flowLabel: 'Diagram of SkillHive connecting a team to its agents',
    flow: {
      teamTitle: 'Your team',
      agentTitle: 'Your agents',
      sources: { skills: 'Skills · practices', knowledge: 'Knowledge · files', members: 'Members & permissions' },
      hubTitle: 'SkillHive',
      hubSubtitle: 'Your server',
      gate: 'MCP · permission-checked',
      agents: ['Claude Code', 'Cursor', 'Your own agents', 'Other MCP clients'],
    },
    items: {
      skills: {
        kicker: 'Skills · how',
        title: 'Team skill library',
        description: 'Capture practices in the portable SKILL.md format. Uploads are validated, security-scanned and reviewed before publishing, so agents only get trusted versions.',
        points: ['Versioned', 'Scanned and reviewed', 'Works with any AI tool'],
      },
      knowledge: {
        kicker: 'Knowledge · with what',
        title: 'Team knowledge base',
        description: 'Templates, standards, runbooks and retrospectives organized by team and folder, usable as soon as they are uploaded. Word, PowerPoint, Excel and PDF become Markdown with a summary, so agents can find and read them.',
        points: ['Markdown conversion', 'AI summaries and full-text search', 'Version history'],
      },
      guardrails: {
        kicker: 'Guardrails · what it may touch',
        title: 'Controlled agent access',
        description: 'Tokens grant operations, team spaces and knowledge bases separately. Every call rechecks membership and download rights, and every read and write is recorded.',
        points: ['Least-privilege tokens', 'Checked every call', 'Fully audited'],
      },
    },
    skillSample: {
      file: 'SKILL.md',
      name: 'weekly-report',
      description: 'Draft the weekly report from the team template',
      heading: '# How we write weekly reports',
      body: ['1. Read this week’s retrospective', '2. Group by progress / risks / next', '3. Lead with the conclusion'],
      badges: { scanned: 'Scanned', reviewed: 'Reviewed' },
    },
    knowledgeSample: {
      root: 'engineering / project-docs',
      files: [
        { name: 'weekly-template.docx', meta: 'v3' },
        { name: 'sprint-retro-W41.md', meta: 'v1' },
        { name: 'order-service-runbook.pdf', meta: 'v7' },
      ],
    },
    guardrailSample: {
      token: 'Token · report assistant',
      columns: { read: 'Read', upload: 'Upload', delete: 'Delete' },
      rows: { skills: 'Skills', knowledge: 'Knowledge' },
      scope: 'Scope: engineering · 2 knowledge bases',
      granted: 'Granted',
      notGranted: 'Not granted',
    },
  },

  governance: {
    eyebrow: 'Governance',
    title: 'Agents join your team. They follow its rules.',
    description: 'Connecting AI to internal files is easy. Trusting it is the hard part. SkillHive enforces permissions, review and audit on the server instead of relying on clients to behave.',
    items: {
      scope: { title: 'Least-privilege tokens', description: 'Read, upload and delete are granted separately and can be limited to specific spaces and knowledge bases. Read-only by default.' },
      recheck: { title: 'Rechecked on every call', description: 'Revoke a token, remove a member or turn off downloads, and the next call follows the new permissions.' },
      noBypass: { title: 'No admin shortcuts', description: 'Platform administrator tokens are still bound by team membership and download rights.' },
      audit: { title: 'Every read and write traced', description: 'Records which token read or changed which file, and when.' },
      safeWrite: { title: 'Writes stay controlled', description: 'Write tokens last at most 30 days. No overwrites, no duplicate retries, and deletes only archive with history kept.' },
      review: { title: 'Skills reviewed first', description: 'Skill packages are validated, security-scanned and reviewed before they are published.' },
    },
    auditTitle: 'Audit log',
    auditLive: 'Illustrative',
    audit: [
      { time: '10:42:07', action: 'read_document', target: 'sprint-retro-W41.md', token: 'report assistant' },
      { time: '10:42:05', action: 'search_knowledge', target: '“sprint retro W41”', token: 'report assistant' },
      { time: '10:41:58', action: 'get_skill', target: '@engineering/weekly-report', token: 'report assistant' },
      { time: '10:31:18', action: 'upload_document', target: 'review-notes-1014.md', token: 'project assistant' },
      { time: '10:28:44', action: 'read_skill_file', target: 'incident-triage/SKILL.md', token: 'on-call assistant' },
      { time: '10:12:03', action: 'delete_document', target: 'old-api-notes.md · archived', token: 'project assistant' },
    ],
  },

  compare: {
    eyebrow: 'Positioning',
    title: 'Not another drive. Not another chatbot.',
    columns: {
      drive: {
        name: 'Online docs and drives',
        tag: 'Designed for people to read',
        points: ['Mature editing and sharing', 'Agents only get what you paste in', 'No common format for practices'],
      },
      rag: {
        name: 'RAG knowledge platforms',
        tag: 'A separate bot inside the platform',
        points: ['Chunking, embeddings and tuning', 'Answers come from the platform’s bot', 'Separate from the agents your team uses'],
      },
      hive: {
        name: 'SkillHive',
        tag: 'Context for the agents you already use',
        points: ['Skills, files and permissions in one connection', 'Files become Markdown for agents to reason over', 'Self-hosted; data stays on your server'],
      },
    },
    note: 'Keep collaborating in the docs tool you like. Put the practices and files your agents need into SkillHive.',
  },

  library: {
    eyebrow: 'Skill library',
    title: 'Practices your team has captured, ready for any agent.',
    sortLabel: 'Skill sort order',
    popular: 'Popular',
    recent: 'Recently published',
    viewAll: 'View all skills',
    loading: 'Loading skills…',
    loadError: 'Skills could not be loaded. Please try again later.',
    empty: 'No skills to show yet.',
    noDescription: 'No description yet',
    versionPending: 'Version pending',
    downloads: '{{count}} downloads',
  },

  connect: {
    eyebrow: 'Connect',
    title: 'Three steps to give agents your team’s context.',
    steps: {
      token: { title: 'Create an MCP token', description: 'Choose operations and allowed team spaces, and optionally restrict knowledge bases. Read-only by default.' },
      config: { title: 'Add it to your agent', description: 'Enter the server URL and token in your agent’s MCP settings. Remote access needs no extra deployment.' },
      use: { title: 'Ask in plain language', description: 'Have the agent find skills, read files, or upload and archive within its grants.' },
    },
    cta: 'Create an MCP token',
    codeTitle: 'MCP configuration',
    copy: 'Copy',
    copied: 'Copied',
    tokenPlaceholder: '<YOUR_TOKEN>',
    note: 'MCP clients that support Streamable HTTP and custom headers connect directly. Local stdio is also available.',
  },

  closing: {
    title: 'Keep your team’s context in your own hands.',
    description: 'Open source and self-hosted. Deploy with Docker Compose on your server or intranet, and store files on local disk, S3 or MinIO.',
    source: 'View source code',
    connect: 'Connect your agent',
  },
}
