<template>
  <component :is="layout" v-if="ready" id="app-root">
    <router-view v-if="access === 'allow'" v-slot="{ Component }">
      <Transition name="shell-page" :css="layout === AppLayout">
        <component :is="Component" />
      </Transition>
    </router-view>
    <div v-else class="shell-status">
      <LpStateBlock :status="stateStatus" :title="stateTitle" :message="stateMessage">
        <template v-if="access === 'auth-error' || access === 'forbidden'" #action>
          <LpButton v-if="access === 'auth-error'" :disabled="retrying" @click="retryAuth">重试</LpButton>
          <router-link v-else to="/space/my">返回我的空间</router-link>
        </template>
      </LpStateBlock>
    </div>
  </component>
  <div v-else class="shell-status"><LpStateBlock status="loading" title="正在确认登录状态" /></div>
</template>
<script setup>
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { routeAccess } from '@/utils/shellAccess'
import PublicLayout from '@/layouts/PublicLayout.vue'
import AppLayout from '@/layouts/AppLayout.vue'
import LpStateBlock from '@/components/ui/LpStateBlock.vue'
import LpButton from '@/components/ui/LpButton.vue'
const user = useUserStore()
const route = useRoute()
const router = useRouter()
const ready = ref(false)
const retrying = ref(false)
router.isReady().then(() => { ready.value = true })
user.ensureCurrentUser().catch(() => {})
const access = computed(() => retrying.value && route.meta.requiresAuth ? 'loading' : routeAccess(route.meta, user))
const layout = computed(() => {
  if (route.meta.requiresAuth && !user.authReady) return 'div'
  return route.meta.layout !== 'public' && user.authReady && user.isLoggedIn ? AppLayout : PublicLayout
})
const stateStatus = computed(() => ['auth-error', 'forbidden'].includes(access.value) ? 'error' : 'loading')
const stateTitle = computed(() => ({
  'auth-error': '无法确认登录状态',
  forbidden: '无权访问此页面',
  login: '正在前往登录',
  loading: '正在确认登录状态'
}[access.value]))
const stateMessage = computed(() => access.value === 'auth-error' ? '连接暂时不可用，请重试。' : access.value === 'forbidden' ? '此页面仅管理员可访问。' : '')
watch(access, value => {
  if (value === 'login') router.replace({ name: 'login', query: { redirect: route.fullPath } })
})
async function retryAuth() {
  retrying.value = true
  try { await user.ensureCurrentUser() } catch { /* The store retains the retryable error. */ }
  finally { retrying.value = false }
}
</script>
<style scoped>
.shell-status { padding: var(--lp-gutter); min-height: 40vh; display: grid; align-items: center; }
.shell-status a { text-decoration: underline; }
/* Enter-only fade: no outgoing-page delay or remount on query changes.
   Do not translate/scale legacy pages containing fixed overlays. */
.shell-page-enter-active { transition: opacity var(--lp-dur-base) var(--lp-ease-standard); }
.shell-page-enter-from { opacity: 0.65; }
@media (prefers-reduced-motion: reduce) { .shell-page-enter-active { transition: none; } }
</style>
