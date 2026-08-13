<template>
  <section class="timeline-card" aria-labelledby="growth-title">
    <header>
      <span class="eyebrow">成长档案</span>
      <h2 id="growth-title">最近发生的变化</h2>
    </header>
    <div v-if="!records.length" class="empty-state">
      伙伴还没有成长记录，选择一张图片开始第一次喂养。
    </div>
    <ol v-else class="timeline-list">
      <li v-for="record in records" :key="record.id" class="timeline-item">
        <span class="timeline-dot" aria-hidden="true"></span>
        <div class="event-card">
          <div class="event-heading">
            <div>
              <strong>{{ eventLabel(record.eventType) }}</strong>
              <span class="experience">+{{ record.lifeExperienceDelta }} 生命经验</span>
            </div>
            <time :datetime="record.createdTime">{{ formatTime(record.createdTime) }}</time>
          </div>
          <p class="reason">{{ record.reason }}</p>
          <div v-if="traitChanges(record).length || skillChanges(record).length" class="delta-list">
            <span v-for="item in traitChanges(record)" :key="item.key"
                  :data-testid="`growth-trait-${item.key}`">
              {{ item.label }} {{ formatSignedDelta(item.value) }}
            </span>
            <span v-for="item in skillChanges(record)" :key="item.key">
              {{ item.label }} +{{ item.value }}
            </span>
          </div>
          <div class="event-meta">
            <router-link v-if="record.sourcePictureId" :to="`/picture/${record.sourcePictureId}`">
              图片 #{{ shortId(record.sourcePictureId) }}
            </router-link>
            <span>规则 {{ record.balanceVersion }}</span>
            <span data-testid="growth-nutrition-label">{{ record.nutritionLabel || '图片营养' }}</span>
            <span v-if="record.providerCode && record.modelCode">来源 {{ record.providerCode }} / {{ record.modelCode }}</span>
            <span v-if="record.confidence != null">置信度 {{ Number(record.confidence).toFixed(2) }}</span>
            <span v-if="record.contentUnderstood" class="visual-badge">已分析图片内容</span>
            <span v-else class="demo-badge">未进行内容理解</span>
          </div>
        </div>
      </li>
    </ol>
  </section>
</template>

<script setup>
import { SKILL_LABEL, TRAIT_AXES } from '@/constants/companion'
import { formatSignedDelta } from '@/utils/companion'

defineProps({ records: { type: Array, required: true } })

function eventLabel(type) {
  return type === 'PICTURE_REVISITED' ? '再次遇见熟悉的图片' : '从图片中获得成长'
}

function traitChanges(record) {
  return TRAIT_AXES
    .map(axis => ({ key: axis.key, label: `${axis.negative} ↔ ${axis.positive}`, value: record.traitDelta?.[axis.key] ?? 0 }))
    .filter(item => Number(item.value) !== 0)
}

function skillChanges(record) {
  return Object.entries(record.skillExperienceDelta || {})
    .filter(([, value]) => Number(value) !== 0)
    .map(([key, value]) => ({ key, value, label: SKILL_LABEL[key] || key }))
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
.timeline-card { border: 2px solid var(--black); background: var(--white); }
.timeline-card > header { padding: 1.25rem 1.5rem; border-bottom: 2px solid var(--black); }
.timeline-card h2 { font-size: 1.35rem; }
.eyebrow { color: var(--blue); font-size: .68rem; font-weight: 800; letter-spacing: .12em; text-transform: uppercase; }
.empty-state { padding: 2rem 1.5rem; color: var(--gray-600); }
.timeline-list { padding: 1.25rem 1.5rem 1.5rem; }
.timeline-item { position: relative; padding-left: 1.5rem; }
.timeline-item:not(:last-child) { padding-bottom: 1.25rem; }
.timeline-item:not(:last-child)::before { content: ''; position: absolute; left: 5px; top: 12px; bottom: -2px; width: 2px; background: var(--gray-200); }
.timeline-dot { position: absolute; left: 0; top: 8px; width: 12px; height: 12px; border: 2px solid var(--black); border-radius: 50%; background: var(--red); }
.event-card { min-width: 0; padding: 1rem; background: var(--gray-100); border-left: 4px solid var(--black); }
.event-heading { display: flex; justify-content: space-between; align-items: flex-start; gap: 1rem; }
.event-heading strong, .experience { display: block; }
.experience { color: var(--red); font-size: .78rem; font-weight: 800; }
.event-heading time { color: var(--gray-600); font-size: .72rem; white-space: nowrap; }
.reason { margin-top: .65rem; font-size: .88rem; }
.delta-list, .event-meta { display: flex; flex-wrap: wrap; gap: .45rem; margin-top: .75rem; }
.delta-list span { padding: .25rem .45rem; border: 1px solid var(--gray-400); background: var(--white); font-size: .72rem; }
.event-meta { align-items: center; color: var(--gray-600); font-size: .7rem; }
.event-meta a { color: var(--blue); font-weight: 700; text-decoration: underline; text-underline-offset: 2px; }
.demo-badge { padding: .2rem .4rem; border: 1px solid var(--red); color: var(--red); font-weight: 700; }
.visual-badge { padding: .2rem .4rem; border: 1px solid #075d2a; color: #075d2a; font-weight: 700; }
@media (max-width: 767px) {
  .timeline-card > header, .timeline-list { padding-inline: 1.25rem; }
  .timeline-list { grid-template-columns: 1fr; }
  .event-heading { flex-direction: column; gap: .35rem; }
}
</style>
