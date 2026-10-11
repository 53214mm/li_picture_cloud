<template>
  <section v-if="!floating || state?.speeches?.length" ref="reader" class="speech-reader" :class="{ floating }" aria-label="绫页的独立观察发言" data-testid="observation-speech">
    <header><img :src="lingyeAssets.shell.src" alt="绫页" width="40" height="48" /><strong>绫页 · 独立观察发言</strong></header>
    <div v-if="state?.speechVisible && latest" class="speech-bubble" data-testid="observation-speech-body" aria-live="polite">
      <span>最近一次实际发言 · {{ time(latest.at) }}</span><p>{{ latest.text }}</p>
      <div class="speech-actions"><button type="button" @click="store.coordinator.dismissSpeech()">关闭气泡</button><button type="button" @click="$emit('reply')">回复绫页 · 普通聊天</button></div>
    </div>
    <p v-else-if="!latest" class="notice">还没有收到有效发言。请求状态会单独显示，不代替绫页说话。</p>
    <p class="notice" role="status" data-testid="observation-activity">{{ activity }}</p>
    <details v-if="state?.speeches?.length" data-testid="observation-speech-history">
      <summary>查看近期发言（{{ state.speeches.length }} / 50）</summary>
      <p class="notice">仅当前账号的应用内存；跨页和关闭气泡保留，刷新、退出、切号或清空会删除。</p>
      <ol><li v-for="speech in state.speeches" :key="speech.id"><time>{{ time(speech.at) }}</time><p>{{ speech.text }}</p></li></ol>
    </details>
  </section>
</template>
<script setup>
import { computed, ref, watch, nextTick } from 'vue'
import { useCompanionObserverStore } from '@/stores/companionObserver'
import { observationActivity } from '@/presentation/observationPresentation'
import { lingyeAssets } from '@/components/companion/body/lingyeAssets'
const props = defineProps({ floating: Boolean })
defineEmits(['reply'])
const store = useCompanionObserverStore()
const state = computed(() => store.state)
const latest = computed(() => state.value?.speeches?.[0])
const reader = ref(null)
watch(() => latest.value?.id, async id => {
  if (!id || props.floating || !state.value?.speechVisible) return
  await nextTick()
  if (latest.value?.id === id && state.value?.speechVisible) reader.value?.querySelector?.('[data-testid="observation-speech-body"]')?.scrollIntoView?.({ block: 'nearest' })
}, { flush: 'post' })
const activity = computed(() => observationActivity(state.value))
const time = at => new Date(at).toLocaleTimeString()
</script>
<style scoped>
.speech-reader { min-width: 0; border: 1px solid var(--lp-border); border-radius: 14px; padding: 12px; background: var(--lp-surface); font-size: .9rem; }
.floating { position: fixed; left: 240px; bottom: 20px; z-index: var(--lp-z-sticky); width: min(340px, calc(100vw - 32px)); max-height: min(55dvh, 520px); overflow: auto; box-shadow: 0 8px 32px #0002; }
header { display: flex; align-items: center; gap: 10px; margin-bottom: 8px; } header img { object-fit: contain; flex-shrink: 0; }
.speech-bubble { padding: 12px; border-radius: 4px 14px 14px; background: var(--lp-accent-soft); }
.speech-bubble p, li p { white-space: pre-wrap; overflow-wrap: anywhere; line-height: 1.75; margin-block: 8px; }
.speech-bubble > span, time, .notice { color: var(--lp-text-secondary); font-size: .8rem; line-height: 1.6; }
.speech-actions { display: flex; flex-wrap: wrap; gap: 8px; } button, summary { cursor: pointer; min-height: 36px; padding: 8px 4px; } button { text-decoration: underline; } .notice { margin-block: 8px; }
ol { padding-left: 20px; max-height: 260px; overflow: auto; } li { padding-block: 8px; border-bottom: 1px solid var(--lp-border); }
@media (768px <= width < 1024px) { .floating { left: 104px; width: 280px; } }
@media (width < 768px) { .floating { left: 16px; bottom: 136px; width: calc(100vw - 32px); max-height: 45dvh; } }
</style>
