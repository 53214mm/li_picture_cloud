import { createRouter, createWebHistory } from 'vue-router'
import HomeView from '@/views/HomeView.vue'
import { useUserStore } from '@/stores/user'
import { COMPANION_UI_ENABLED } from '@/config/features'

const routes = [
  {
    path: '/',
    name: 'home',
    component: HomeView
  },
  {
    path: '/login',
    name: 'login',
    component: () => import('@/views/LoginView.vue')
  },
  {
    path: '/register',
    name: 'register',
    component: () => import('@/views/RegisterView.vue')
  },
  {
    path: '/gallery',
    name: 'gallery',
    component: () => import('@/views/GalleryView.vue')
  },
  {
    path: '/picture/:id',
    name: 'picture-detail',
    component: () => import('@/views/PictureDetailView.vue')
  },
  {
    path: '/upload',
    name: 'picture-upload',
    component: () => import('@/views/PictureUploadView.vue')
  },
  // ===== 空间管理 =====
  {
    path: '/spaces',
    name: 'spaces',
    component: () => import('@/views/SpaceManageView.vue')
  },
  {
    path: '/space/create',
    name: 'space-create',
    component: () => import('@/views/SpaceCreateView.vue')
  },
  {
    path: '/space/my',
    name: 'my-space',
    component: () => import('@/views/MySpaceView.vue')
  },
  {
    path: '/space/:id',
    name: 'space-detail',
    component: () => import('@/views/SpaceDetailView.vue')
  },
  {
    path: '/space/analyze',
    name: 'space-analyze',
    component: () => import('@/views/SpaceAnalyzeView.vue')
  },
  // ===== 管理员 =====
  {
    path: '/admin/pictures',
    name: 'admin-pictures',
    component: () => import('@/views/PictureManageView.vue')
  },
  {
    path: '/admin/users',
    name: 'admin-users',
    component: () => import('@/views/AdminUserView.vue')
  }
]

// 这是构建期开关：默认生产包没有伙伴路由/chunk，后端开关与它都开启时才形成完整入口。
if (COMPANION_UI_ENABLED) {
  routes.push({
    path: '/companion',
    name: 'companion',
    component: () => import('@/views/CompanionView.vue'),
    meta: { requiresAuth: true }
  })
}

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach(async to => {
  if (!to.meta.requiresAuth) return true
  const userStore = useUserStore()
  try {
    await userStore.ensureCurrentUser()
  } catch {
    // 临时网络失败不伪装成“未登录”；目标页会呈现可重试的认证状态。
    return true
  }
  if (userStore.isLoggedIn) return true
  return { name: 'login', query: { redirect: to.fullPath } }
})

export default router
