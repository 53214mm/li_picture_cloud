<template>
  <section class="fusion-card" aria-labelledby="fusion-title">
    <header>
      <div>
        <span class="eyebrow">多图融合</span>
        <h2 id="fusion-title">把多张图片融合成一张新作品</h2>
      </div>
    </header>

    <div v-if="error" class="panel-error" role="alert">{{ error }}</div>

    <form class="fusion-create" @submit.prevent="submitCreate">
      <div v-if="pictures.length" class="fusion-pictures">
        <label v-for="picture in pictures.slice(0, MAX_PICTURES)" :key="picture.id"
               class="fusion-picture-choice"
               :class="{ checked: selectedIds.includes(String(picture.id)) }">
          <input type="checkbox" :value="String(picture.id)" v-model="selectedIds">
          <img :src="picture.thumbnailUrl || picture.url" :alt="picture.name || '图片'"
               loading="lazy" width="80" height="60">
          <span>{{ picture.name || '未命名图片' }}</span>
        </label>
      </div>
      <p v-else class="empty-state">图库里暂时没有可用图片，先上传或喂几张图片吧。</p>
      <p v-if="selectedIds.length === 1" class="fusion-hint">
        多图融合至少需要 2 张图片（当前已选 1 张）。
      </p>
      <button class="btn" type="submit" :disabled="creating || selectedIds.length < 2">
        {{ creating ? '正在开始创作…' : '开始融合创作' }}
      </button>
    </form>

    <ul v-if="tasks.length" class="fusion-list" data-testid="fusion-list">
      <li v-for="task in tasks" :key="task.id" class="fusion-row"
          :data-status="task.status">
        <div class="fusion-main">
          <span class="fusion-status" :class="{ done: task.status === 'SAVED' }">
            {{ creationStatusLabel(task.status) }}
          </span>
          <span class="fusion-source">来源图片 {{ task.sourcePictureIds.join('、') }}</span>
          <img v-if="hasPreview(task)" class="fusion-preview" :src="previewUrl(task)"
               :alt="`融合结果预览（任务 ${task.id}）`" loading="lazy">
          <p v-if="task.resultText" class="fusion-result" data-testid="fusion-result">
            已保存为新图片 #{{ task.resultText }}
          </p>
        </div>
        <div class="fusion-actions">
          <button v-if="task.status === 'PENDING'" class="btn btn-sm" type="button"
                  :disabled="busyTaskId !== null" @click="run('generate', task)">生成融合图</button>
          <template v-if="task.status === 'AWAITING_CONFIRM'">
            <select v-model="spaceSelections[task.id]" class="fusion-space"
                    :aria-label="`任务 ${task.id} 的目标空间`">
              <option :value="null" disabled>选择目标空间</option>
              <option v-for="space in spaces" :key="space.id" :value="String(space.id)">
                {{ space.spaceName || `空间 ${space.id}` }}
              </option>
            </select>
            <input v-model="names[task.id]" type="text" class="fusion-name"
                   :aria-label="`任务 ${task.id} 的作品名`" maxlength="128"
                   placeholder="作品名（可选）">
            <button class="btn btn-sm" type="button"
                    :disabled="busyTaskId !== null || !spaceSelections[task.id]"
                    @click="run('save', task)">保存到图库</button>
          </template>
        </div>
      </li>
    </ul>
    <p v-else class="empty-state">还没有融合作品。选好至少两张图片，开始第一次融合吧。</p>
  </section>
</template>

<script setup>
import { onMounted, reactive, ref, watch } from 'vue'
import {
  createFusion,
  fusionPreviewUrl,
  generateFusion,
  listFusionTasks,
  saveFusion
} from '@/api/creation'
import { listSpaceVOByPage } from '@/api/space'
import { useUserStore } from '@/stores/user'
import { creationStatusLabel } from '@/constants/creation'

const MAX_PICTURES = 12

const props = defineProps({
  pictures: { type: Array, default: () => [] },
  refreshKey: { type: Number, default: 0 }
})

const userStore = useUserStore()
const tasks = ref([])
const spaces = ref([])
const selectedIds = ref([])
const spaceSelections = reactive({})
const names = reactive({})
const creating = ref(false)
const busyTaskId = ref(null)
const error = ref('')
const pendingIdempotencyKey = ref(null)

onMounted(() => {
  loadTasks()
  loadSpaces()
})
watch(() => props.refreshKey, loadTasks)
watch(() => userStore.currentUser?.id, () => {
  if (userStore.currentUser?.id != null) loadSpaces()
})

async function loadTasks() {
  try {
    tasks.value = ((await listFusionTasks()) ?? []).filter(task => task.kind === 'IMAGE_FUSION')
    error.value = ''
  } catch (failure) {
    error.value = extractMessage(failure, '融合任务加载失败')
  }
}

async function loadSpaces() {
  try {
    const userId = userStore.currentUser?.id
    if (userId == null) return
    const params = {
      current: 1,
      pageSize: 20,
      userId: String(userId),
      sortField: 'createTime',
      sortOrder: 'descend'
    }
    const page = await listSpaceVOByPage(params)
    spaces.value = page?.records ?? []
  } catch (failure) {
    error.value = extractMessage(failure, '空间列表加载失败')
  }
}

function hasPreview(task) {
  return ['AWAITING_CONFIRM', 'SAVING', 'SAVED'].includes(task.status)
}

function previewUrl(task) {
  // 版本号变化时强制重新拉取，避免确认后仍显示旧缓存。
  return `${fusionPreviewUrl(task.id)}?r=${task.revision}`
}

async function submitCreate() {
  creating.value = true
  try {
    if (!pendingIdempotencyKey.value) {
      pendingIdempotencyKey.value = crypto.randomUUID()
    }
    await createFusion({
      pictureIds: selectedIds.value,
      idempotencyKey: pendingIdempotencyKey.value
    })
    selectedIds.value = []
    pendingIdempotencyKey.value = null
    await loadTasks()
  } catch (failure) {
    error.value = extractMessage(failure, '融合创作开始失败')
  } finally {
    creating.value = false
  }
}

async function run(action, task) {
  busyTaskId.value = task.id
  try {
    if (action === 'generate') await generateFusion(task.id)
    if (action === 'save') {
      await saveFusion(task.id, {
        spaceId: spaceSelections[task.id],
        name: names[task.id] || null
      })
    }
    await loadTasks()
  } catch (failure) {
    error.value = extractMessage(failure, '融合步骤失败，请重试')
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
.fusion-card { border: 2px solid var(--black); background: var(--white); }
.fusion-card > header { padding: 1.25rem 1.5rem; border-bottom: 2px solid var(--black); }
.fusion-card h2 { font-size: 1.35rem; }
.eyebrow { color: #075d2a; font-size: .68rem; font-weight: 800; letter-spacing: .12em; text-transform: uppercase; }
.panel-error { margin: 1rem 1.5rem 0; padding: .6rem .8rem; border-left: 4px solid var(--red); background: var(--gray-100); color: var(--red); font-size: .8rem; }
.empty-state { padding: 1.5rem; color: var(--gray-600); font-size: .9rem; }
.fusion-create { padding: 1.25rem 1.5rem; display: grid; gap: .9rem; border-bottom: 2px solid var(--black); }
.fusion-pictures { display: grid; grid-template-columns: repeat(auto-fill, minmax(7rem, 1fr)); gap: .6rem; }
.fusion-picture-choice { display: grid; gap: .3rem; padding: .4rem; border: 2px solid var(--gray-200); cursor: pointer; font-size: .72rem; font-weight: 700; }
.fusion-picture-choice.checked { border-color: var(--blue); box-shadow: 0 0 0 2px var(--blue); }
.fusion-picture-choice input { position: absolute; opacity: 0; }
.fusion-picture-choice:has(input:focus-visible) { outline: 3px solid var(--blue); outline-offset: 2px; }
.fusion-picture-choice img { width: 100%; aspect-ratio: 4 / 3; object-fit: cover; background: var(--gray-100); }
.fusion-hint { color: #8a6d1a; font-size: .8rem; font-weight: 700; }
.fusion-list { list-style: none; }
.fusion-row { display: grid; grid-template-columns: minmax(0, 1fr) auto; gap: .8rem; padding: 1rem 1.5rem; border-bottom: 1px solid var(--gray-200); }
.fusion-main { display: grid; gap: .35rem; }
.fusion-status { justify-self: start; padding: .15rem .45rem; background: var(--gray-100); border: 1px solid var(--gray-400); font-size: .72rem; font-weight: 700; }
.fusion-status.done { background: #e6f4ea; border-color: #075d2a; color: #075d2a; }
.fusion-source { font-size: .75rem; color: var(--gray-600); }
.fusion-preview { width: min(18rem, 100%); border: 2px solid var(--black); background: var(--gray-100); }
.fusion-result { border-left: 4px solid #075d2a; padding-left: .7rem; font-size: .88rem; font-weight: 700; }
.fusion-actions { display: grid; gap: .5rem; align-content: start; justify-items: start; }
.fusion-space { min-width: 11rem; padding: .4rem .5rem; border: 2px solid var(--gray-400); font-size: .85rem; }
.fusion-name { width: 11rem; padding: .4rem .5rem; border: 2px solid var(--gray-400); font-size: .85rem; }
@media (max-width: 767px) {
  .fusion-row { grid-template-columns: 1fr; }
}
</style>
