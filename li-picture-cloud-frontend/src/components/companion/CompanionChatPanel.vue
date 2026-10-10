<template>
  <section class="chat-card" :class="{ 'chat-card--compact': compact }" :aria-labelledby="titleId" :data-chat-ready="!unavailable">
    <header>
      <button v-if="!compact" type="button" class="portrait-interaction" aria-label="和绫页互动" aria-haspopup="dialog" @click="interaction.open()">
        <CompanionPortrait :presentation="presentation" />
      </button>
      <CompanionPortrait v-else :presentation="presentation" />
      <div>
        <span class="eyebrow">绫页 · 伙伴日常</span>
        <h2 :id="titleId" tabindex="-1">和绫页聊聊</h2>
        <p class="chat-invitation">今天的小事，也可以说给她听。</p>
      </div>
    </header>

    <details v-if="modeView" class="chat-mode" data-testid="chat-mode" :open="modeView.modelBroken || Boolean(chat.modeError)">
      <summary>回应设置 <span>{{ modeView.mode === 'MODEL' ? '真实模型' : '演示回复' }}</span></summary>
      <div class="chat-mode-switch" role="group" aria-label="聊天模式">
        <button type="button" :aria-pressed="modeView.mode === 'DEMO'" :disabled="modeLocked"
                @click="chat.chooseMode('DEMO')">演示回复</button>
        <button type="button" :aria-pressed="modeView.mode === 'MODEL'" :disabled="modeLocked || !modeView.modelAvailable"
                :title="modeView.modelAvailable ? '' : '平台模型未启用或暂不可用，也可以连接自己的模型'" @click="chat.chooseMode('MODEL')">真实模型</button>
      </div>
      <p class="chat-mode-note" data-testid="chat-mode-note">
        {{ modeCopy.note }}
        <router-link v-if="modeCopy.link" to="/model-gateway">{{ modeCopy.link }}</router-link>
      </p>
      <p v-if="chat.modeError" class="chat-error" role="alert">{{ chat.modeError }}</p>
    </details>

    <div v-if="state.loadError" class="chat-state error" role="alert">
      <p>{{ state.loadError }}</p>
      <button class="btn btn-outline" type="button" @click="chat.reload">重试</button>
    </div>
    <div v-else-if="state.loading && !state.messages.length" class="chat-state" role="status">正在找回上次聊到的地方…</div>
    <form class="chat-input" @submit.prevent="chat.send">
      <label class="visually-hidden" :for="inputId">对伙伴说的话</label>
      <input :id="inputId" v-model="draft" type="text" maxlength="500"
             placeholder="此刻，想和绫页说什么？" autocomplete="off" :disabled="sending || state.needsRefresh" />
      <button class="btn btn-primary" type="submit" :disabled="sending || unavailable || !draft.trim()">
        {{ sending ? '回应中…' : '发送' }}
      </button>
    </form>
    <p v-if="state.sendError" class="chat-error" role="alert">{{ state.sendError }}</p>
    <div v-if="state.needsRefresh || state.notice" class="chat-recovery">
      <p>{{ state.notice }}</p>
      <button v-if="state.needsRefresh" type="button" class="btn btn-outline" :disabled="state.loading || sending" @click="chat.reload">
        {{ state.loading ? '正在刷新…' : '刷新对话' }}
      </button>
    </div>

    <div v-if="!state.loadError && !state.loading && !moment.current.length" class="chat-state chat-welcome">
      <span class="moment-label">留一点时间，一起待着</span>
      不必想好话题，从一句「今天过得怎么样」开始也好。
    </div>
    <div v-else-if="moment.current.length" class="chat-moment" role="log" aria-label="最近一轮交流" aria-live="polite">
      <template v-for="message in moment.current" :key="message.localKey">
        <div v-if="message.role === 'COMPANION' && message.content" class="moment-reply">
          <span class="moment-label">绫页的回应</span>
          <p class="moment-content" tabindex="0" aria-label="绫页的回应内容">{{ message.content }}</p>
        </div>
        <div v-else-if="message.role === 'USER'" class="moment-question">
          <span class="moment-label">刚刚，你说</span>
          <p tabindex="0" aria-label="你刚刚说的话">{{ message.content }}</p>
        </div>
      </template>
      <p v-if="sending" class="chat-state streaming" role="status">{{ state.phase === 'waiting' ? '绫页正在想怎么回应…' : '绫页还在说…' }}</p>
      <p v-else-if="!moment.current.some(message => message.role === 'COMPANION' && message.content)" class="chat-state streaming">这句话还没有收到完整回应。</p>
    </div>

    <details v-if="moment.previous.length" class="chat-memories" @toggle="openMemories">
      <summary>回看之前的对话 <span>{{ moment.previous.length }} 条</span></summary>
      <p class="history-note">这里是最近保留的对话，最新一轮留在上面。</p>
      <div ref="scroller" class="chat-scroll" role="region" aria-label="之前的对话" tabindex="0">
        <div class="chat-list">
          <template v-for="message in moment.previous" :key="message.localKey">
            <CompanionMessageBubble v-if="message.role === 'COMPANION'" :message="message.content" speaker-label="绫页" />
            <div v-else class="user-message">
              <span class="speaker-label">你</span>
              <p class="user-bubble">{{ message.content }}</p>
            </div>
          </template>
        </div>
      </div>
    </details>
  </section>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch, watchEffect } from 'vue'
import { storeToRefs } from 'pinia'
import CompanionMessageBubble from '@/components/companion/CompanionMessageBubble.vue'
import CompanionPortrait from '@/components/companion/body/CompanionPortrait.vue'
import { useCompanionInteractionStore } from '@/stores/companionInteraction'
import { useCompanionChatStore } from '@/stores/companionChat'
import { describeChatMode } from '@/presentation/companionChatMode'
import { splitChatMoment } from '@/presentation/companionChatMoment'
const interaction = useCompanionInteractionStore()
const chat = useCompanionChatStore()
const { state } = storeToRefs(chat)
const props = defineProps({
  chatMode: { type: Object, default: null },
  presentation: { type: Object, required: true },
  compact: Boolean
})
const emit = defineEmits(['presentation-change'])
const scroller = ref(null)
const moment = computed(() => splitChatMoment(state.value.messages))
const sending = computed(() => state.value.phase !== 'idle')
const draft = computed({ get: () => state.value.draft, set: value => chat.setDraft(value) })
const inputId = computed(() => props.compact ? 'companion-quick-chat-input' : 'companion-chat-input')
const titleId = computed(() => props.compact ? 'quick-chat-title' : 'chat-title')
// Home reads carry the server's chat mode; a switch made in either panel is shared.
watch(() => props.chatMode, view => chat.adoptMode(view), { immediate: true })
const modeView = computed(() => chat.mode)
const modeCopy = computed(() => describeChatMode(modeView.value))
const modeLocked = computed(() => chat.modeBusy || sending.value)
const unavailable = computed(() => !state.value.loaded || state.value.loading || Boolean(state.value.loadError) || state.value.needsRefresh)
let release = () => {}
onMounted(() => { release = chat.acquire() })
onBeforeUnmount(() => { release(); emit('presentation-change', { phase: 'idle', error: false }) })
watchEffect(() => emit('presentation-change', {
  phase: state.value.phase,
  error: Boolean(state.value.sendError || state.value.loadError)
}))
// Opening memories starts near the most recent past exchange. Streaming never
// moves the page or pulls a reader away from the older exchange they chose.
async function openMemories(event) {
  if (!event.target.open) return
  await nextTick()
  if (scroller.value) scroller.value.scrollTop = scroller.value.scrollHeight
}
</script>

<style scoped>
.chat-card { --chat-ink: #37463b; --chat-muted: #64715f; --chat-line: #dce1d2; min-width: 0; border: 1px solid var(--chat-line); border-radius: 16px; background: #fffdf7; color: var(--chat-ink); }
.chat-card > header { display: flex; align-items: center; gap: 1rem; padding: 1.25rem 1.5rem .75rem; }
.chat-card h2 { margin: .2rem 0; font-size: 1.35rem; font-weight: 500; }
.portrait-interaction { flex: 0 0 auto; border: 0; padding: 0; border-radius: var(--lp-radius-m); background: transparent; }
.portrait-interaction:hover, .portrait-interaction:focus-visible { outline: 2px solid var(--chat-muted); outline-offset: 3px; }
.eyebrow, .moment-label { display: block; color: var(--chat-muted); font-size: .72rem; letter-spacing: .05em; }
.chat-invitation { color: var(--chat-muted); font-size: .82rem; line-height: 1.6; }
.chat-input { display: grid; grid-template-columns: minmax(0, 1fr) auto; gap: .6rem; padding: 1rem 1.5rem; }
.chat-input input { min-width: 0; min-height: 46px; padding: .7rem .85rem; border: 1px solid #9fae99; border-radius: 10px; background: #fffefa; color: var(--chat-ink); font: inherit; font-size: 16px; }
.chat-input input:focus-visible { outline: 2px solid #637b60; outline-offset: 2px; }
.chat-input .btn { min-width: 72px; min-height: 46px; border: 0; border-radius: 10px; box-shadow: none; background: #435a47; color: #fffdf7; }
.chat-input .btn:disabled { opacity: .55; }
.chat-moment { display: grid; gap: 1rem; padding: .25rem 1.5rem 1.5rem; }
.moment-question { padding-left: .8rem; border-left: 2px solid #d2dbc8; color: var(--chat-muted); font-size: .85rem; }
.moment-question p { max-height: 5rem; overflow-y: auto; overflow-wrap: anywhere; margin-top: .3rem; line-height: 1.7; }
.moment-reply { padding: 1rem 1.2rem; border-radius: 4px 18px 18px 18px; background: #f0f2e8; }
.moment-content { margin-top: .65rem; max-height: 14rem; overflow-y: auto; overscroll-behavior: contain; white-space: pre-wrap; overflow-wrap: anywhere; font-size: .95rem; line-height: 1.9; }
.moment-content:focus-visible, .moment-question p:focus-visible, summary:focus-visible, .chat-scroll:focus-visible { outline: 2px solid #637b60; outline-offset: 3px; }
.chat-state { padding: .8rem 1.5rem; color: var(--chat-muted); font-size: .85rem; line-height: 1.8; }
.chat-welcome { margin: 0 1.5rem 1.5rem; padding: 1.2rem; border-radius: 12px; background: #f0f2e8; }
.chat-welcome .moment-label { margin-bottom: .5rem; }
.chat-state.error, .chat-error { color: #9d3e2f; }
.chat-state.error .btn { margin-top: .6rem; }
.chat-state.streaming { padding: 0; font-size: .8rem; }
.chat-error, .chat-recovery { margin: 0 1.5rem 1rem; font-size: .85rem; line-height: 1.7; }
.chat-recovery { display: grid; justify-items: start; gap: .5rem; }
.chat-mode { margin: 0 1.5rem; color: var(--chat-muted); }
summary { min-height: 44px; padding: .65rem 0; cursor: pointer; font-size: .8rem; }
summary span { margin-left: .5rem; color: var(--chat-muted); font-size: .75rem; }
.chat-mode-switch { display: inline-flex; flex-wrap: wrap; gap: .25rem; padding: 3px; border: 1px solid var(--chat-line); border-radius: 12px; background: #fffefa; }
.chat-mode-switch button { min-height: 44px; padding: 0 .9rem; border: 0; border-radius: 9px; background: transparent; color: var(--chat-muted); font-size: .8rem; cursor: pointer; }
.chat-mode-switch button[aria-pressed="true"] { background: #435a47; color: #fffdf7; }
.chat-mode-switch button:disabled { cursor: not-allowed; opacity: .5; }
.chat-mode-switch button:focus-visible { outline: 2px solid #637b60; outline-offset: 2px; }
.chat-mode-note { padding: .5rem 0; font-size: .78rem; line-height: 1.6; overflow-wrap: anywhere; }
.chat-mode-note a { display: inline-block; margin-left: .25rem; color: #3f684a; text-decoration: underline; text-underline-offset: 3px; }
.chat-mode .chat-error { margin: .5rem 0; }
.chat-memories { margin: 0 1.5rem; border-top: 1px solid var(--chat-line); }
.history-note { font-size: .75rem; color: var(--chat-muted); line-height: 1.7; }
.chat-scroll { max-height: 20rem; overflow-y: auto; overscroll-behavior: contain; scrollbar-gutter: stable; }
.chat-list { display: grid; gap: 1rem; padding: 1rem .25rem; }
.chat-list :deep(.companion-message) { margin-top: 0; }
.chat-list :deep(.companion-avatar) { display: none; }
.chat-list :deep(.message-bubble) { border: 0; background: #f0f2e8; white-space: pre-wrap; }
.chat-list :deep(.message-bubble)::before { display: none; }
.user-message { display: flex; flex-direction: column; align-items: flex-end; }
.speaker-label { color: var(--chat-muted); font-size: .72rem; }
.user-bubble { max-width: 90%; padding: .65rem .9rem; border-radius: 12px; background: #eeeadd; font-size: .85rem; line-height: 1.7; white-space: pre-wrap; overflow-wrap: anywhere; }
.chat-card--compact > header { gap: .65rem; padding: .85rem; }
.chat-card--compact h2 { font-size: 1.1rem; }
.chat-card--compact :deep(.companion-portrait) { width: 56px; height: 56px; }
.chat-card--compact .chat-input, .chat-card--compact .chat-moment { padding: .75rem; }
.chat-card--compact .chat-mode, .chat-card--compact .chat-memories, .chat-card--compact .chat-error, .chat-card--compact .chat-recovery, .chat-card--compact .chat-welcome { margin-inline: .75rem; }
.chat-card--compact .moment-content { max-height: 10rem; }
.visually-hidden { position: absolute; width: 1px; height: 1px; overflow: hidden; clip: rect(0 0 0 0); white-space: nowrap; }
@media (max-width: 767px) {
  .chat-card > header, .chat-input, .chat-moment { padding-inline: 1rem; }
  .chat-mode, .chat-memories, .chat-error, .chat-recovery, .chat-welcome { margin-inline: 1rem; }
  .chat-input { gap: .4rem; }
  .chat-input .btn { min-width: 60px; padding-inline: .65rem; }
  .chat-input input { padding-inline: .6rem; }
  .moment-reply { padding: .85rem; }
}
</style>
