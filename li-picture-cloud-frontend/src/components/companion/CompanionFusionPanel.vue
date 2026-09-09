<template>
  <section class="fusion-card" aria-labelledby="fusion-title">
    <header>
      <div>
        <span class="eyebrow">多图融合</span>
        <h2 id="fusion-title">把多张图片融合成一张新作品</h2>
      </div>
    </header>

    <div v-if="error" class="panel-error" role="alert">{{ error }}</div>

    <p class="panel-notice" role="status" data-testid="fusion-unavailable">
      真实多图融合能力尚未开放：当前图像模型适配器不支持把多张授权图片作为参考图输入。
      需要支持图片编辑/多参考图的适配器完成供应商能力验证后才会开放，届时这里会恢复入口。
    </p>

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
          <p v-if="task.status === 'PENDING' || task.status === 'AWAITING_CONFIRM'"
             class="fusion-not-open-note">
            该任务等待的融合生成能力尚未开放，暂时无法继续。
          </p>
        </div>
      </li>
    </ul>
    <p v-else class="empty-state">还没有融合作品。多图融合开放后，选好至少两张图片即可开始。</p>
  </section>
</template>

<script setup>
import { onMounted, ref, watch } from 'vue'
import { fusionPreviewUrl, listFusionTasks } from '@/api/creation'
import { creationStatusLabel } from '@/constants/creation'

const props = defineProps({
  pictures: { type: Array, default: () => [] },
  refreshKey: { type: Number, default: 0 }
})

const tasks = ref([])
const error = ref('')

onMounted(loadTasks)
watch(() => props.refreshKey, loadTasks)

async function loadTasks() {
  try {
    tasks.value = ((await listFusionTasks()) ?? []).filter(task => task.kind === 'IMAGE_FUSION')
    error.value = ''
  } catch (failure) {
    error.value = extractMessage(failure, '融合任务加载失败')
  }
}

function hasPreview(task) {
  return ['AWAITING_CONFIRM', 'SAVING', 'SAVED'].includes(task.status)
}

function previewUrl(task) {
  // 版本号变化时强制重新拉取，避免确认后仍显示旧缓存。
  return `${fusionPreviewUrl(task.id)}?r=${task.revision}`
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
.panel-notice { margin: 1rem 1.5rem 0; padding: .75rem 1rem; border-left: 4px solid var(--blue); background: var(--gray-100); color: var(--blue); font-size: .85rem; font-weight: 700; }
.empty-state { padding: 1.5rem; color: var(--gray-600); font-size: .9rem; }
.fusion-not-open-note { margin-top: .5rem; color: #8a6d1a; font-size: .78rem; font-weight: 700; }
.fusion-list { list-style: none; }
.fusion-row { display: grid; grid-template-columns: minmax(0, 1fr); gap: .8rem; padding: 1rem 1.5rem; border-bottom: 1px solid var(--gray-200); }
.fusion-main { display: grid; gap: .35rem; }
.fusion-status { justify-self: start; padding: .15rem .45rem; background: var(--gray-100); border: 1px solid var(--gray-400); font-size: .72rem; font-weight: 700; }
.fusion-status.done { background: #e6f4ea; border-color: #075d2a; color: #075d2a; }
.fusion-source { font-size: .75rem; color: var(--gray-600); }
.fusion-preview { width: min(18rem, 100%); border: 2px solid var(--black); background: var(--gray-100); }
.fusion-result { border-left: 4px solid #075d2a; padding-left: .7rem; font-size: .88rem; font-weight: 700; }
</style>
