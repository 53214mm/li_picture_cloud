<template>
  <section class="relationship-card" aria-labelledby="relationship-title">
    <header>
      <div>
        <span class="eyebrow">关系状态</span>
        <h2 id="relationship-title">你们之间的联结</h2>
      </div>
    </header>
    <div v-if="!relationship" class="empty-state">
      伙伴还没和你建立关系，先喂一张图片开始相处。
    </div>
    <div v-else class="relationship-body">
      <ul class="relationship-axes">
        <li v-for="axis in RELATIONSHIP_AXES" :key="axis.key" class="axis-row">
          <div class="axis-head">
            <span>{{ axis.label }}</span>
            <strong>{{ axisValue(axis.key) }}</strong>
          </div>
          <div class="axis-bar" aria-hidden="true">
            <span :class="{ negative: axis.key === 'recentFeedback' && axisValue(axis.key) < 0 }"
                  :style="barStyle(axis.key)"></span>
          </div>
          <p class="axis-desc">{{ axis.description }}</p>
        </li>
      </ul>
      <p class="relationship-note">关系是慢状态：只随相处慢慢积累，不会因为时间流逝而消失。</p>
    </div>
  </section>
</template>

<script setup>
import { RELATIONSHIP_AXES } from '@/constants/companion'

const props = defineProps({ relationship: { type: Object, default: null } })

function axisValue(key) {
  return Number(props.relationship?.[key] ?? 0)
}

function barStyle(key) {
  if (key === 'recentFeedback') {
    // 近期反馈以 0 为中心向两侧延伸，负值向左、正值向右。
    const width = Math.min(Math.abs(axisValue(key)), 100)
    return axisValue(key) < 0
      ? { width: `${width}%`, marginLeft: `${100 - width}%` }
      : { width: `${width}%` }
  }
  return { width: `${axisValue(key)}%` }
}
</script>

<style scoped>
.relationship-card { border: 2px solid var(--black); background: var(--white); }
.relationship-card > header { padding: 1.25rem 1.5rem; border-bottom: 2px solid var(--black); }
.relationship-card h2 { font-size: 1.35rem; }
.eyebrow { color: #075d2a; font-size: .68rem; font-weight: 800; letter-spacing: .12em; text-transform: uppercase; }
.empty-state { padding: 2rem 1.5rem; color: var(--gray-600); }
.relationship-body { padding: 1.25rem 1.5rem 1.5rem; }
.relationship-axes { display: grid; gap: .9rem; }
.axis-head { display: flex; justify-content: space-between; align-items: baseline; gap: 1rem; font-size: .85rem; }
.axis-head strong { color: #075d2a; }
.axis-bar { height: .6rem; border: 2px solid var(--black); background: var(--gray-100); }
.axis-bar span { display: block; height: 100%; background: #4aa16d; transition: width .4s ease; }
.axis-bar span.negative { background: var(--red); }
.axis-desc { margin-top: .25rem; color: var(--gray-600); font-size: .7rem; }
.relationship-note { margin-top: 1rem; padding: .6rem .8rem; border-left: 4px solid #4aa16d; background: var(--gray-100); color: var(--gray-600); font-size: .75rem; }
@media (max-width: 767px) {
  .relationship-card > header, .relationship-body { padding-inline: 1.25rem; }
}
</style>
