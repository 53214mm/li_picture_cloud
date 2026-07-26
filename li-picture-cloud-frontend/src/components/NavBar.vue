<template>
  <nav class="navbar">
    <div class="container nav-inner">
      <router-link to="/" class="logo">
        <span class="logo-mark">●</span> LiPictureCloud
      </router-link>
      <div class="nav-links">
        <router-link to="/">首页</router-link>
        <router-link to="/gallery">探索</router-link>
        <template v-if="userStore.isLoggedIn">
          <router-link to="/upload">上传</router-link>
          <router-link to="/space/my">我的空间</router-link>
          <router-link to="/spaces">空间管理</router-link>
          <router-link to="/space/analyze">图库分析</router-link>
          <template v-if="userStore.isAdmin">
            <router-link to="/admin/pictures">审核</router-link>
            <router-link to="/admin/users">用户</router-link>
          </template>
          <span class="nav-user">{{ userStore.currentUser?.userName }}</span>
          <button class="nav-btn" @click="handleLogout">登出</button>
        </template>
        <template v-else>
          <router-link to="/login" class="btn btn-outline btn-sm">登录</router-link>
          <router-link to="/register" class="btn btn-primary btn-sm">注册</router-link>
        </template>
      </div>
    </div>
  </nav>
</template>

<script setup>
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const userStore = useUserStore()

async function handleLogout() {
  await userStore.logout()
  router.push('/')
}
</script>

<style scoped>
.navbar {
  position: sticky; top: 0; z-index: 100;
  background: var(--white);
  border-bottom: 2px solid var(--black);
}
.nav-inner {
  display: flex; align-items: center; justify-content: space-between;
  height: 4rem;
}
.logo {
  font-size: 1.25rem; font-weight: 700; letter-spacing: -0.02em;
  display: flex; align-items: center; gap: 0.5rem;
}
.logo-mark { color: var(--red); font-size: 0.625rem; }
.nav-links { display: flex; align-items: center; gap: 1.5rem; font-size: 0.875rem; font-weight: 500; }
.nav-links a:hover { color: var(--red); }
.nav-user { color: var(--gray-600); }
.nav-btn { font-size: 0.875rem; font-weight: 500; color: var(--gray-600); }
.nav-btn:hover { color: var(--red); }
.btn-sm { padding: 0.5rem 1.25rem; font-size: 0.75rem; }
</style>
