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
