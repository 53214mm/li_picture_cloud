<template>
  <header class="public-navbar">
    <router-link to="/" class="brand">LiPictureCloud</router-link>
    <nav aria-label="官网导航">
      <router-link v-for="item in items" :key="item.to" :to="item.to">{{ item.label }}</router-link>
    </nav>
  </header>
</template>
<script setup>
import { computed } from 'vue'
import { useUserStore } from '@/stores/user'
import { buildPublicNavigation } from '@/constants/navigation'
const user = useUserStore()
const items = computed(() => buildPublicNavigation(user.isLoggedIn))
</script>
<style scoped>
.public-navbar { position: sticky; top: 0; z-index: var(--lp-z-sticky); display: flex; flex-wrap: wrap; justify-content: space-between; align-items: center; gap: 4px 24px; padding: 12px var(--lp-gutter); border-bottom: 1px solid var(--lp-border); background: var(--lp-surface); }
.brand { display: inline-flex; align-items: center; min-height: 44px; font-weight: 600; letter-spacing: -0.03em; }
nav { display: flex; flex-wrap: wrap; gap: 4px; }
nav a { display: inline-flex; align-items: center; min-height: 44px; padding: 8px 12px; font-size: 0.8125rem; color: var(--lp-text-secondary); border-radius: var(--lp-radius-s); }
nav a:hover, nav a.router-link-exact-active { color: var(--lp-accent-strong); background: var(--lp-accent-soft); }
@media (width < 768px) { .public-navbar { padding: 8px 16px; } }
@media (width < 480px) { .public-navbar { padding-inline: 12px; } nav { width: 100%; } nav a { flex: 1; justify-content: center; } }
</style>
