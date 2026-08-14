<template>
  <section class="memory-card" aria-labelledby="memory-title">
    <header>
      <div>
        <span class="eyebrow">伙伴记忆</span>
        <h2 id="memory-title">它记得的事</h2>
      </div>
      <button class="refresh-button" type="button" :disabled="loading" @click="loadMemories">
        {{ loading ? '正在读取…' : '刷新' }}
      </button>
    </header>

    <div v-if="loadError" class="memory-state error" role="alert">
      <p>{{ loadError }}</p>
      <button class="btn btn-outline" type="button" @click="loadMemories">重试</button>
    </div>
    <div v-else-if="loading && !memories.length" class="memory-state">正在读取伙伴的记忆…</div>
    <div v-else-if="!memories.length" class="memory-state">
      伙伴还没有形成记忆。完成一次真实内容理解的喂养后，它会在这里记下候选记忆。
    </div>
    <ul v-else class="memory-list">
      <li v-for="memory in memories" :key="memory.id" class="memory-item"
          :data-testid="`memory-${memory.id}`">
        <div class="memory-meta">
          <span class="status-badge" :class="statusMeta(memory.status).tone"
                data-testid="memory-status">{{ statusMeta(memory.status).label }}</span>
          <span v-if="memory.confidence != null">置信度 {{ Number(memory.confidence).toFixed(2) }}</span>
          <time :datetime="memory.createdTime">{{ formatTime(memory.createdTime) }}</time>
        </div>

        <template v-if="memory.content">
          <CompanionMessageBubble :message="memory.content" />
          <div class="memory-source">
            <router-link v-if="memory.sourcePictureId" :to="`/picture/${memory.sourcePictureId}`">
              来源图片 #{{ shortId(memory.sourcePictureId) }}
            </router-link>
            <span v-else>来源：伙伴成长记录</span>
          </div>
        </template>
        <p v-else class="invalidated-note">
          这条记忆的来源图片已不可用，内容不再展示。删除后可以从档案中移除。
        </p>

        <div v-if="correctingId !== memory.id && availableActions(memory.status).length" class="memory-actions">
          <button v-for="action in availableActions(memory.status)" :key="action.key"
                  class="memory-action" :class="action.tone"
                  type="button" :disabled="busy" :data-action="action.key"
                  @click="runAction(memory, action.key)">
            {{ action.label }}
          </button>
        </div>
        <form v-if="correctingId === memory.id" class="correct-editor" @submit.prevent="saveCorrection(memory)">
          <label for="memory-correct-input">纠正这条记忆</label>
          <textarea id="memory-correct-input" v-model="correctContent" maxlength="300" rows="3"></textarea>
          <p class="correct-error" v-if="actionError" role="alert">{{ actionError }}</p>
          <div class="correct-actions">
            <button class="btn btn-primary" type="submit" :disabled="busy || !correctContent.trim()">
              {{ busy ? '正在保存…' : '保存纠正' }}
            </button>
            <button class="btn btn-outline" type="button" :disabled="busy" @click="cancelCorrection">取消</button>
          </div>
        </form>
        <p v-if="actionError && correctingId !== memory.id" class="action-error" role="alert">{{ actionError }}</p>
      </li>
    </ul>
  </section>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { MEMORY_STATUS } from '@/constants/companion'
import CompanionMessageBubble from '@/components/companion/CompanionMessageBubble.vue'
import {
  listCompanionMemories,
  confirmCompanionMemory,
  correctCompanionMemory,
  dismissCompanionMemory,
  deleteCompanionMemory
} from '@/api/companion'

const memories = ref([])
const loading = ref(false)
const loadError = ref('')
const busy = ref(false)
const actionError = ref('')
const correctingId = ref(null)
const correctContent = ref('')

const ACTIONS = Object.freeze({
  confirm: { key: 'confirm', label: '确认', tone: 'confirm' },
  correct: { key: 'correct', label: '纠正', tone: 'correct' },
  dismiss: { key: 'dismiss', label: '忽略', tone: 'dismiss' },
  delete: { key: 'delete', label: '删除', tone: 'delete' }
})

onMounted(loadMemories)

function statusMeta(status) {
  return MEMORY_STATUS[status] || { label: status || '未知', tone: 'pending' }
}

function availableActions(status) {
  switch (status) {
    case 'PENDING':
      return [ACTIONS.confirm, ACTIONS.correct, ACTIONS.dismiss, ACTIONS.delete]
    case 'CONFIRMED':
      return [ACTIONS.correct, ACTIONS.dismiss, ACTIONS.delete]
    case 'DISMISSED':
      return [ACTIONS.confirm, ACTIONS.correct, ACTIONS.delete]
    default:
      return status === 'INVALIDATED' ? [ACTIONS.delete] : []
  }
}

async function loadMemories() {
  loading.value = true
  loadError.value = ''
  try {
    memories.value = (await listCompanionMemories()).records || []
  } catch (error) {
    loadError.value = error.message || '记忆读取失败，请稍后重试。'
  } finally {
    loading.value = false
  }
}

async function runAction(memory, action) {
  if (busy.value) return
  if (action === 'correct') {
    correctingId.value = memory.id
    correctContent.value = memory.content || ''
    actionError.value = ''
    return
  }
  busy.value = true
  actionError.value = ''
  try {
    const updated = await (action === 'confirm'
      ? confirmCompanionMemory(memory.id)
      : action === 'dismiss'
        ? dismissCompanionMemory(memory.id)
        : deleteCompanionMemory(memory.id))
    replaceMemory(updated)
  } catch (error) {
    actionError.value = error.message || '操作失败，请稍后重试。'
  } finally {
    busy.value = false
  }
}

async function saveCorrection(memory) {
  if (busy.value || !correctContent.value.trim()) return
  busy.value = true
  actionError.value = ''
  try {
    const updated = await correctCompanionMemory(memory.id, correctContent.value.trim())
    replaceMemory(updated)
    correctingId.value = null
    correctContent.value = ''
  } catch (error) {
    actionError.value = error.message || '纠正失败，请稍后重试。'
  } finally {
    busy.value = false
  }
}

function cancelCorrection() {
  correctingId.value = null
  correctContent.value = ''
  actionError.value = ''
}

function replaceMemory(updated) {
  const index = memories.value.findIndex(memory => memory.id === updated.id)
  if (index === -1) return
  // 已删除的记忆直接从列表移除；其余状态原地更新徽章与文案。
  if (updated.status === 'DELETED') memories.value.splice(index, 1)
  else memories.value[index] = updated
}

function shortId(id) {
  return String(id).slice(-6)
}

function formatTime(value) {
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? String(value) : new Intl.DateTimeFormat('zh-CN', {
    month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit'
  }).format(date)
}
</script>

<style scoped>
.memory-card { border: 2px solid var(--black); background: var(--white); }
.memory-card > header { display: flex; align-items: flex-start; justify-content: space-between; gap: 1rem; padding: 1.25rem 1.5rem; border-bottom: 2px solid var(--black); }
.memory-card h2 { font-size: 1.35rem; }
.eyebrow { color: var(--red); font-size: .68rem; font-weight: 800; letter-spacing: .12em; text-transform: uppercase; }
.refresh-button { padding: .45rem .7rem; border: 2px solid var(--black); background: var(--white); font-size: .75rem; font-weight: 800; cursor: pointer; }
.refresh-button:hover:not(:disabled) { background: var(--yellow); }
.refresh-button:disabled { cursor: not-allowed; opacity: .5; }
.memory-state { padding: 2rem 1.5rem; color: var(--gray-600); }
.memory-state.error { color: var(--red); }
.memory-state .btn { margin-top: 1rem; }
.memory-list { display: grid; gap: 1rem; padding: 1.25rem 1.5rem 1.5rem; }
.memory-item { padding: 1rem; background: var(--gray-100); border-left: 4px solid var(--black); }
.memory-meta { display: flex; flex-wrap: wrap; gap: .5rem; align-items: center; color: var(--gray-600); font-size: .72rem; }
.status-badge { padding: .2rem .45rem; border: 1px solid currentColor; font-weight: 800; }
.status-badge.pending { color: #8a6d1a; }
.status-badge.confirmed { color: #075d2a; }
.status-badge.dismissed { color: var(--gray-600); }
.status-badge.invalidated { color: var(--red); }
.status-badge.deleted { color: var(--gray-600); }
.memory-source { margin: .6rem 0 0 .65rem; font-size: .75rem; }
.memory-source a { color: var(--blue); font-weight: 700; text-decoration: underline; text-underline-offset: 2px; }
.invalidated-note { margin-top: .6rem; padding: .6rem .8rem; border: 1px dashed var(--gray-400); color: var(--gray-600); font-size: .78rem; }
.memory-actions { display: flex; flex-wrap: wrap; gap: .5rem; margin-top: .85rem; }
.memory-action { min-height: 44px; padding: .35rem .7rem; border: 2px solid var(--black); background: var(--white); font-size: .75rem; font-weight: 800; cursor: pointer; }
.memory-action:hover:not(:disabled) { background: var(--yellow); }
.memory-action.confirm:hover:not(:disabled) { background: #d9f2e3; }
.memory-action.delete { border-color: var(--red); color: var(--red); }
.memory-action.delete:hover:not(:disabled) { background: #fdeaea; }
.memory-action:disabled { cursor: not-allowed; opacity: .5; }
.correct-editor { margin-top: .85rem; padding: .8rem; border: 2px dashed var(--gray-400); background: var(--white); }
.correct-editor label { display: block; margin-bottom: .4rem; font-size: .78rem; font-weight: 800; }
.correct-editor textarea { width: 100%; padding: .5rem; border: 2px solid var(--black); font: inherit; font-size: .85rem; resize: vertical; }
.correct-actions { display: flex; gap: .5rem; margin-top: .6rem; }
.correct-actions .btn { min-height: 44px; font-size: .78rem; }
.correct-error, .action-error { margin-top: .5rem; color: var(--red); font-size: .78rem; }
@media (max-width: 767px) {
  .memory-card > header, .memory-list { padding-inline: 1.25rem; }
}
</style>
