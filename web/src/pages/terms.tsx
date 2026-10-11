import { useTranslation } from 'react-i18next'
import { LegalDocument } from '@/shared/components/legal-document'

const termsDocuments = {
  zh: {
    eyebrow: '法律与政策',
    title: '服务条款',
    summary: '本条款适用于你访问和使用 SkillHive 提供的团队技能库、知识库、团队空间、审核治理、账号管理、API Token 与 MCP 接入等服务。使用本服务即表示你同意受本条款约束。',
    lastUpdated: '最后更新：2026年10月11日',
    note: 'SkillHive 是私有部署软件，每个实例由部署它的组织（下称“运营方”）运营。运营方可以在本条款之外增加自己的内部规范、信息安全要求或可接受使用政策；两者冲突时，以运营方的规定为准。',
    sections: [
      {
        title: '1. 接受条款与适用范围',
        paragraphs: [
          '当你访问、注册或使用 SkillHive 时，即表示你同意本条款、隐私政策以及运营方的相关规定。',
          '如果你代表团队、公司或其他组织使用 SkillHive，你确认自己有权代表该主体接受这些条款。',
        ],
      },
      {
        title: '2. 账号、API Token 与访问安全',
        paragraphs: [],
        bullets: [
          '你应提供真实、准确的账号信息，并妥善保管自己的密码、登录会话和 API Token，不得与他人共用。',
          '你需对通过自己账号或 Token 发生的操作负责，包括你交给脚本或 AI 工具使用的 Token 所发起的访问。创建 Token 时请只授予需要的访问范围，用于 AI 工具时使用只读 Token。',
          '发现账号被盗用或 Token 泄露时，应立即修改密码或吊销 Token，并通知运营方。',
        ],
      },
      {
        title: '3. 你的内容',
        paragraphs: [
          '你保留对自己上传内容（技能包、知识库文件、说明、审核意见等）的权利，并授予运营方在提供服务所必需的范围内存储、复制、处理、预览、展示，以及按你设置的可见范围向有权限的成员分发这些内容的许可。',
          '你承诺有权上传这些内容，并对其合法性和准确性负责。',
        ],
        bullets: [
          '不得上传恶意代码、违法内容、侵权内容、欺诈性内容，或故意误导他人的技能和资料。',
          '不得在知识库中上传你无权在团队内共享的保密信息、个人信息或第三方资料。',
          '下载和再分发技能时，必须遵守该技能自身的许可证、第三方依赖的许可证和适用法律。',
        ],
      },
      {
        title: '4. 团队空间与权限',
        paragraphs: [
          '团队空间的所有者和管理员负责管理成员、角色以及成员的编辑和下载权限。你只能在被授予的范围内访问和使用团队内容。',
          '你不得规避访问控制，包括冒用他人身份、使用他人 Token、绕过下载权限获取文件，或未经授权访问其他团队的内容。',
        ],
      },
      {
        title: '5. 审核、治理与执行',
        paragraphs: [
          '技能在发布前可能经过结构校验、安全扫描和人工审核。运营方和管理员可以对内容执行审核、拒绝、隐藏、下架、撤回版本、限制访问或暂停账号等治理动作。',
          '发布、审核、权限变更、文件下载与预览、通过 Token 读取内容等操作会记入审计日志。发生滥用、侵权、安全风险或违反本条款的情况时，运营方可以保留相关记录并采取必要措施。',
        ],
      },
      {
        title: '6. API、MCP 与可接受使用',
        paragraphs: [],
        bullets: [
          '你不得干扰服务的稳定性，不得绕过鉴权、限流或安全策略，不得以破坏性方式抓取、压测或扫描实例。',
          '通过 API 或 MCP 服务读取的内容会交给你选择的脚本或 AI 工具处理，你需自行评估这些工具及其服务商的安全性与合规性，并确保这样使用符合团队的规定。',
          'SkillHive 不执行技能。技能由其提供者负责，你应在使用前自行评估其安全性、兼容性和适用性；安全扫描和审核不能保证技能绝对安全。',
        ],
      },
      {
        title: '7. 开源组件与知识产权',
        paragraphs: [
          'SkillHive 基于 Apache License 2.0 发布，衍生自开源项目 SkillHub，并包含受各自许可证约束的第三方开源组件。',
          '除非另有说明，SkillHive 的名称、标识、界面设计和站点内容归相应权利人所有，不因你使用服务而转让。',
        ],
      },
      {
        title: '8. 服务可用性与免责声明',
        paragraphs: [
          '运营方可以随时调整、限制或中断部分功能，不保证任何功能持续可用。',
          '在适用法律允许的范围内，SkillHive 按“现状”和“可用”提供，不对适销性、特定用途适用性、不中断、无错误或内容准确性作出明示或默示保证。',
        ],
      },
      {
        title: '9. 责任限制',
        paragraphs: [
          '在适用法律允许的最大范围内，SkillHive 的开发者和运营方不对任何间接、附带、特殊或后果性损害，以及利润、数据或商誉损失承担责任。',
          '因你的使用、内容或违规行为导致第三方提出索赔的，你同意在法律允许范围内承担相应责任并配合处理。',
        ],
      },
      {
        title: '10. 终止、变更与联系',
        paragraphs: [
          '你可以随时停止使用 SkillHive。运营方可以在你违反条款、造成安全风险或依法要求时暂停或终止你的访问，并吊销相关 Token。',
          '我们可能更新本条款，新版本会在站内发布并以页面标注的更新时间为准；更新后继续使用即表示你接受新条款。如有疑问，请联系当前实例的运营方或管理员。',
        ],
      },
    ],
  },
  en: {
    eyebrow: 'Legal',
    title: 'Terms of Service',
    summary: 'These terms apply when you access and use SkillHive services, including team skill libraries, knowledge bases, team spaces, review and governance, account management, API tokens, and MCP access. By using the service, you agree to these terms.',
    lastUpdated: 'Last updated: October 11, 2026',
    note: 'SkillHive is self-hosted software. Each instance is operated by the organization that deploys it (the "operator"). Operators may add their own internal rules, security requirements, or acceptable use policies; where they conflict with these terms, the operator\'s rules apply.',
    sections: [
      {
        title: '1. Acceptance and Scope',
        paragraphs: [
          'By accessing, registering for, or using SkillHive, you agree to these terms, the Privacy Policy, and any applicable rules set by the operator.',
          'If you use SkillHive on behalf of a team, company, or other organization, you confirm that you are authorized to accept these terms for that entity.',
        ],
      },
      {
        title: '2. Accounts, API Tokens, and Access Security',
        paragraphs: [],
        bullets: [
          'You must provide accurate account information and keep your password, sessions, and API tokens confidential; do not share them with others.',
          'You are responsible for activity under your account or tokens, including access made by scripts or AI tools you give a token to. Grant tokens only the access they need, and use read-only tokens for AI tools.',
          'If your account is compromised or a token leaks, change your password or revoke the token immediately and notify the operator.',
        ],
      },
      {
        title: '3. Your Content',
        paragraphs: [
          'You keep your rights in the content you upload (skill packages, knowledge files, descriptions, review comments, and so on), and you grant the operator a license to store, copy, process, preview, display, and distribute it to permitted members according to the visibility you choose, as needed to provide the service.',
          'You confirm that you have the right to upload the content and are responsible for its legality and accuracy.',
        ],
        bullets: [
          'Do not upload malicious code, unlawful, infringing, or fraudulent content, or skills and files that intentionally mislead others.',
          'Do not upload confidential information, personal data, or third-party material to a knowledge base that you are not allowed to share with the team.',
          'When downloading or redistributing skills, follow each skill\'s license, the licenses of its dependencies, and applicable law.',
        ],
      },
      {
        title: '4. Team Spaces and Permissions',
        paragraphs: [
          'Team space owners and admins manage members, roles, and each member\'s edit and download permissions. You may access and use team content only within the permissions you are granted.',
          'You must not circumvent access controls, including impersonating others, using someone else\'s token, bypassing download permissions to obtain files, or accessing other teams\' content without authorization.',
        ],
      },
      {
        title: '5. Review, Governance, and Enforcement',
        paragraphs: [
          'Skills may go through validation, security scanning, and human review before release. The operator and administrators may review, reject, hide, take down, or yank content, restrict access, or suspend accounts.',
          'Publishing, reviews, permission changes, file downloads and previews, and content reads through tokens are recorded in audit logs. In cases of abuse, infringement, security risk, or violations of these terms, the operator may retain records and take necessary action.',
        ],
      },
      {
        title: '6. APIs, MCP, and Acceptable Use',
        paragraphs: [],
        bullets: [
          'Do not disrupt the service, bypass authentication, rate limits, or security policies, or scrape, load-test, or scan the instance in a disruptive way.',
          'Content read through the API or the MCP server is handed to the scripts or AI tools you choose. You are responsible for assessing the security and compliance of those tools and their providers, and for making sure such use follows your team\'s rules.',
          'SkillHive does not run skills. Skills are the responsibility of their providers; evaluate their safety, compatibility, and fitness before use. Security scanning and review cannot guarantee that a skill is completely safe.',
        ],
      },
      {
        title: '7. Open Source and Intellectual Property',
        paragraphs: [
          'SkillHive is released under the Apache License 2.0, is derived from the open-source SkillHub project, and includes third-party open-source components governed by their own licenses.',
          'Unless stated otherwise, the SkillHive name, logo, interface design, and site content remain the property of their respective owners and are not transferred by your use of the service.',
        ],
      },
      {
        title: '8. Availability and Disclaimer',
        paragraphs: [
          'The operator may change, limit, or discontinue features at any time and does not guarantee that any feature will remain available.',
          'To the extent permitted by law, SkillHive is provided "as is" and "as available" without express or implied warranties of merchantability, fitness for a particular purpose, uninterrupted or error-free operation, or content accuracy.',
        ],
      },
      {
        title: '9. Limitation of Liability',
        paragraphs: [
          'To the maximum extent permitted by law, SkillHive\'s developers and operators are not liable for indirect, incidental, special, or consequential damages, or for loss of profits, data, or goodwill.',
          'If your use, content, or violations lead to third-party claims, you agree to bear the corresponding responsibility and cooperate in resolving them to the extent permitted by law.',
        ],
      },
      {
        title: '10. Termination, Changes, and Contact',
        paragraphs: [
          'You may stop using SkillHive at any time. The operator may suspend or terminate your access, and revoke your tokens, if you violate these terms, create a security risk, or as required by law.',
          'We may update these terms; the latest version is posted in the service and identified by its update date, and continuing to use the service means you accept it. For questions, contact your instance operator or administrator.',
        ],
      },
    ],
  },
} as const

export function TermsOfServicePage() {
  const { i18n } = useTranslation()
  const language = i18n.resolvedLanguage?.split('-')[0] === 'zh' ? 'zh' : 'en'

  return <LegalDocument {...termsDocuments[language]} />
}
