<template>
  <section class="chat-card" aria-labelledby="chat-title">
    <header>
      <div>
        <span class="eyebrow">站内对话</span>
        <h2 id="chat-title">和伙伴说说话</h2>
      </div>
    </header>

    <div v-if="loadError" class="chat-state error" role="alert">
      <p>{{ loadError }}</p>
      <button class="btn btn-outline" type="button" @click="loadHistory">重试</button>
    </div>
    <div v-else-if="loading && !messages.length" class="chat-state">正在读取我们的对话…</div>
    <div v-else-if="!messages.length" class="chat-state">
      伙伴还在这里。你可以问问它记得什么，或者聊聊今天想喂它哪张图片。
    </div>
    <div v-else class="chat-scroll" role="log" aria-label="伙伴对话记录" tabindex="0">
      <div class="chat-list">
        <template v-for="message in messages" :key="message.localKey">
          <CompanionMessageBubble v-if="message.role === 'COMPANION'"
                                  :message="message.content"
                                  speaker-label="伙伴说" />
          <div v-else class="user-message">
            <span class="speaker-label">你说</span>
            <p class="message-bubble user-bubble">{{ message.content }}</p>
          </div>
        </template>
        <div v-if="sending" class="chat-state streaming" role="status">伙伴正在想怎么回…</div>
      </div>
    </div>

    <form class="chat-input" @submit.prevent="send">
      <label class="visually-hidden" for="companion-chat-input">对伙伴说的话</label>
      <input id="companion-chat-input" v-model="draft" type="text" maxlength="500"
             placeholder="对伙伴说点什么…" autocomplete="off" :disabled="sending" />
      <button class="btn btn-primary" type="submit" :disabled="sending || !draft.trim()">
        {{ sending ? '伙伴正在回应…' : '发送' }}
      </button>
    </form>
    <p v-if="sendError" class="chat-error" role="alert">{{ sendError }}</p>
  </section>
</template>

<script setup>
import { nextTick, onMounted, ref } from 'vue'
import { listCompanionChatHistory } from '@/api/companion'
import { streamCompanionChat } from '@/utils/companion'
import CompanionMessageBubble from '@/components/companion/CompanionMessageBubble.vue'

const messages = ref([])
const loading = ref(false)
const loadError = ref('')
const draft = ref('')
const sending = ref(false)
const sendError = ref('')
let localKeySeed = 0

onMounted(loadHistory)

async function loadHistory() {
  loading.value = true
  loadError.value = ''
  try {
    const history = await listCompanionChatHistory()
    messages.value = (history.records || []).map(message => withKey(message))
  } catch (error) {
    loadError.value = error.message || '对话历史读取失败，请稍后重试。'
  } finally {
    loading.value = false
  }
}

async function send() {
  const content = draft.value.trim()
  if (sending.value || !content) return
  sending.value = true
  sendError.value = ''
  messages.value.push(withKey({ role: 'USER', content }))
  draft.value = ''
  let partial = ''
  const companionDraft = withKey({ role: 'COMPANION', content: '' })
  messages.value.push(companionDraft)
  try {
    await streamCompanionChat(content, {
      onChunk(chunk) {
        partial += chunk
        companionDraft.content = partial
      },
      onError(error) {
        companionDraft.content = partial || error.message
        sendError.value = error.message
      }
    })
    if (!companionDraft.content) companionDraft.content = '（这次没有想好怎么回）'
  } catch (error) {
    // 请求未发出或响应异常：移除空泡泡并提示，历史刷新后会与后端一致。
    const index = messages.value.indexOf(companionDraft)
    if (index !== -1 && !companionDraft.content) messages.value.splice(index, 1)
    sendError.value = error.message || '伙伴暂时没法回应，请稍后再试。'
  } finally {
    sending.value = false
    await nextTick()
    scrollToBottom()
  }
}

function withKey(message) {
  localKeySeed += 1
  return { localKey: `m-${localKeySeed}`, role: message.role, content: message.content }
}

function scrollToBottom() {
  const scroller = document.querySelector('.chat-scroll')
  if (scroller) scroller.scrollTop = scroller.scrollHeight
}
</script>

<style scoped>
.chat-card { border: 2px solid var(--black); background: var(--white); }
.chat-card > header { padding: 1.25rem 1.5rem; border-bottom: 2px solid var(--black); }
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
.visually-hidden { position: absolute; width: 1px; height: 1px; overflow: hidden; clip: rect(0 0 0 0); white-space: nowrap; }
@media (max-width: 767px) {
  .chat-card > header, .chat-list, .chat-input { padding-inline: 1.25rem; }
  .chat-input { grid-template-columns: 1fr; }
  .chat-input .btn { width: 100%; }
}
</style>
