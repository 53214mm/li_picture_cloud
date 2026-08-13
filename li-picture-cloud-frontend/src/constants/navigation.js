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
