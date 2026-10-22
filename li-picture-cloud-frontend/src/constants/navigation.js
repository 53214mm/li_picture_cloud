export function buildPublicNavigation(isLoggedIn) {
  return [
    { label: '首页', to: '/' },
    { label: '图库', to: '/gallery' },
    ...(isLoggedIn
      ? [{ label: '进入空间', to: '/space/my' }]
      : [{ label: '登录', to: '/login' }, { label: '注册', to: '/register' }])
  ]
}

export function buildAppNavigation({ isAdmin, companionEnabled }) {
  return [
    { id: 'spaces', label: '空间', items: [
      { label: '我的空间', to: '/space/my' },
      { label: '空间管理', to: '/spaces' },
      { label: '空间分析', to: '/space/analyze' }
    ] },
    { id: 'gallery', label: '图库', to: '/gallery' },
    ...(companionEnabled ? [{ id: 'tools', label: '工具', items: [
      { label: '模型连接', to: '/model-gateway' }
    ] }] : []),
    ...(isAdmin ? [{ id: 'admin', label: '管理', items: [
      { label: '图片管理', to: '/admin/pictures' },
      { label: '用户管理', to: '/admin/users' }
    ] }] : [])
  ]
}

// Retained for legacy consumers until their navigation migrates.
export function buildNavigationGroups({ isLoggedIn, isAdmin, companionEnabled = true }) {
  const groups = [
    {
      id: 'browse',
      label: '浏览',
      items: [
        { label: '首页', to: '/' },
        { label: '探索图库', to: '/gallery' }
      ]
    }
  ]

  if (isLoggedIn) {
    groups.push({
      id: 'workspace',
      label: '工作空间',
      items: [
        { label: '上传图片', to: '/upload' },
        { label: '我的空间', to: '/space/my' },
        ...(companionEnabled ? [{ label: '我的伙伴', to: '/companion' }] : []),
        ...(companionEnabled ? [{ label: '模型控制中心', to: '/model-gateway' }] : []),
        { label: '空间管理', to: '/spaces' },
        { label: '图库分析', to: '/space/analyze' }
      ]
    })
  }

  if (isLoggedIn && isAdmin) {
    groups.push({
      id: 'admin',
      label: '管理',
      items: [
        { label: '图片审核', to: '/admin/pictures' },
        { label: '用户管理', to: '/admin/users' }
      ]
    })
  }

  groups.push({
    id: 'account',
    label: '账户',
    items: isLoggedIn
      ? [{ label: '退出登录', action: 'logout', danger: true }]
      : [{ label: '登录', to: '/login' }, { label: '注册', to: '/register' }]
  })

  return groups
}
