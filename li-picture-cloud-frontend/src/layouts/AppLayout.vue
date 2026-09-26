<template>
  <div class="app-layout">
    <a class="skip-link" href="#workspace-main" :inert="legacyOverlay">跳到工作区</a>
    <aside class="sidebar" :inert="legacyOverlay">
      <router-link to="/space/my" class="brand" aria-label="LiPictureCloud 我的空间"><span class="brand-short">LP</span><span class="brand-full">LiPictureCloud</span></router-link>
      <div class="sidebar-navigation">
        <div class="desktop-navigation"><AppNavigation /></div>
        <div class="rail-navigation"><AppNavigation compact @expand="panel = 'navigation'" /></div>
      </div>
      <CompanionPresenceSlot v-if="companionEnabled" :active="route.meta.section === 'companion'" />
    </aside>
    <div class="app-content">
      <AppToolbar
        :inert="legacyOverlay"
        :title="route.meta.title"
        :section="route.meta.section"
        :navigation-open="panel === 'navigation'"
        :account-open="panel === 'account'"
        @navigation="panel = 'navigation'"
        @account="panel = 'account'"
      />
      <AppMain :workspace="route.meta.workspace" :frame="route.meta.frame"><slot /></AppMain>
      <SiteFooter :inert="legacyOverlay" />
    </div>
    <nav class="bottom-navigation" aria-label="快捷导航" :inert="legacyOverlay">
      <router-link to="/space/my" :aria-current="route.meta.section === 'spaces' ? 'page' : undefined"><ShellIcon name="spaces" /><span>空间</span></router-link>
      <router-link to="/gallery" :aria-current="route.meta.section === 'gallery' ? 'page' : undefined"><ShellIcon name="gallery" /><span>图库</span></router-link>
      <router-link v-if="companionEnabled" class="companion-tab" to="/companion" :aria-current="route.meta.section === 'companion' ? 'page' : undefined"><ShellIcon name="companion" /><span>伙伴</span></router-link>
      <button type="button" aria-haspopup="dialog" :aria-expanded="panel === 'navigation'" @click="panel = 'navigation'"><ShellIcon name="more" /><span>更多</span></button>
    </nav>
    <ShellDialog :open="panel === 'navigation'" title="导航" :return-focus="focusNavigation" @close="panel = null">
      <div @click="closeOnLink"><AppNavigation /></div>
      <div class="drawer-links" @click="closeOnLink">
        <CompanionPresenceSlot v-if="companionEnabled && panel === 'navigation'" :active="route.meta.section === 'companion'" />
        <router-link to="/gallery">图库搜索</router-link>
      </div>
    </ShellDialog>
    <ShellDialog :open="panel === 'account'" title="账户" side="right" @close="panel = null">
      <div class="account-summary">
        <span class="account-avatar" aria-hidden="true">{{ accountInitial }}</span>
        <div>
          <strong>{{ accountName }}</strong>
          <p v-if="accountIdentifier">账号：{{ accountIdentifier }}</p>
        </div>
      </div>
      <div class="account-actions">
        <LpButton class="logout-button" variant="quiet" :disabled="loggingOut" @click="logout">{{ loggingOut ? '正在退出…' : '退出登录' }}</LpButton>
      </div>
      <p v-if="logoutError" role="alert" class="logout-error">{{ logoutError }}</p>
    </ShellDialog>
  </div>
</template>
<script setup>
import { computed, ref, watch, onMounted, onBeforeUnmount } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { COMPANION_UI_ENABLED } from '@/config/features'
import AppNavigation from '@/components/shell/AppNavigation.vue'
import AppToolbar from '@/components/shell/AppToolbar.vue'
import AppMain from '@/components/shell/AppMain.vue'
import ShellDialog from '@/components/shell/ShellDialog.vue'
import CompanionPresenceSlot from '@/components/shell/CompanionPresenceSlot.vue'
import SiteFooter from '@/components/shell/SiteFooter.vue'
import ShellIcon from '@/components/shell/ShellIcon.vue'
import LpButton from '@/components/ui/LpButton.vue'
import { useLegacyOverlayIsolation } from '@/composables/useLegacyOverlayIsolation'
const user = useUserStore()
const route = useRoute()
const router = useRouter()
const companionEnabled = COMPANION_UI_ENABLED
const panel = ref(null)
const legacyOverlay = useLegacyOverlayIsolation()
const loggingOut = ref(false)
const logoutError = ref('')
const accountName = computed(() => user.currentUser?.userName || user.currentUser?.userAccount || '已登录')
const accountIdentifier = computed(() => {
  const account = user.currentUser?.userAccount
  return account && account !== accountName.value ? account : ''
})
const accountInitial = computed(() => Array.from(accountName.value)[0]?.toUpperCase() || '用')
watch(() => route.fullPath, () => { panel.value = null })
watch(legacyOverlay, active => { if (active) panel.value = null })
function closeOnLink(event) { if (event.target.closest('a[href]')) panel.value = null }
function focusNavigation() {
  const menu = document.querySelector('.app-toolbar .menu-button')
  if (menu?.getClientRects().length) menu.focus()
  else document.querySelector('.sidebar .brand')?.focus()
}
async function logout() {
  loggingOut.value = true
  logoutError.value = ''
  try {
    await user.logout()
    panel.value = null
    await router.replace('/login')
  } catch (error) {
    logoutError.value = error.message || '退出失败，请重试'
  } finally { loggingOut.value = false }
}
let breakpoints = []
function onBreakpoint() { if (panel.value === 'navigation') panel.value = null }
onMounted(() => {
  breakpoints = [768, 1024].map(width => window.matchMedia(`(min-width: ${width}px)`))
  breakpoints.forEach(query => query.addEventListener('change', onBreakpoint))
})
onBeforeUnmount(() => breakpoints.forEach(query => query.removeEventListener('change', onBreakpoint)))
</script>
<style scoped>
.app-layout { min-height: 100dvh; display: grid; grid-template-columns: 224px minmax(0, 1fr); }
.skip-link { position: fixed; top: -100px; left: 16px; z-index: var(--lp-z-toast); background: var(--lp-surface-elevated); padding: 12px; }
.skip-link:focus { top: 8px; }
.sidebar { position: sticky; top: 0; height: 100dvh; min-height: 0; z-index: var(--lp-z-sticky); display: flex; flex-direction: column; gap: 24px; padding: 24px 16px 16px; border-right: 1px solid var(--lp-border); background: var(--lp-surface); }
.brand { display: flex; align-items: center; min-height: 44px; padding-inline: 12px; font-size: 1rem; font-weight: 600; letter-spacing: -0.03em; }
.brand-short { display: none; }
.sidebar-navigation { flex: 1; min-height: 0; overflow-y: auto; }
.rail-navigation { display: none; }
.app-content { display: flex; flex-direction: column; min-width: 0; }
.bottom-navigation { display: none; }
.drawer-links { display: grid; gap: 12px; margin-top: 24px; }
.drawer-links > a { padding: 12px; min-height: 44px; font-size: 0.875rem; }
.account-summary { display: flex; align-items: center; gap: var(--lp-space-4); padding: var(--lp-space-3) 0 var(--lp-space-6); }
.account-avatar { display: grid; place-items: center; width: 48px; height: 48px; flex: 0 0 auto; border-radius: var(--lp-radius-full); color: var(--lp-on-accent); background: var(--lp-accent); font-weight: 600; }
.account-summary strong { display: block; overflow-wrap: anywhere; font-size: 1rem; }
.account-summary p { margin-top: var(--lp-space-1); color: var(--lp-text-secondary); font-size: 0.8125rem; overflow-wrap: anywhere; }
.account-actions { padding-top: var(--lp-space-4); border-top: 1px solid var(--lp-border); }
.logout-button { width: 100%; justify-content: flex-start; }
.logout-error { color: var(--lp-danger); margin-top: 16px; }
@media (768px <= width < 1024px) {
  .app-layout { grid-template-columns: 88px minmax(0, 1fr); }
  .sidebar { padding: 16px 8px; }
  .brand { justify-content: center; padding: 0; }
  .brand-short, .rail-navigation { display: block; }
  .brand-full, .desktop-navigation { display: none; }
}
@media (width < 768px) {
  .app-layout { display: block; padding-bottom: calc(64px + env(safe-area-inset-bottom)); }
  .app-content { min-height: calc(100dvh - 64px - env(safe-area-inset-bottom)); }
  .sidebar { display: none; }
  .bottom-navigation { display: flex; position: fixed; inset: auto 0 0; z-index: var(--lp-z-sticky); height: calc(64px + env(safe-area-inset-bottom)); padding: 8px max(12px, env(safe-area-inset-right)) max(8px, env(safe-area-inset-bottom)) max(12px, env(safe-area-inset-left)); border-top: 1px solid var(--lp-border); background: var(--lp-surface); }
  .bottom-navigation > * { flex: 1; display: flex; flex-direction: column; gap: 2px; align-items: center; justify-content: center; min-height: 44px; font-size: 0.75rem; color: var(--lp-text-secondary); border-radius: var(--lp-radius-s); transition: color var(--lp-dur-fast) var(--lp-ease-standard), background-color var(--lp-dur-fast) var(--lp-ease-standard); }
  .bottom-navigation .companion-tab { color: var(--lp-accent-strong); font-weight: 600; }
  .companion-tab .shell-icon { width: 30px; padding-inline: 5px; border-radius: var(--lp-radius-s); background: var(--lp-accent-soft); }
  .bottom-navigation [aria-current] { color: var(--lp-accent-strong); background: var(--lp-accent-soft); }
  /* Keep the keyboard/input area clear without changing any page form. */
  .app-layout:has(input:focus, textarea:focus, [contenteditable="true"]:focus) .bottom-navigation { display: none; }
}
</style>
