const publicPage = title => ({ title, layout: 'public', frame: 'legacy' })
const workspace = (title, section, width = 'wide', admin = false) => ({
  title, section, layout: 'app', workspace: width, frame: 'legacy',
  requiresAuth: true, requiresAdmin: admin
})

const shellSections = {
  spaces: { label: '空间', to: '/space/my' },
  gallery: { label: '图库', to: '/gallery' },
  tools: { label: '工具' },
  admin: { label: '管理' },
  companion: { label: '伙伴', to: '/companion' }
}

export function buildShellBreadcrumb(meta = {}) {
  const root = shellSections[meta.section]
  if (!root) return meta.title ? [{ label: meta.title }] : []
  if (!meta.title || meta.title === root.label) return [{ label: root.label }]
  return [
    { label: root.label, ...(root.to ? { to: root.to } : {}) },
    { label: meta.title }
  ]
}

// Width is an interface for later migrations; legacy pages retain their own containers.
export const shellMeta = {
  home: publicPage('首页'),
  login: publicPage('登录'),
  register: publicPage('注册'),
  gallery: { title: '图库', section: 'gallery', layout: 'adaptive', workspace: 'fluid', frame: 'legacy' },
  'picture-detail': { title: '图片详情', section: 'gallery', layout: 'adaptive', workspace: 'fluid', frame: 'legacy' },
  'picture-upload': workspace('上传图片', 'spaces', 'text'),
  spaces: workspace('空间管理', 'spaces'),
  'space-create': workspace('创建空间', 'spaces', 'text'),
  'my-space': workspace('我的空间', 'spaces'),
  'space-detail': workspace('空间详情', 'spaces', 'fluid'),
  'space-analyze': workspace('空间分析', 'spaces'),
  companion: workspace('伙伴', 'companion', 'text'),
  'model-gateway': workspace('模型连接', 'tools'),
  'recipe-workshop': workspace('配方', 'tools'),
  'admin-pictures': workspace('图片管理', 'admin', 'wide', true),
  'admin-users': workspace('用户管理', 'admin', 'wide', true),
  'admin-companion-feed-runs': workspace('喂养日志', 'admin', 'wide', true),
  'admin-companion-feed-run-detail': workspace('喂养日志详情', 'admin', 'wide', true)
}
