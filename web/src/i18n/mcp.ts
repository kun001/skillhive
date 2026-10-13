export const mcpZh = {
  title: '连接 Agent（MCP）',
  hint: '在支持 Streamable HTTP 的 Agent 中填写下面的地址，并使用你自己的只读 API Token。技能库选择“只读”或“不授予”；知识库权限按需开启。',
  permissions: '每次调用都校验团队成员身份、文件可见性和下载权限。Token 到期、撤销或权限变更立即影响后续调用。',
  endpoint: '服务地址',
  copy: '复制 MCP 配置',
  copied: 'MCP 配置已复制',
  placeholder: '替换为你的只读 API Token',
  readOnlyRequired: '此 Token 包含写入权限，无法用于 MCP。请新建只读 Token。',
}
export const mcpEn = {
  title: 'Connect your agent (MCP)',
  hint: 'Use the URL below in an agent supporting Streamable HTTP, with your own read-only API token. Set skill access to Read-only or None; enable knowledge access as needed.',
  permissions: 'Every call checks team membership, visibility and download permission. Expiration, revocation and permission changes apply to subsequent calls.',
  endpoint: 'Server URL',
  copy: 'Copy MCP configuration',
  copied: 'MCP configuration copied',
  placeholder: 'Replace with your read-only API token',
  readOnlyRequired: 'This token has write permissions and cannot be used with MCP. Create a read-only token.',
}
