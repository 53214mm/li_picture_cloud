<template>
  <section class="mood-card" aria-labelledby="mood-title">
    <header>
      <div>
        <span class="eyebrow">当前情绪</span>
        <h2 id="mood-title">伙伴此刻的状态</h2>
      </div>
    </header>
    <div v-if="!mood" class="empty-state">
      伙伴还没有形成明显情绪，先喂一张图片看看它的反应。
    </div>
    <template v-else>
      <div class="mood-body">
        <CompanionMessageBubble :message="mood.summary" />
        <ul class="mood-axes">
          <li v-for="axis in MOOD_AXES" :key="axis.key" class="axis-row">
            <div class="axis-head">
              <span>{{ axis.label }}</span>
              <strong>{{ axisValue(axis.key) }}</strong>
            </div>
            <div class="axis-bar" aria-hidden="true">
              <span :style="{ width: `${axisValue(axis.key)}%` }"></span>
            </div>
            <p class="axis-desc">{{ axis.description }}</p>
          </li>
        </ul>
        <p class="mood-note">情绪是快状态：随时间自然回落，每次喂养只带来短暂波动，不会改变长期性格。</p>
      </div>
    </template>
  </section>
</template>

<script setup>
import { MOOD_AXES } from '@/constants/companion'
import CompanionMessageBubble from '@/components/companion/CompanionMessageBubble.vue'

const props = defineProps({ mood: { type: Object, default: null } })

function axisValue(key) {
  return Number(props.mood?.[key] ?? 0)
}
</script>

<style scoped>
.mood-card { border: 2px solid var(--black); background: var(--white); }
.mood-card > header { padding: 1.25rem 1.5rem; border-bottom: 2px solid var(--black); }
.mood-card h2 { font-size: 1.35rem; }
.eyebrow { color: var(--red); font-size: .68rem; font-weight: 800; letter-spacing: .12em; text-transform: uppercase; }
.empty-state { padding: 2rem 1.5rem; color: var(--gray-600); }
.mood-body { padding: 1.25rem 1.5rem 1.5rem; }
.mood-body :deep(.companion-message) { margin-top: 0; }
.mood-axes { display: grid; gap: .9rem; margin-top: 1rem; }
.axis-head { display: flex; justify-content: space-between; align-items: baseline; gap: 1rem; font-size: .85rem; }
.axis-head strong { color: var(--red); }
.axis-bar { height: .6rem; border: 2px solid var(--black); background: var(--gray-100); }
.axis-bar span { display: block; height: 100%; background: var(--blue); transition: width .4s ease; }
.axis-desc { margin-top: .25rem; color: var(--gray-600); font-size: .7rem; }
.mood-note { margin-top: 1rem; padding: .6rem .8rem; border-left: 4px solid var(--yellow); background: var(--gray-100); color: var(--gray-600); font-size: .75rem; }
@media (max-width: 767px) {
  .mood-card > header, .mood-body { padding-inline: 1.25rem; }
}
</style>
