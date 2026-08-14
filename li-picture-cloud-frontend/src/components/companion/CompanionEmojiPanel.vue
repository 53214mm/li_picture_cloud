<template>
  <section class="emoji-card" aria-labelledby="emoji-title">
    <header>
      <div>
        <span class="eyebrow">表情草稿</span>
        <h2 id="emoji-title">让伙伴从图片里挑一句俏皮话</h2>
      </div>
    </header>

    <div v-if="error" class="panel-error" role="alert">{{ error }}</div>

    <form class="emoji-create" @submit.prevent="submitCreate">
      <div v-if="pictures.length" class="emoji-pictures">
        <label v-for="picture in pictures.slice(0, MAX_PICTURES)" :key="picture.id"
               class="emoji-picture-choice"
               :class="{ checked: selectedId === String(picture.id) }">
          <input type="radio" name="emoji-source" :value="String(picture.id)" v-model="selectedId">
          <img :src="picture.thumbnailUrl || picture.url" :alt="picture.name || '图片'"
               loading="lazy" width="80" height="60">
          <span>{{ picture.name || '未命名图片' }}</span>
        </label>
      </div>
      <p v-else class="empty-state">图库里暂时没有可用图片。</p>
      <button class="btn" type="submit" :disabled="creating || !selectedId">
        {{ creating ? '正在开始创作…' : '开始生成表情' }}
      </button>
    </form>

    <ul v-if="tasks.length" class="emoji-list" data-testid="emoji-list">
      <li v-for="task in tasks" :key="task.id" class="emoji-row" :data-status="task.status">
        <div class="emoji-main">
          <span class="emoji-status" :class="{ done: task.status === 'SAVED' }">
            {{ creationStatusLabel(task.status) }}
          </span>
          <span class="emoji-source">来源图片 {{ task.sourcePictureIds.join('、') }}</span>
          <div v-if="task.status === 'AWAITING_CONFIRM' && candidates[task.id]?.length"
               class="emoji-candidates" role="radiogroup" :aria-label="`任务 ${task.id} 的表情候选`">
            <label v-for="candidate in candidates[task.id]" :key="candidate.seq"
                   class="emoji-candidate">
              <input type="radio" name="`emoji-pick-${task.id}`" :value="candidate.seq"
                     v-model="selectedCandidate[task.id]">
              <span>{{ candidate.text }}</span>
            </label>
          </div>
          <p v-if="task.status === 'SAVING'" class="emoji-text">{{ task.draftText }}</p>
          <p v-if="task.resultText" class="emoji-text emoji-result" data-testid="emoji-result">
            {{ task.resultText }}
          </p>
        </div>
        <div class="emoji-actions">
          <button v-if="task.status === 'PENDING'" class="btn btn-sm" type="button"
                  :disabled="busyTaskId !== null" @click="run('generate', task)">生成候选</button>
          <button v-if="task.status === 'AWAITING_CONFIRM'" class="btn btn-sm" type="button"
                  :disabled="busyTaskId !== null || selectedCandidate[task.id] == null"
                  @click="run('select', task)">选中保存</button>
          <button v-if="task.status === 'SAVING'" class="btn btn-sm" type="button"
                  :disabled="busyTaskId !== null" @click="run('save', task)">保存作品</button>
        </div>
      </li>
    </ul>
    <p v-else class="empty-state">还没有表情草稿。选一张图片，生成第一批候选吧。</p>
  </section>
</template>

<script setup>
import { onMounted, reactive, ref, watch } from 'vue'
import {
  createEmoji,
  generateEmoji,
  listEmojiCandidates,
  listEmojiTasks,
  saveEmoji,
  selectEmojiCandidate
} from '@/api/creation'
import { creationStatusLabel } from '@/constants/creation'

const MAX_PICTURES = 12

const props = defineProps({
  pictures: { type: Array, default: () => [] },
  refreshKey: { type: Number, default: 0 }
})

const tasks = ref([])
const candidates = reactive({})
const selectedCandidate = reactive({})
const selectedId = ref(null)
const creating = ref(false)
const busyTaskId = ref(null)
const error = ref('')
const pendingIdempotencyKey = ref(null)

onMounted(loadTasks)
watch(() => props.refreshKey, loadTasks)

async function loadTasks() {
  try {
    tasks.value = ((await listEmojiTasks()) ?? []).filter(task => task.kind === 'EMOJI_DRAFT')
    for (const task of tasks.value) {
      if (task.status === 'AWAITING_CONFIRM') {
        candidates[task.id] = (await listEmojiCandidates(task.id)) ?? []
      }
    }
    error.value = ''
  } catch (failure) {
    error.value = extractMessage(failure, '表情草稿加载失败')
  }
}

async function submitCreate() {
  creating.value = true
  try {
    if (!pendingIdempotencyKey.value) {
      pendingIdempotencyKey.value = crypto.randomUUID()
    }
    await createEmoji({
      pictureIds: [selectedId.value],
      idempotencyKey: pendingIdempotencyKey.value
    })
    selectedId.value = null
    pendingIdempotencyKey.value = null
    await loadTasks()
  } catch (failure) {
    error.value = extractMessage(failure, '创作开始失败')
  } finally {
    creating.value = false
  }
}

async function run(action, task) {
  busyTaskId.value = task.id
  try {
    if (action === 'generate') await generateEmoji(task.id)
    if (action === 'select') {
      await selectEmojiCandidate(task.id, selectedCandidate[task.id])
    }
    if (action === 'save') await saveEmoji(task.id)
    await loadTasks()
  } catch (failure) {
    error.value = extractMessage(failure, '表情草稿步骤失败，请重试')
    await loadTasks()
  } finally {
    busyTaskId.value = null
  }
}

function extractMessage(failure, fallback) {
  return failure?.message || fallback
}
</script>

<style scoped>
.emoji-card { border: 2px solid var(--black); background: var(--white); }
.emoji-card > header { padding: 1.25rem 1.5rem; border-bottom: 2px solid var(--black); }
.emoji-card h2 { font-size: 1.35rem; }
.eyebrow { color: #075d2a; font-size: .68rem; font-weight: 800; letter-spacing: .12em; text-transform: uppercase; }
.panel-error { margin: 1rem 1.5rem 0; padding: .6rem .8rem; border-left: 4px solid var(--red); background: var(--gray-100); color: var(--red); font-size: .8rem; }
.empty-state { padding: 1.5rem; color: var(--gray-600); font-size: .9rem; }
.emoji-create { padding: 1.25rem 1.5rem; display: grid; gap: .9rem; border-bottom: 2px solid var(--black); }
.emoji-pictures { display: grid; grid-template-columns: repeat(auto-fill, minmax(7rem, 1fr)); gap: .6rem; }
.emoji-picture-choice { display: grid; gap: .3rem; padding: .4rem; border: 2px solid var(--gray-200); cursor: pointer; font-size: .72rem; font-weight: 700; }
.emoji-picture-choice.checked { border-color: var(--blue); box-shadow: 0 0 0 2px var(--blue); }
.emoji-picture-choice input { position: absolute; opacity: 0; }
.emoji-picture-choice img { width: 100%; aspect-ratio: 4 / 3; object-fit: cover; background: var(--gray-100); }
.emoji-list { list-style: none; }
.emoji-row { display: grid; grid-template-columns: minmax(0, 1fr) auto; gap: .8rem; padding: 1rem 1.5rem; border-bottom: 1px solid var(--gray-200); }
.emoji-main { display: grid; gap: .35rem; }
.emoji-status { justify-self: start; padding: .15rem .45rem; background: var(--gray-100); border: 1px solid var(--gray-400); font-size: .72rem; font-weight: 700; }
.emoji-status.done { background: #e6f4ea; border-color: #075d2a; color: #075d2a; }
.emoji-source { font-size: .75rem; color: var(--gray-600); }
.emoji-candidates { display: grid; gap: .4rem; }
.emoji-candidate { display: flex; gap: .5rem; align-items: center; padding: .45rem .6rem; border: 1px solid var(--gray-300); cursor: pointer; font-size: .85rem; }
.emoji-candidate:has(input:checked) { border-color: var(--blue); box-shadow: 0 0 0 2px var(--blue); }
.emoji-text { white-space: pre-wrap; overflow-wrap: anywhere; font-size: .88rem; line-height: 1.6; }
.emoji-result { border-left: 4px solid #075d2a; padding-left: .7rem; }
.emoji-actions { display: flex; gap: .5rem; align-items: flex-start; }
@media (max-width: 767px) {
  .emoji-row { grid-template-columns: 1fr; }
}
</style>
