<template>
  <section class="emoji-card" aria-labelledby="emoji-title">
    <header>
      <div>
        <span class="eyebrow">表情草稿</span>
        <h2 id="emoji-title">让伙伴从图片里挑一句俏皮话</h2>
      </div>
    </header>

    <div v-if="error" class="panel-error" role="alert">{{ error }}</div>

    <p class="panel-notice" role="status" data-testid="emoji-unavailable">
      文字表情草稿暂未开放：生成候选需要先对授权图片做视觉理解，提取其中的人物、动作、
      表情或物体元素。在视觉理解能力落地前不会生成候选，也不会调用任何模型。
    </p>

    <ul v-if="tasks.length" class="emoji-list" data-testid="emoji-list">
      <li v-for="task in tasks" :key="task.id" class="emoji-row" :data-status="task.status">
        <div class="emoji-main">
          <span class="emoji-status" :class="{ done: task.status === 'SAVED' }">
            {{ creationStatusLabel(task.status) }}
          </span>
          <span class="emoji-source">来源图片 {{ task.sourcePictureIds.join('、') }}</span>
          <div v-if="task.status === 'AWAITING_CONFIRM' && candidates[task.id]?.length"
               class="emoji-candidates" :aria-label="`任务 ${task.id} 的表情候选`">
            <p v-for="candidate in candidates[task.id]" :key="candidate.seq" class="emoji-candidate">
              {{ candidate.text }}
            </p>
          </div>
          <p v-if="task.status === 'SAVING'" class="emoji-text">{{ task.draftText }}</p>
          <p v-if="task.resultText" class="emoji-text emoji-result" data-testid="emoji-result">
            {{ task.resultText }}
          </p>
          <p v-if="task.status === 'PENDING' || task.status === 'AWAITING_CONFIRM'"
             class="emoji-not-open-note">
            该任务等待的表情草稿能力尚未开放，暂时无法继续。
          </p>
        </div>
      </li>
    </ul>
    <p v-else class="empty-state">还没有表情草稿。视觉理解能力开放后，选一张图片即可生成第一批候选。</p>
  </section>
</template>

<script setup>
import { onMounted, reactive, ref, watch } from 'vue'
import { listEmojiCandidates, listEmojiTasks } from '@/api/creation'
import { creationStatusLabel } from '@/constants/creation'

const props = defineProps({
  pictures: { type: Array, default: () => [] },
  refreshKey: { type: Number, default: 0 }
})

const tasks = ref([])
const candidates = reactive({})
const error = ref('')

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
.panel-notice { margin: 1rem 1.5rem 0; padding: .75rem 1rem; border-left: 4px solid var(--blue); background: var(--gray-100); color: var(--blue); font-size: .85rem; font-weight: 700; }
.empty-state { padding: 1.5rem; color: var(--gray-600); font-size: .9rem; }
.emoji-not-open-note { margin-top: .5rem; color: #8a6d1a; font-size: .78rem; font-weight: 700; }
.emoji-list { list-style: none; }
.emoji-row { display: grid; grid-template-columns: minmax(0, 1fr); gap: .8rem; padding: 1rem 1.5rem; border-bottom: 1px solid var(--gray-200); }
.emoji-main { display: grid; gap: .35rem; }
.emoji-status { justify-self: start; padding: .15rem .45rem; background: var(--gray-100); border: 1px solid var(--gray-400); font-size: .72rem; font-weight: 700; }
.emoji-status.done { background: #e6f4ea; border-color: #075d2a; color: #075d2a; }
.emoji-source { font-size: .75rem; color: var(--gray-600); }
.emoji-candidates { display: grid; gap: .4rem; }
.emoji-candidate { padding: .45rem .6rem; border: 1px solid var(--gray-300); font-size: .85rem; }
.emoji-text { white-space: pre-wrap; overflow-wrap: anywhere; font-size: .88rem; line-height: 1.6; }
.emoji-result { border-left: 4px solid #075d2a; padding-left: .7rem; }
</style>
