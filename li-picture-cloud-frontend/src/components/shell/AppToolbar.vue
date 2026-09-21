<template>
  <header class="app-toolbar">
    <button type="button" class="menu-button toolbar-button" aria-label="打开导航菜单" aria-haspopup="dialog" :aria-expanded="navigationOpen" @click="emit('navigation')">菜单</button>
    <nav class="location" aria-label="当前位置">
      <router-link to="/space/my" class="location-home">空间</router-link>
      <span v-if="title" aria-current="page"><span class="separator" aria-hidden="true">/</span>{{ title }}</span>
    </nav>
    <div class="toolbar-actions">
      <router-link to="/gallery" class="search-link toolbar-button">图库搜索</router-link>
      <router-link to="/upload" class="upload-link toolbar-button">上传</router-link>
      <button type="button" class="account-button toolbar-button" aria-haspopup="dialog" :aria-expanded="accountOpen" aria-label="打开账户菜单" @click="emit('account')">账户</button>
    </div>
  </header>
</template>
<script setup>
defineProps({ title: { type: String, default: '' }, navigationOpen: Boolean, accountOpen: Boolean })
const emit = defineEmits(['navigation', 'account'])
</script>
<style scoped>
.app-toolbar { position: sticky; top: 0; z-index: var(--lp-z-sticky); min-height: 68px; display: flex; align-items: center; gap: var(--lp-space-3); padding: 10px var(--lp-gutter); background: var(--lp-bg); border-bottom: 1px solid var(--lp-border); }
.location { flex: 1; min-width: 0; display: flex; align-items: center; font-size: 0.8125rem; color: var(--lp-text-secondary); }
.location > span { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.location-home { flex-shrink: 0; }
.separator { margin-inline: 12px; color: var(--lp-text-tertiary); }
.toolbar-actions { display: flex; gap: var(--lp-space-2); align-items: center; flex-shrink: 0; }
.toolbar-button { min-height: 44px; display: inline-flex; align-items: center; justify-content: center; padding: 10px 12px; border-radius: var(--lp-radius-s); font-size: 0.8125rem; color: var(--lp-text-secondary); }
.toolbar-button:hover { background: var(--lp-bg-subtle); }
.upload-link { background: var(--lp-accent); color: var(--lp-on-accent); }
.upload-link:hover { background: var(--lp-accent-strong); }
.menu-button { display: none; }
@media (width < 1024px) { .menu-button { display: inline-flex; } .location-home, .separator { display: none; } }
@media (width < 768px) { .app-toolbar { padding-inline: 16px; min-height: 60px; } .search-link { display: none; } }
@media (width < 480px) { .app-toolbar { padding-inline: 12px; gap: 4px; } .toolbar-actions { gap: 4px; } .toolbar-button { padding-inline: 10px; } }
</style>
