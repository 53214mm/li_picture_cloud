<template>
  <ol class="feed-timeline" aria-label="伙伴喂养链路阶段">
    <li v-for="stage in timeline" :key="stage.code" class="feed-stage" :class="stageClass(stage.status)">
      <span class="stage-dot" aria-hidden="true"></span>
      <div class="stage-main">
        <div class="stage-heading">
          <strong>{{ stage.label }}</strong>
          <span class="stage-status">{{ stageStatusLabel(stage.status) }}</span>
        </div>
        <p>{{ stage.description }}</p>
        <time v-if="stage.occurredAt" :datetime="stage.occurredAt">{{ formatObservationDateTime(stage.occurredAt) }}</time>
      </div>
    </li>
  </ol>
</template>

<script setup>
import {
  formatObservationDateTime,
  stageClass,
  stageStatusLabel
} from '@/utils/companionObservation'

defineProps({
  timeline: {
    type: Array,
    default: () => []
  }
})
</script>

<style scoped>
.feed-timeline { list-style: none; margin: 0; padding: 0; }
.feed-stage { position: relative; display: grid; grid-template-columns: 1rem 1fr; gap: 0.9rem; min-height: 5.5rem; }
.feed-stage:not(:last-child)::after { content: ''; position: absolute; left: 0.45rem; top: 1rem; bottom: 0; width: 2px; background: var(--gray-200); }
.stage-dot { position: relative; z-index: 1; width: 0.9rem; height: 0.9rem; margin-top: 0.2rem; border: 3px solid var(--gray-300); border-radius: 50%; background: var(--white); }
.stage-main { padding-bottom: 1.2rem; }
.stage-heading { display: flex; align-items: center; gap: 0.6rem; flex-wrap: wrap; }
.stage-heading strong { font-size: 0.95rem; }
.stage-status { padding: 0.15rem 0.45rem; font-size: 0.72rem; font-weight: 700; background: var(--gray-100); color: var(--gray-600); }
.stage-main p { margin: 0.35rem 0 0; color: var(--gray-600); font-size: 0.85rem; line-height: 1.6; }
.stage-main time { display: block; margin-top: 0.35rem; color: var(--gray-400); font-size: 0.75rem; }
.is-success .stage-dot { border-color: #2d7a4b; background: #e9f6ed; }
.is-success .stage-status { color: #24633d; background: #e9f6ed; }
.is-failed .stage-dot { border-color: var(--red); background: #fff0ef; }
.is-failed .stage-status { color: var(--red); background: #fff0ef; }
.is-processing .stage-dot { border-color: #9a6b00; background: #fff8df; }
.is-processing .stage-status { color: #805800; background: #fff8df; }
.is-degraded .stage-dot { border-color: #996b18; background: #fff8df; }
.is-degraded .stage-status { color: #805800; background: #fff8df; }
.is-skipped .stage-dot { border-color: var(--gray-300); background: var(--gray-100); }
@media (max-width: 640px) {
  .feed-stage { grid-template-columns: 0.8rem 1fr; gap: 0.65rem; }
  .feed-stage:not(:last-child)::after { left: 0.35rem; }
  .stage-dot { width: 0.75rem; height: 0.75rem; }
}
</style>
