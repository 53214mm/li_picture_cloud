<template>
  <div class="quick-chat" data-testid="companion-quick-chat">
    <p v-if="loading" role="status">正在读取伙伴状态…</p>
    <div v-else-if="error" class="quick-chat-state" role="alert">
      <p>{{ error }}</p>
      <button type="button" class="btn btn-outline" @click="load">重试</button>
    </div>
    <CompanionChatPanel v-else-if="home?.companion" compact :presentation="presentation" :chat-policy="home.chatPolicy" />
    <p v-else>还没有唤醒伙伴。可以先到伙伴空间看看。</p>
  </div>
</template>
<script setup>
import { computed, onBeforeUnmount, onMounted, ref, shallowRef } from 'vue'
import { useUserStore } from '@/stores/user'
import { useCompanionChatStore } from '@/stores/companionChat'
import { getCompanionHome } from '@/api/companion'
import { mapCompanionPresentation } from '@/presentation/companionPresentation'
import CompanionChatPanel from './CompanionChatPanel.vue'

// This component exists only in an explicitly opened interaction drawer. It
// never acquires/replaces the Home observation lease or evaluates proposals.
const user = useUserStore()
const chat = useCompanionChatStore()
const actor = String(user.currentUser?.id)
const home = shallowRef(null)
const loading = ref(true)
const error = ref('')
let active = true
let generation = 0
let controller
const current = cycle => active && cycle === generation && actor === String(user.currentUser?.id)
const presentation = computed(() => mapCompanionPresentation({
  home: home.value,
  homeStatus: loading.value ? 'loading' : error.value ? 'error' : 'ready',
  chat: { phase: chat.state.phase, error: Boolean(chat.state.sendError || chat.state.loadError) }
}))
async function load() {
  const cycle = ++generation
  controller?.abort()
  controller = new AbortController()
  loading.value = true
  error.value = ''
  try {
    const snapshot = await getCompanionHome({ signal: controller.signal })
    if (!current(cycle)) return
    if (!['ready', 'absent'].includes(mapCompanionPresentation({ home: snapshot, homeStatus: 'ready' }).availability)) throw new Error('伙伴状态暂不可用，请重试。')
    home.value = snapshot
  } catch (failure) {
    if (current(cycle)) error.value = failure.message || '伙伴状态读取失败，请重试。'
  } finally { if (current(cycle)) loading.value = false }
}
onMounted(load)
onBeforeUnmount(() => { active = false; generation += 1; controller?.abort() })
</script>
<style scoped>
.quick-chat { min-width: 0; }
.quick-chat > p, .quick-chat-state { color: var(--lp-text-secondary); font-size: .875rem; line-height: 1.7; }
.quick-chat-state { display: grid; gap: .75rem; }
</style>
