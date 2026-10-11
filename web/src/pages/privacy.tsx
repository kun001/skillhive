import { useTranslation } from 'react-i18next'
import { LegalDocument } from '@/shared/components/legal-document'

const privacyDocuments = {
  zh: {
    eyebrow: '法律与隐私',
    title: '隐私政策',
    summary: '本政策说明 SkillHive 在提供团队技能库、知识库、团队空间、账号登录、API Token 与 MCP 接入等服务时，如何收集、使用、共享并保护与你有关的信息。',
    lastUpdated: '最后更新：2026年10月11日',
    note: 'SkillHive 是私有部署软件，每个实例由部署它的组织（下称“运营方”）运营。运营方决定实例的配置、数据存放位置和保留期限，并对其实例中的数据负责；隐私相关的请求请优先联系你所在实例的运营方或管理员。',
    sections: [
      {
        title: '1. 适用范围',
        paragraphs: [
          '本政策适用于 SkillHive 的网站、Web 控制台、公共技能库页面、登录流程、API 以及本地 MCP 服务对实例的访问。',
          '当你浏览或发布技能、在知识库中上传和查看文件、参与审核、管理团队空间、创建 API Token 或通过 AI 工具访问实例时，本政策描述我们如何处理与你相关的信息。',
        ],
      },
      {
        title: '2. 我们收集的信息',
        paragraphs: [],
        bullets: [
          '账号与身份信息：用户名、显示名、邮箱、头像、第三方登录（OAuth / OIDC / 企业 SSO）标识、平台角色、团队空间成员关系，以及成员的编辑和下载权限。',
          '你提交的内容：技能包及其文件、版本说明与合规声明、知识库中的文件、文件夹、标题、描述和各个历史版本、团队空间资料、收藏、评分、订阅、举报和审核意见。',
          '凭证信息：API Token 的名称、前缀、作用域、创建、过期和最近使用时间。Token 明文只在创建时显示一次，实例只保存其哈希值。',
          '使用与安全信息：IP 地址、浏览器标识（User-Agent）、请求日志、登录事件、错误日志，以及审计日志。审计日志记录发布、审核、权限变更、知识库文件的上传、修改、下载与预览，以及通过 API Token 读取技能文件或下载技能包等操作，并注明来自网页会话还是哪个 Token。',
        ],
      },
      {
        title: '3. 我们如何使用信息',
        paragraphs: [],
        bullets: [
          '提供登录、会话管理、团队空间与成员级权限控制、API Token 鉴权和账号安全。',
          '展示和分发技能、托管知识库文件并提供在线预览、版本历史与搜索（知识库搜索只匹配标题和描述，不解析文件内容）。',
          '对技能包执行结构校验和安全扫描，支持人工审核、举报处理、下架与隐藏等治理流程。',
          '记录审计日志，用于安全调查、合规审计和追溯谁在何时访问或修改了哪些内容。',
          '发送与审核、团队协作、账号安全（如密码重置验证码）相关的站内或邮件通知。',
          '进行限流、风控、故障排查和性能分析。',
        ],
      },
      {
        title: '4. 信息的可见范围与共享',
        paragraphs: [
          '团队空间内的技能和知识库文件只对有权限的成员可见；文件的预览和下载还受成员下载权限控制。你在公共技能库中公开发布的技能、说明和部分个人资料（如显示名）可能被其他用户或匿名访客看到。',
          '运营方可能把数据交给其选择的基础设施处理，例如对象存储（本地磁盘、S3 或 MinIO）、邮件服务和日志或监控系统。若运营方为安全扫描启用了大模型分析，技能包内容会发送给其配置的大模型服务商。',
          '当你使用自己的 API Token 让 AI 工具（例如通过 MCP 服务）读取内容时，这些内容会传给你选择的 AI 工具及其服务商，由其按各自的政策处理。请只在你信任的工具中使用 Token。',
          '我们不出售你的个人信息。除提供服务所必需、遵守法律要求或保护实例和用户安全外，不向其他第三方共享。',
        ],
      },
      {
        title: '5. 数据保留',
        paragraphs: [
          '账号资料、技能及其版本、知识库文件及其历史版本、审核记录和审计日志会在实现业务目的所需的期限内保留，具体期限由运营方决定，并可能因法律、审计或合规要求延长。',
          '删除知识库文件为归档处理，相关版本和对象可能继续保留；下架或删除技能后，备份、副本或日志中的相关信息也可能在合理周期内继续存在。被吊销的 API Token 立即失效，但相关审计记录会保留。',
        ],
      },
      {
        title: '6. 你的选择与权利',
        paragraphs: [],
        bullets: [
          '你可以更新个人资料、修改密码，查看、修改过期时间或吊销自己的 API Token。',
          '创建 Token 时可以只授予需要的访问范围（技能库只读或读写、知识库只读）；用于 AI 工具时建议使用只读 Token。',
          '你可以联系实例运营方或管理员申请查阅、导出、更正或删除与你有关的信息；部分记录（如审计日志）可能因安全、审计或合规义务无法立即删除。',
        ],
      },
      {
        title: '7. 安全措施',
        paragraphs: [
          'SkillHive 提供团队空间与成员级访问控制、按作用域限制的 API Token（只保存哈希值）、技能包安全扫描、传输加密支持和审计日志等措施。实例的实际安全水平还取决于运营方的部署与配置。',
          '任何互联网服务都无法保证绝对安全。请妥善保管账号凭据和 API Token，发现异常登录、Token 泄露或滥用时及时吊销并通知运营方。',
        ],
      },
      {
        title: '8. 政策更新与联系我们',
        paragraphs: [
          '我们可能随产品能力、法律要求或运营方式的变化更新本政策，新版本会在站内发布并以页面标注的更新时间为准。运营方也可以在本政策之外补充其内部数据治理要求。',
          '如需联系，请通过当前实例提供的管理员联系方式或支持渠道与运营方联系。',
        ],
      },
    ],
  },
  en: {
    eyebrow: 'Legal',
    title: 'Privacy Policy',
    summary: 'This policy explains how SkillHive collects, uses, shares, and protects information when providing team skill libraries, knowledge bases, team spaces, sign-in, API tokens, and MCP access.',
    lastUpdated: 'Last updated: October 11, 2026',
    note: 'SkillHive is self-hosted software. Each instance is operated by the organization that deploys it (the "operator"), which decides the instance configuration, where data is stored and how long it is kept, and is responsible for the data in its instance. Please send privacy requests to your instance operator or administrator first.',
    sections: [
      {
        title: '1. Scope',
        paragraphs: [
          'This policy applies to the SkillHive website, web console, public skill library pages, sign-in flows, APIs, and access to the instance through the local MCP server.',
          'When you browse or publish skills, upload or view files in a knowledge base, take part in reviews, manage team spaces, create API tokens, or access the instance through AI tools, this policy describes how information related to you is handled.',
        ],
      },
      {
        title: '2. Information We Collect',
        paragraphs: [],
        bullets: [
          'Account and identity information: username, display name, email, avatar, third-party sign-in (OAuth / OIDC / enterprise SSO) identifiers, platform roles, team space membership, and each member\'s edit and download permissions.',
          'Content you submit: skill packages and their files, release notes and compliance declarations, knowledge base files, folders, titles, descriptions and every file version, team space profiles, stars, ratings, subscriptions, reports, and review comments.',
          'Credential information: API token names, prefixes, scopes, and creation, expiry, and last-used times. The token value is shown only once, when it is created; the instance stores only its hash.',
          'Usage and security information: IP address, browser identifier (User-Agent), request logs, sign-in events, error logs, and audit logs. Audit logs record publishing, reviews, permission changes, knowledge file uploads, edits, downloads and previews, and skill file reads or package downloads made with API tokens, noting whether each came from a web session or which token.',
        ],
      },
      {
        title: '3. How We Use Information',
        paragraphs: [],
        bullets: [
          'To provide sign-in, session management, team space and per-member access control, API token authentication, and account security.',
          'To display and distribute skills and to host knowledge base files with preview, version history, and search (knowledge search matches titles and descriptions only and does not parse file contents).',
          'To validate and security-scan skill packages and to support human review, reports, hiding, and takedowns.',
          'To keep audit logs for security investigations, compliance audits, and tracing who accessed or changed what and when.',
          'To send in-app or email notifications about reviews, team collaboration, and account security (such as password reset codes).',
          'To perform rate limiting, abuse prevention, debugging, and performance analysis.',
        ],
      },
      {
        title: '4. Visibility and Sharing',
        paragraphs: [
          'Skills and knowledge files in a team space are visible only to permitted members, and previews and downloads also depend on each member\'s download permission. Skills you publish to the public library, their descriptions, and some profile information (such as your display name) may be visible to other users or anonymous visitors.',
          'The operator may have data processed by infrastructure it chooses, such as object storage (local disk, S3, or MinIO), email services, and logging or monitoring systems. If the operator enables LLM analysis for security scanning, skill package contents are sent to the model provider it configures.',
          'When you let an AI tool read content with your own API token (for example through the MCP server), that content is passed to the AI tool you chose and its provider, which handle it under their own policies. Only use your tokens with tools you trust.',
          'We do not sell your personal information. We share it only as needed to provide the service, to comply with law, or to protect the instance and its users.',
        ],
      },
      {
        title: '5. Data Retention',
        paragraphs: [
          'Account information, skills and their versions, knowledge files and their history, review records, and audit logs are kept for as long as needed for their purpose. The operator decides the retention periods, which may be extended for legal, audit, or compliance reasons.',
          'Deleting a knowledge file archives it, and its versions and stored objects may be retained. After a skill is hidden or deleted, residual copies in backups or logs may persist for a reasonable period. Revoked API tokens stop working immediately, but related audit records remain.',
        ],
      },
      {
        title: '6. Your Choices and Rights',
        paragraphs: [],
        bullets: [
          'You can update your profile, change your password, and view, change the expiry of, or revoke your API tokens.',
          'When creating a token you can grant only the access you need (skills read-only or read-write, knowledge read-only); use read-only tokens for AI tools.',
          'You can ask your instance operator or administrator to access, export, correct, or delete information related to you; some records, such as audit logs, may need to be kept for security, audit, or compliance reasons.',
        ],
      },
      {
        title: '7. Security Measures',
        paragraphs: [
          'SkillHive provides team space and per-member access control, scope-limited API tokens stored only as hashes, security scanning of skill packages, support for encrypted transport, and audit logs. The actual security of an instance also depends on how the operator deploys and configures it.',
          'No internet service can guarantee absolute security. Protect your credentials and API tokens, and revoke them and notify the operator promptly if you notice suspicious sign-ins, token leaks, or misuse.',
        ],
      },
      {
        title: '8. Policy Updates and Contact',
        paragraphs: [
          'We may update this policy as the product, legal requirements, or operating model change. The latest version is posted in the service and identified by its update date. Operators may add their own data governance requirements on top of this policy.',
          'To get in touch, use the administrator contact or support channel provided by your instance operator.',
        ],
      },
    ],
  },
} as const

export function PrivacyPolicyPage() {
  const { i18n } = useTranslation()
  const language = i18n.resolvedLanguage?.split('-')[0] === 'zh' ? 'zh' : 'en'

  return <LegalDocument {...privacyDocuments[language]} />
}
