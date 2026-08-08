<template>
  <div class="ai-panel">
    <!-- 快捷按钮 -->
    <div class="quick-row">
      <button class="quick-btn" @click="fillInput('帮我生成一张图片：')">🖼️ 生成图片</button>
      <button class="quick-btn" @click="fillInput('查询任务进度：')">📋 查询结果</button>
      <button class="quick-btn" @click="openImageForm">🔍 以图生图</button>
      <button class="quick-btn clear-btn" @click="clearChat" v-if="messages.length > 1">🗑️ 清空</button>
    </div>

    <!-- 以图生图表单 -->
    <div v-if="imageForm.visible" class="image-form">
      <div class="image-form-row">
        <label>参考图片</label>
        <div class="file-row">
          <input type="file" accept="image/*" @change="onRefFileChange" ref="refFileInput" class="file-input" />
          <span class="file-or">或粘贴 URL</span>
        </div>
        <input v-model="imageForm.url" class="chat-input" placeholder="https://example.com/image.jpg" @keyup.enter="submitImageForm" />
        <span v-if="imageForm.uploading" class="upload-hint">上传中...</span>
      </div>
      <div class="image-form-row">
        <label>风格描述</label>
        <input v-model="imageForm.desc" class="chat-input" placeholder="描述你想要的风格或效果" @keyup.enter="submitImageForm" />
      </div>
      <div class="image-form-row form-buttons">
        <button class="quick-btn" @click="imageForm.visible = false">取消</button>
        <button class="quick-btn" @click="submitImageForm" :disabled="!imageForm.url.trim() || imageForm.uploading" style="border-color:#8b5cf6;background:#f0edff;">发送</button>
      </div>
    </div>

    <!-- 对话区 -->
    <div ref="chatRef" class="chat-area">
      <div v-for="(m, i) in messages" :key="i"
        :class="m.role === 'user' ? 'msg-right' : 'msg-left'"
      >
        <div v-if="m.role !== 'user'" class="ai-avatar">AI</div>
        <div :class="m.role === 'user' ? 'bubble-user' : 'bubble-ai'">
          <div v-if="m.role === 'user'">{{ m.content }}</div>
          <div v-else class="markdown-body" v-html="renderMd(m.content)"></div>
        </div>
      </div>

      <div v-if="loading" class="msg-left">
        <div class="ai-avatar">AI</div>
        <div class="bubble-ai streaming">
          <div v-if="streaming" class="markdown-body" v-html="renderMd(streaming)"></div>
          <span v-else class="dots"><i>.</i><i>.</i><i>.</i></span>
          <span class="cursor"></span>
        </div>
      </div>
    </div>

    <!-- 输入区 -->
    <div class="input-row">
      <input v-model="input" class="chat-input" placeholder="输入消息…" :disabled="loading"
        @keyup.enter="send" ref="inputRef" />
      <button class="send-btn" :disabled="loading || !input.trim()" @click="send">
        <svg v-if="!loading" width="18" height="18" fill="currentColor" viewBox="0 0 20 20"><path d="M10.894 2.553a1 1 0 00-1.788 0l-7 14a1 1 0 001.169 1.409l5-1.429A1 1 0 009 15.571V11a1 1 0 112 0v4.571a1 1 0 00.725.962l5 1.428a1 1 0 001.17-1.408l-7-14z"/></svg>
        <span v-else>…</span>
      </button>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, nextTick, onBeforeUnmount, watch } from 'vue'
import { marked } from 'marked'
import DOMPurify from 'dompurify'
import { uploadPicture } from '@/api/picture'
marked.setOptions({ breaks: true, gfm: true })

const props = defineProps({ userId: { type: [String, Number], default: '' } })
const STORAGE_KEY = 'ai_msgs_' + props.userId
const CHAT_ID_KEY = 'ai_cid_' + props.userId

function loadHistory() {
  try {
    const saved = localStorage.getItem(STORAGE_KEY)
    if (saved) { const msgs = JSON.parse(saved); if (msgs.length) return msgs }
  } catch { /* localStorage may be unavailable */ }
  return [{ role: 'assistant', content: '你好！我是 **PicAgent** 🎨\n\n试试点击下方按钮，或者直接告诉我你想做什么。' }]
}
function saveHistory(msgs) { localStorage.setItem(STORAGE_KEY, JSON.stringify(msgs.slice(-40))) }

const messages = ref(loadHistory())
const input = ref('')
const loading = ref(false)
const streaming = ref('')
const chatRef = ref(null)
const inputRef = ref(null)
let chatId = localStorage.getItem(CHAT_ID_KEY) || ('pic-' + Date.now())

watch(messages, v => saveHistory(v), { deep: true })
onBeforeUnmount(() => { saveHistory(messages.value); localStorage.setItem(CHAT_ID_KEY, chatId) })

function scroll() { nextTick(() => { const el = chatRef.value; if (el) el.scrollTop = el.scrollHeight }) }

function renderMd(text) {
  if (!text) return ''
  try {
    // 1. 先提取 markdown 图片语法 ![alt](url)，替换为占位符保护起来
    const protectedImgs = []
    let html = text.replace(/!\[([^\]]*)\]\(([^)\s][^)]*)\)/g, (_, alt, url) => {
      protectedImgs.push({ alt, url })
      return `\n__IMG_${protectedImgs.length - 1}__\n`
    })
    // 2. 裸图片 URL → <img>
    html = html.replace(
      /(?<!["'=])(https?:\/\/\S+\.(?:png|jpg|jpeg|webp|gif)(?:\?[^\s<>"'\n]*)?)/gi,
      '\n<img src="$1" style="max-width:100%;border-radius:8px;margin:0.5rem 0" loading="lazy" />\n'
    )
    // 3. 恢复被保护的 markdown 图片
    html = html.replace(/__IMG_(\d+)__/g, (_, i) => {
      const p = protectedImgs[parseInt(i)]
      return `\n<img src="${p.url}" alt="${p.alt}" style="max-width:100%;border-radius:8px;margin:0.5rem 0" loading="lazy" />\n`
    })
    // 4. marked 解析剩余的 markdown 格式
    html = marked.parse(html)
    return DOMPurify.sanitize(html, {
      ALLOWED_TAGS: ['img', 'h1', 'h2', 'h3', 'h4', 'h5', 'h6', 'p', 'br', 'strong', 'em', 'code', 'pre', 'ul', 'ol', 'li', 'a', 'blockquote', 'table', 'thead', 'tbody', 'tr', 'th', 'td', 'hr', 'span', 'div'],
      ALLOWED_ATTR: ['src', 'alt', 'href', 'target', 'rel', 'class', 'style', 'loading']
    })
  } catch { return text.replace(/\n/g, '<br>') }
}

/** ★ 修复快捷按钮：只填输入框不自动发送，让用户可以继续编辑 */
function fillInput(prompt) {
  input.value = prompt
  nextTick(() => {
    inputRef.value?.focus()
  })
}

/** ★ 以图生图表单 */
const refFileInput = ref(null)
const imageForm = reactive({ visible: false, url: '', desc: '', uploading: false })
function openImageForm() {
  imageForm.visible = true
  imageForm.url = ''
  imageForm.desc = ''
  imageForm.uploading = false
}
/** 选择本地文件 → 上传到平台拿到 URL → 填入表单 */
async function onRefFileChange(e) {
  const file = e.target.files?.[0]
  if (!file) return
  imageForm.uploading = true
  try {
    const formData = new FormData()
    formData.append('file', file)
    const result = await uploadPicture(formData)
    imageForm.url = result.url || result.thumbnailUrl || ''
  } catch (err) {
    alert('上传失败: ' + (err.message || '未知错误'))
  } finally {
    imageForm.uploading = false
    if (refFileInput.value) refFileInput.value.value = ''
  }
}
function submitImageForm() {
  const url = imageForm.url.trim()
  if (!url) return
  const desc = imageForm.desc.trim() || '相似风格'
  const msg = `请用这张参考图生成一张相似风格的图片。参考图URL: ${url}，描述: ${desc}`
  imageForm.visible = false
  messages.value.push({ role: 'user', content: msg })
  scroll()
  doSend(msg)
}

/** ★ 清空对话：清除前端状态 + 后端记忆 */
async function clearChat() {
  try { await fetch('/api/ai/chat/clear?chatId=' + chatId, { method: 'POST' }) } catch { /* local state is still cleared */ }
  messages.value = [{ role: 'assistant', content: '你好！我是 **PicAgent** 🎨\n\n试试点击下方按钮，或者直接告诉我你想做什么。' }]
  streaming.value = ''
  chatId = 'pic-' + Date.now()
  localStorage.removeItem(STORAGE_KEY)
  localStorage.setItem(CHAT_ID_KEY, chatId)
}

async function send() {
  const msg = input.value.trim()
  if (!msg || loading.value) return
  input.value = ''
  messages.value.push({ role: 'user', content: msg })
  scroll()
  doSend(msg)
}

/** 发送指定消息到 AI 后端（供以图生图/上传参考图表单复用） */
function doSend(msg) {
  loading.value = true; streaming.value = ''

  const url = '/api/ai/chat/stream?message=' + encodeURIComponent(msg) + '&chatId=' + chatId
  const es = new EventSource(url)
  let retryCount = 0

  es.onmessage = (event) => {
    retryCount = 0
    streaming.value += event.data
    try {
      const msgs = messages.value.slice(-39)
      msgs.push({ role: 'assistant', content: streaming.value })
      localStorage.setItem(STORAGE_KEY, JSON.stringify(msgs))
    } catch { /* localStorage may be unavailable */ }
    scroll()
  }

  es.onerror = () => {
    retryCount++
    if (streaming.value) {
      es.close()
      messages.value.push({ role: 'assistant', content: streaming.value })
      streaming.value = ''; loading.value = false
      return
    }
    if (retryCount <= 3) {
      console.warn('SSE 断连，自动重连中... #' + retryCount)
      return
    }
    es.close()
    messages.value.push({ role: 'assistant', content: '（网络异常，请重试）' })
    streaming.value = ''; loading.value = false
  }

  es.addEventListener('done', () => { es.close()
    if (streaming.value) {
      messages.value.push({ role: 'assistant', content: streaming.value })
      localStorage.setItem(CHAT_ID_KEY, chatId)
    }
    streaming.value = ''; loading.value = false
  })
}
</script>

<style scoped>
.ai-panel { display: flex; flex-direction: column; height: 100%; min-height: 450px; }

/* 快捷按钮 */
.quick-row { display: flex; gap: 0.5rem; padding: 0.5rem 1rem; flex-wrap: wrap; border-bottom: 1px solid #e8e6f0; }
.quick-btn {
  padding: 0.375rem 0.75rem; font-size: 0.75rem; font-weight: 500;
  border: 1.5px solid #e0ddf0; background: #f8f7ff; border-radius: 8px;
  cursor: pointer; transition: all 0.15s; white-space: nowrap;
}
.quick-btn:hover { border-color: #8b5cf6; background: #f0edff; }
.quick-btn:disabled { opacity: 0.4; }
.clear-btn { border-color: #fecaca; background: #fff5f5; color: #dc2626; }
.clear-btn:hover { border-color: #dc2626; background: #fef2f2; }

/* 图片表单 */
.image-form { padding: 0.75rem 1rem; border-bottom: 1px solid #e8e6f0; background: #faf9ff; display: flex; flex-direction: column; gap: 0.5rem; }
.image-form-row { display: flex; flex-direction: column; gap: 0.25rem; }
.image-form-row label { font-size: 0.75rem; font-weight: 500; color: #555; }
.file-row { display: flex; align-items: center; gap: 0.5rem; margin-bottom: 0.25rem; }
.file-input { font-size: 0.75rem; }
.file-or { font-size: 0.7rem; color: #999; }
.upload-hint { font-size: 0.7rem; color: #8b5cf6; }
.form-buttons { flex-direction: row; justify-content: flex-end; gap: 0.5rem; }

/* 对话区 */
.chat-area { flex: 1; overflow-y: auto; padding: 1rem; display: flex; flex-direction: column; gap: 1rem; }
.msg-right { display: flex; justify-content: flex-end; }
.msg-left { display: flex; gap: 0.625rem; align-items: flex-start; }
.ai-avatar {
  width: 30px; height: 30px; flex-shrink: 0;
  background: linear-gradient(135deg, #6366f1, #8b5cf6);
  border-radius: 8px; display: flex; align-items: center; justify-content: center;
  color: #fff; font-size: 0.6875rem; font-weight: 700; margin-top: 2px;
}
.bubble-user {
  max-width: 75%; padding: 0.625rem 1rem;
  background: var(--black); color: var(--white);
  border-radius: 16px 16px 4px 16px; font-size: 0.875rem; line-height: 1.5; word-break: break-word;
}
.bubble-ai {
  max-width: 95%; padding: 0.75rem 1rem;
  background: #f8f7ff; border: 1px solid #e8e6f0;
  border-radius: 4px 16px 16px 16px; font-size: 0.875rem; line-height: 1.6; word-break: break-word;
}
.bubble-ai.streaming { background: #f3f0ff; border-color: #d8d4f0; }
.dots i { animation: dotBounce 1.4s infinite both; font-style: normal; font-size: 1.25rem; color: #8b5cf6; }
.dots i:nth-child(2) { animation-delay: 0.2s; }
.dots i:nth-child(3) { animation-delay: 0.4s; }
@keyframes dotBounce { 0%,80%,100%{opacity:.2} 40%{opacity:1} }
.cursor { display: inline-block; width: 2px; height: 16px; background: #8b5cf6; animation: blink .8s infinite; vertical-align: text-bottom; margin-left: 1px; }
@keyframes blink { 0%,100%{opacity:1} 50%{opacity:0} }

.input-row { display: flex; gap: 0.5rem; padding: 0.75rem 1rem; border-top: 1px solid #e8e6f0; background: #fff; }
.chat-input { flex: 1; padding: 0.625rem 1rem; font-size: 0.875rem; border: 1.5px solid #e8e6f0; border-radius: 12px; outline: none; font-family: inherit; }
.chat-input:focus { border-color: #8b5cf6; }
.send-btn {
  width: 42px; height: 42px; flex-shrink: 0;
  background: linear-gradient(135deg, #6366f1, #8b5cf6);
  color: #fff; border: none; border-radius: 12px; cursor: pointer;
  display: flex; align-items: center; justify-content: center; transition: opacity .2s;
}
.send-btn:disabled { opacity: 0.4; }

.markdown-body :deep(img) { max-width: 100%; max-height: 400px; object-fit: contain; border-radius: 8px; margin: 0.5rem 0; cursor: pointer; display: block; }
.markdown-body :deep(img:hover) { opacity: 0.9; }
.markdown-body :deep(h2) { font-size: 1.1rem; margin: 0.5rem 0 0.25rem; font-weight: 600; }
.markdown-body :deep(p) { margin: 0.25rem 0; }
.markdown-body :deep(code) { background: #eeeaf8; padding: 0.125rem 0.375rem; border-radius: 4px; font-size: 0.8125rem; }
.markdown-body :deep(pre) { background: #1e1b2e; color: #e0dff0; padding: 0.75rem; border-radius: 8px; overflow-x: auto; margin: 0.5rem 0; }
.markdown-body :deep(pre code) { background: none; padding: 0; }
.markdown-body :deep(strong) { font-weight: 600; }

@media (max-width: 767px) {
  .ai-panel { min-height: min(30rem, 70dvh); }
  .quick-row { flex-wrap: nowrap; overflow-x: auto; padding-inline: 0.75rem; }
  .quick-btn { min-height: 44px; flex: 0 0 auto; }
  .chat-area { padding: 0.75rem; }
  .bubble-user, .bubble-ai { max-width: 90%; }
  .input-row { padding: 0.625rem 0.75rem max(0.625rem, env(safe-area-inset-bottom)); }
  .chat-input { min-width: 0; font-size: 1rem; }
  .send-btn { width: 44px; height: 44px; }
  .markdown-body :deep(table) { display: block; max-width: 100%; overflow-x: auto; }
  .markdown-body :deep(pre) { max-width: 100%; }
}
</style>
