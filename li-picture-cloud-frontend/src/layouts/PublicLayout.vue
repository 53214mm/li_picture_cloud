<template>
  <div class="public-layout">
    <a class="skip-link" href="#workspace-main">跳到内容</a>
    <NavBar />
    <main id="workspace-main" tabindex="-1"><slot /></main>
    <SiteFooter />
  </div>
</template>
<script setup>
import NavBar from '@/components/NavBar.vue'
import SiteFooter from '@/components/shell/SiteFooter.vue'
</script>
<style scoped>
.public-layout { min-height: 100dvh; display: flex; flex-direction: column; }
main { flex: 1; min-width: 0; }
.skip-link { position: fixed; top: -100px; left: 16px; z-index: var(--lp-z-toast); padding: 12px; background: var(--lp-surface-elevated); }
.skip-link:focus { top: 8px; }
</style>
