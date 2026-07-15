import { createRouter, createWebHistory } from 'vue-router'
import HomeView from '@/views/HomeView.vue'

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

const router = createRouter({
  history: createWebHistory(),
  routes
})

export default router
