<template>
  <header class="app-toolbar">
    <button type="button" class="menu-button toolbar-button" aria-label="打开导航菜单" aria-haspopup="dialog" :aria-expanded="navigationOpen" @click="emit('navigation')">菜单</button>
    <nav class="location" aria-label="当前位置">
      <template v-for="(item, index) in breadcrumbs" :key="`${item.label}-${index}`">
        <span v-if="index" class="separator" aria-hidden="true">/</span>
        <router-link v-if="item.to && index < breadcrumbs.length - 1" :to="item.to">{{ item.label }}</router-link>
        <span v-else :aria-current="index === breadcrumbs.length - 1 ? 'page' : undefined">{{ item.label }}</span>
      </template>
    </nav>
    <div class="toolbar-actions" aria-label="全局操作">
      <router-link to="/gallery" class="search-link toolbar-button">
        <ShellIcon name="search" /><span class="action-label">图库搜索</span>
      </router-link>
      <router-link to="/upload" class="upload-link toolbar-button" aria-label="上传">
        <ShellIcon name="upload" /><span class="action-label">上传</span>
      </router-link>
      <button type="button" class="account-button toolbar-button" aria-haspopup="dialog" :aria-expanded="accountOpen" aria-label="打开账户菜单" @click="emit('account')">
        账户
      </button>
    </div>
  </header>
</template>
<script setup>
import { computed } from 'vue'
import { buildShellBreadcrumb } from '@/constants/shell'
import ShellIcon from '@/components/shell/ShellIcon.vue'
const props = defineProps({
  title: { type: String, default: '' },
  section: { type: String, default: '' },
  navigationOpen: Boolean,
  accountOpen: Boolean
})
const emit = defineEmits(['navigation', 'account'])
const breadcrumbs = computed(() => buildShellBreadcrumb({ section: props.section, title: props.title }))
</script>
<style scoped>
.app-toolbar { position: sticky; top: 0; z-index: var(--lp-z-sticky); min-height: 68px; display: flex; align-items: center; gap: var(--lp-space-4); padding: 8px var(--lp-gutter); background: var(--lp-bg); border-bottom: 1px solid var(--lp-border); }
.location { flex: 1; min-width: 0; display: flex; align-items: center; font-size: 0.8125rem; color: var(--lp-text-secondary); white-space: nowrap; overflow: hidden; }
.location > * { flex-shrink: 0; }
.location > [aria-current="page"] { overflow: hidden; text-overflow: ellipsis; color: var(--lp-text-primary); }
.separator { margin-inline: var(--lp-space-3); color: var(--lp-text-tertiary); }
.toolbar-actions { display: flex; align-items: center; flex-shrink: 0; gap: 2px; padding: 3px; }
.toolbar-button { min-height: 44px; display: inline-flex; align-items: center; justify-content: center; gap: var(--lp-space-2); padding: 9px 12px; border-radius: var(--lp-radius-s); font-size: 0.8125rem; color: var(--lp-text-secondary); transition: color var(--lp-dur-fast) var(--lp-ease-standard), background-color var(--lp-dur-fast) var(--lp-ease-standard), border-color var(--lp-dur-fast) var(--lp-ease-standard); }
.toolbar-button:hover { color: var(--lp-text-primary); background: var(--lp-bg-subtle); }
.account-button { position: relative; margin-left: 3px; padding-left: 14px; }
.account-button::before { content: ''; position: absolute; left: -3px; top: 9px; bottom: 9px; width: 1px; background: var(--lp-border); }
.menu-button { display: none; }
@media (width < 1024px) {
  .menu-button { display: inline-flex; }
  .location > a, .location > .separator { display: none; }
}
@media (width < 768px) {
  .app-toolbar { padding-inline: 16px; min-height: 60px; gap: var(--lp-space-2); }
  .search-link { display: none; }
  .account-button { padding-inline: 10px; }
}
@media (width < 480px) {
  .app-toolbar { padding-inline: 12px; gap: 4px; }
  .toolbar-actions { gap: 0; }
  .toolbar-button { padding-inline: 10px; }
  .upload-link .action-label { display: none; }
}
@media (prefers-reduced-motion: reduce) { .toolbar-button { transition: none; } }
</style>
