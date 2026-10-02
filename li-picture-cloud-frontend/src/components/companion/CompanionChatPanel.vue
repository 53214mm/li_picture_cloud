<template>
  <section class="chat-card" :class="{ 'chat-card--compact': compact }" :aria-labelledby="titleId" :data-chat-ready="!unavailable">
    <header>
      <button v-if="!compact" type="button" class="portrait-interaction" aria-label="和绫页互动" aria-haspopup="dialog" @click="interaction.open()">
        <CompanionPortrait :presentation="presentation" />
      </button>
      <CompanionPortrait v-else :presentation="presentation" />
      <div>
        <span class="eyebrow">绫页 · 站内对话</span>
        <h2 :id="titleId" tabindex="-1">{{ compact ? '和绫页聊聊' : '和伙伴说说话' }}</h2>
      </div>
    </header>

    <div v-if="state.loadError" class="chat-state error" role="alert">
      <p>{{ state.loadError }}</p>
      <button class="btn btn-outline" type="button" @click="chat.reload">重试</button>
    </div>
    <div v-else-if="state.loading && !state.messages.length" class="chat-state">正在加载对话记录…</div>
    <div v-else-if="!state.messages.length" class="chat-state">
      还没有对话记录。可以聊聊图片，或问问伙伴记得什么。
    </div>
    <div v-else ref="scroller" class="chat-scroll" role="log" aria-label="伙伴对话记录" tabindex="0">
      <div class="chat-list">
        <template v-for="message in state.messages" :key="message.localKey">
          <CompanionMessageBubble v-if="message.role === 'COMPANION'"
                                  :message="message.content"
                                  speaker-label="伙伴说" />
          <div v-else class="user-message">
            <span class="speaker-label">你说</span>
            <p class="message-bubble user-bubble">{{ message.content }}</p>
          </div>
        </template>
        <div v-if="sending" class="chat-state streaming" role="status">正在处理这条消息…</div>
      </div>
    </div>

    <form class="chat-input" @submit.prevent="chat.send">
      <label class="visually-hidden" :for="inputId">对伙伴说的话</label>
      <input :id="inputId" v-model="draft" type="text" maxlength="500"
             placeholder="对伙伴说点什么…" autocomplete="off" :disabled="sending || state.needsRefresh" />
      <button class="btn btn-primary" type="submit" :disabled="sending || unavailable || !draft.trim()">
        {{ sending ? '伙伴正在回应…' : '发送' }}
      </button>
    </form>
    <p v-if="state.sendError" class="chat-error" role="alert">{{ state.sendError }}</p>
    <div v-if="state.needsRefresh || state.notice" class="chat-recovery">
      <p>{{ state.notice }}</p>
      <button v-if="state.needsRefresh" type="button" class="btn btn-outline" :disabled="state.loading || sending" @click="chat.reload">
        {{ state.loading ? '正在刷新…' : '刷新对话' }}
      </button>
    </div>
    <p v-if="chatPolicy === 'DEMO'" class="chat-policy-note" data-testid="chat-policy-note">
      当前对话模式：演示回复（不调用模型）。
    </p>
  </section>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch, watchEffect } from 'vue'
import { storeToRefs } from 'pinia'
import CompanionMessageBubble from '@/components/companion/CompanionMessageBubble.vue'
import CompanionPortrait from '@/components/companion/body/CompanionPortrait.vue'
import { useCompanionInteractionStore } from '@/stores/companionInteraction'
import { useCompanionChatStore } from '@/stores/companionChat'
const interaction = useCompanionInteractionStore()
const chat = useCompanionChatStore()
const { state } = storeToRefs(chat)
const props = defineProps({
  chatPolicy: { type: String, default: null },
  presentation: { type: Object, required: true },
  compact: Boolean
})
const emit = defineEmits(['presentation-change'])
const scroller = ref(null)
const sending = computed(() => state.value.phase !== 'idle')
const draft = computed({ get: () => state.value.draft, set: value => chat.setDraft(value) })
const inputId = computed(() => props.compact ? 'companion-quick-chat-input' : 'companion-chat-input')
const titleId = computed(() => props.compact ? 'quick-chat-title' : 'chat-title')
const unavailable = computed(() => !state.value.loaded || state.value.loading || Boolean(state.value.loadError) || state.value.needsRefresh)
let release = () => {}
onMounted(() => { release = chat.acquire() })
onBeforeUnmount(() => { release(); emit('presentation-change', { phase: 'idle', error: false }) })
watchEffect(() => emit('presentation-change', {
  phase: state.value.phase,
  error: Boolean(state.value.sendError || state.value.loadError)
}))
watch(() => state.value.messages, async () => {
  await nextTick()
  if (scroller.value) scroller.value.scrollTop = scroller.value.scrollHeight
})
</script>

<style scoped>
.portrait-interaction { flex: 0 0 auto; border: 0; padding: 0; border-radius: var(--lp-radius-m); background: transparent; }
.portrait-interaction:hover, .portrait-interaction:focus-visible { outline: 2px solid var(--lp-accent); outline-offset: 3px; }
.chat-card { border: 2px solid var(--black); background: var(--white); }
.chat-card > header { display: flex; align-items: center; gap: 1rem; padding: 1.25rem 1.5rem; border-bottom: 2px solid var(--black); }
.chat-card h2 { font-size: 1.35rem; }
.eyebrow { color: var(--blue); font-size: .68rem; font-weight: 800; letter-spacing: .12em; text-transform: uppercase; }
.chat-state { padding: 2rem 1.5rem; color: var(--gray-600); }
.chat-state.error { color: var(--red); }
.chat-state.error .btn { margin-top: 1rem; }
.chat-state.streaming { padding: .6rem 1rem; font-size: .8rem; }
.chat-scroll { max-block-size: 26rem; overflow-y: auto; overscroll-behavior: contain; scrollbar-gutter: stable; }
.chat-list { display: grid; gap: .75rem; padding: 1.25rem 1.5rem; }
.chat-list :deep(.companion-message) { margin-top: 0; }
.user-message { display: flex; flex-direction: column; align-items: flex-end; }
.user-message .speaker-label { margin: 0 .65rem .28rem 0; color: var(--gray-600); font-size: .68rem; font-weight: 800; letter-spacing: .06em; }
.user-bubble { position: relative; max-width: 32rem; padding: .75rem .9rem; border: 2px solid var(--black); border-radius: 1rem .25rem 1rem 1rem; background: var(--yellow); font-size: .9rem; line-height: 1.65; overflow-wrap: anywhere; }
.chat-input { display: grid; grid-template-columns: minmax(0, 1fr) auto; gap: .5rem; padding: 1rem 1.5rem 1.5rem; border-top: 2px solid var(--black); }
.chat-input input { min-width: 0; padding: .6rem .75rem; border: 2px solid var(--black); font: inherit; font-size: .9rem; }
.chat-input .btn { min-height: 44px; }
.chat-error { margin: 0 1.5rem 1.25rem; color: var(--red); font-size: .8rem; }
.chat-recovery { display: grid; gap: .5rem; margin: 0 1rem 1rem; color: var(--lp-text-secondary); font-size: .8rem; }
.chat-card--compact { border: 1px solid var(--lp-border); border-radius: var(--lp-radius-m); overflow: hidden; }
.chat-card--compact > header { gap: .6rem; padding: .75rem; border-width: 1px; }
.chat-card--compact h2 { font-size: 1rem; }
.chat-card--compact :deep(.companion-portrait) { width: 56px; height: 56px; }
.chat-card--compact .chat-scroll { max-block-size: 18rem; }
.chat-card--compact .chat-list, .chat-card--compact .chat-input, .chat-card--compact .chat-state { padding: .75rem; }
.chat-card--compact .chat-input { grid-template-columns: 1fr; border-width: 1px; }
.chat-card--compact .chat-policy-note { margin-inline: .75rem; }
.chat-policy-note { margin: 0 1.5rem 1.25rem; padding: .5rem .7rem; border-left: 4px solid #8a6d1a; background: var(--gray-100); color: #8a6d1a; font-size: .78rem; font-weight: 700; }
.visually-hidden { position: absolute; width: 1px; height: 1px; overflow: hidden; clip: rect(0 0 0 0); white-space: nowrap; }
@media (max-width: 767px) {
  .chat-card > header, .chat-list, .chat-input { padding-inline: 1.25rem; }
  .chat-input { grid-template-columns: 1fr; }
  .chat-input .btn { width: 100%; }
}
</style>
