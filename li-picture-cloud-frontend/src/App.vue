<template>
  <div id="app-root">
    <NavBar />
    <main>
      <router-view v-slot="{ Component }">
        <transition name="page" mode="out-in">
          <component :is="Component" />
        </transition>
      </router-view>
    </main>
    <footer class="site-footer">
      <a href="https://beian.miit.gov.cn/" target="_blank" rel="noopener noreferrer">
        湘ICP备2026039576号
      </a>
    </footer>
  </div>
</template>

<script setup>
import NavBar from '@/components/NavBar.vue'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()
userStore.ensureCurrentUser().catch(() => {})
</script>

<style scoped>
#app-root { min-height: 100vh; display: flex; flex-direction: column; }
main { flex: 1; }
.site-footer { padding: 1rem; text-align: center; font-size: 0.875rem; color: var(--gray-600); }
.site-footer a:hover { color: var(--red); text-decoration: underline; }
</style>
