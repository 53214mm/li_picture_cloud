<template>
  <section class="story-card" aria-labelledby="story-title">
    <header>
      <div>
        <span class="eyebrow">图片故事</span>
        <h2 id="story-title">让伙伴为图片写一段故事</h2>
      </div>
    </header>

    <div v-if="error" class="panel-error" role="alert">{{ error }}</div>

    <form class="story-create" @submit.prevent="submitCreate">
      <div v-if="pictures.length" class="story-pictures">
        <label v-for="picture in pictures.slice(0, MAX_PICTURES)" :key="picture.id"
               class="story-picture-choice"
               :class="{ checked: selectedIds.includes(String(picture.id)) }">
          <input type="checkbox" :value="String(picture.id)" v-model="selectedIds">
          <img :src="picture.thumbnailUrl || picture.url" :alt="picture.name || '图片'"
               loading="lazy" width="80" height="60">
          <span>{{ picture.name || '未命名图片' }}</span>
        </label>
      </div>
      <p v-else class="empty-state">图库里暂时没有可用图片，先上传或喂一张图片吧。</p>
      <button class="btn" type="submit" :disabled="creating || selectedIds.length === 0">
        {{ creating ? '正在开始创作…' : '开始创作故事' }}
      </button>
    </form>

    <ul v-if="tasks.length" class="story-list" data-testid="story-list">
      <li v-for="task in tasks" :key="task.id" class="story-row"
          :data-status="task.status">
        <div class="story-main">
          <span class="story-status" :class="{ done: task.status === 'SAVED' }">
            {{ creationStatusLabel(task.status) }}
          </span>
          <span class="story-source">来源图片 {{ task.sourcePictureIds.join('、') }}</span>
          <p v-if="task.outlineText && !task.draftText" class="story-text">
            {{ task.outlineText }}
          </p>
          <p v-if="task.draftText && task.status !== 'SAVED'" class="story-text">
            {{ task.draftText }}
          </p>
          <p v-if="task.resultText" class="story-text story-result" data-testid="story-result">
            {{ task.resultText }}
          </p>
        </div>
        <div class="story-actions">
          <button v-if="task.status === 'PENDING'" class="btn btn-sm" type="button"
                  :disabled="busyTaskId !== null" @click="run('outline', task)">生成大纲</button>
          <button v-if="task.status === 'AWAITING_CONFIRM' && task.outlineText && !task.draftText"
                  class="btn btn-sm" type="button" :disabled="busyTaskId !== null"
                  @click="run('draft', task)">生成草稿</button>
          <button v-if="task.status === 'AWAITING_CONFIRM' && task.draftText"
                  class="btn btn-sm" type="button" :disabled="busyTaskId !== null"
                  @click="run('save', task)">保存作品</button>
        </div>
      </li>
    </ul>
    <p v-else class="empty-state">还没有故事。选好图片，开始第一次创作吧。</p>
  </section>
</template>

<script setup>
import { onMounted, ref, watch } from 'vue'
import {
  createStory,
  draftStory,
  listStories,
  outlineStory,
  saveStory
} from '@/api/creation'
import { creationStatusLabel } from '@/constants/creation'

const MAX_PICTURES = 12

const props = defineProps({
  pictures: { type: Array, default: () => [] },
  refreshKey: { type: Number, default: 0 }
})

const tasks = ref([])
const selectedIds = ref([])
const creating = ref(false)
const busyTaskId = ref(null)
const error = ref('')

onMounted(loadTasks)
watch(() => props.refreshKey, loadTasks)

async function loadTasks() {
  try {
    tasks.value = (await listStories()) ?? []
    error.value = ''
  } catch (failure) {
    error.value = extractMessage(failure, '故事列表加载失败')
  }
}

async function submitCreate() {
  creating.value = true
  try {
    await createStory({
      pictureIds: selectedIds.value,
      idempotencyKey: crypto.randomUUID()
    })
    selectedIds.value = []
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
    if (action === 'outline') await outlineStory(task.id)
    if (action === 'draft') await draftStory(task.id)
    if (action === 'save') await saveStory(task.id)
    await loadTasks()
  } catch (failure) {
    error.value = extractMessage(failure, '故事步骤失败，请重试')
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
.story-card { border: 2px solid var(--black); background: var(--white); }
.story-card > header { padding: 1.25rem 1.5rem; border-bottom: 2px solid var(--black); }
.story-card h2 { font-size: 1.35rem; }
.eyebrow { color: #075d2a; font-size: .68rem; font-weight: 800; letter-spacing: .12em; text-transform: uppercase; }
.panel-error { margin: 1rem 1.5rem 0; padding: .6rem .8rem; border-left: 4px solid var(--red); background: var(--gray-100); color: var(--red); font-size: .8rem; }
.empty-state { padding: 1.5rem; color: var(--gray-600); font-size: .9rem; }
.story-create { padding: 1.25rem 1.5rem; display: grid; gap: .9rem; border-bottom: 2px solid var(--black); }
.story-pictures { display: grid; grid-template-columns: repeat(auto-fill, minmax(7rem, 1fr)); gap: .6rem; }
.story-picture-choice { display: grid; gap: .3rem; padding: .4rem; border: 2px solid var(--gray-200); cursor: pointer; font-size: .72rem; font-weight: 700; }
.story-picture-choice.checked { border-color: var(--blue); box-shadow: 0 0 0 2px var(--blue); }
.story-picture-choice input { position: absolute; opacity: 0; }
.story-picture-choice img { width: 100%; aspect-ratio: 4 / 3; object-fit: cover; background: var(--gray-100); }
.story-list { list-style: none; }
.story-row { display: grid; grid-template-columns: minmax(0, 1fr) auto; gap: .8rem; padding: 1rem 1.5rem; border-bottom: 1px solid var(--gray-200); }
.story-main { display: grid; gap: .35rem; }
.story-status { justify-self: start; padding: .15rem .45rem; background: var(--gray-100); border: 1px solid var(--gray-400); font-size: .72rem; font-weight: 700; }
.story-status.done { background: #e6f4ea; border-color: #075d2a; color: #075d2a; }
.story-source { font-size: .75rem; color: var(--gray-600); }
.story-text { white-space: pre-wrap; overflow-wrap: anywhere; font-size: .88rem; line-height: 1.6; }
.story-result { border-left: 4px solid #075d2a; padding-left: .7rem; }
.story-actions { display: flex; gap: .5rem; align-items: flex-start; }
@media (max-width: 767px) {
  .story-row { grid-template-columns: 1fr; }
}
</style>
