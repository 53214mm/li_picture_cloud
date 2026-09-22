export function getLandingPresentation({ authReady, isLoggedIn, companionEnabled }) {
  if (!authReady) {
    return {
      primary: { label: '浏览图库', to: '/gallery' },
      secondary: null,
      companion: null,
      companionAvailability: '正在确认登录状态。图片和空间功能仍可浏览。'
    }
  }

  const primary = isLoggedIn
    ? { label: '进入空间', to: '/space/my' }
    : { label: '注册', to: '/register' }
  const secondary = isLoggedIn
    ? { label: '图库', to: '/gallery' }
    : { label: '登录', to: '/login' }
  const companion = !companionEnabled
    ? null
    : isLoggedIn
      ? { label: '进入伙伴', to: '/companion' }
      : { label: '登录后查看伙伴', to: { path: '/login', query: { redirect: '/companion' } } }

  return {
    primary,
    secondary,
    companion,
    companionAvailability: companionEnabled
      ? null
      : '伙伴功能暂未开放。你可以先使用图片和空间功能。'
  }
}
