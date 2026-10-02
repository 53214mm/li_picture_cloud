<template>
  <section class="mood-card" aria-labelledby="mood-title"
           :data-affect="presentation.affect" :data-state-status="mood.status">
    <header>
      <div>
        <span class="eyebrow">当前情绪</span>
        <h2 id="mood-title">伙伴此刻的状态</h2>
      </div>
    </header>
    <div class="mood-body">
      <div class="state-overview" data-testid="mood-overview">
        <div class="mood-motif" aria-hidden="true">
          <span class="mood-motif__halo"></span>
          <span class="mood-motif__core"></span>
          <span class="mood-motif__mark"></span>
        </div>
        <div class="state-copy">
          <h3 data-testid="mood-label">{{ mood.label }}</h3>
          <p>{{ mood.description }}</p>
        </div>
      </div>
      <details v-if="mood.status === 'known'" class="state-details" data-testid="mood-details">
        <summary>查看情绪细节<span class="summary-hint">五个短时维度</span></summary>
        <ul class="mood-axes">
          <li v-for="axis in mood.axes" :key="axis.key" class="axis-row"
              :data-testid="`mood-axis-${axis.key}`">
            <div class="axis-head">
              <span>{{ axis.label }}</span>
              <span class="axis-reading"><strong :data-testid="`mood-value-${axis.key}`">{{ axis.value }}</strong> / 100</span>
            </div>
            <div class="axis-track" aria-hidden="true">
              <span class="axis-marker" :style="{ left: `${axis.position}%` }"></span>
            </div>
          </li>
        </ul>
        <p class="state-note">0 表示中性。情绪是短时状态，数值来自最近一次读取；页面不会自行推演变化。</p>
      </details>
    </div>
  </section>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({ presentation: { type: Object, required: true } })
const mood = computed(() => props.presentation.disposition.mood)
</script>

<style scoped>
.mood-card { min-width: 0; border: 1px solid var(--gray-200); border-radius: var(--lp-radius-s); background: var(--white); }
.mood-card > header { padding: 1.25rem 1.5rem; border-bottom: 1px solid var(--gray-200); }
.mood-card h2 { font-size: 1.15rem; font-weight: 500; }
.eyebrow { color: var(--gray-600); font-size: .68rem; letter-spacing: .1em; }
.mood-body { padding: 1.25rem 1.5rem 1.5rem; }
.state-overview { display: flex; align-items: center; gap: 1rem; min-height: 6rem; }
.state-copy { min-width: 0; }
.state-copy h3 { color: var(--gray-900); font-size: 1.18rem; font-weight: 500; line-height: 1.6; }
.state-copy p { margin-top: .4rem; color: var(--gray-600); font-size: .8rem; line-height: 1.9; overflow-wrap: anywhere; }
.mood-motif { position: relative; flex: 0 0 4rem; height: 4rem; color: var(--blue); }
.mood-motif__halo { position: absolute; inset: 2px; border: 1px solid var(--gray-200); border-radius: 50%; background: var(--gray-100); }
.mood-motif__core { position: absolute; inset: 17px; border: 1px solid currentColor; border-radius: 50%; background: var(--white); }
.mood-motif__mark { position: absolute; top: 8px; right: 8px; width: 7px; height: 7px; border: 1px solid currentColor; border-radius: 50%; background: var(--white); }
.mood-card[data-affect="energetic"] .mood-motif__core { inset: 13px; border-radius: 40% 60% 50% 50%; }
.mood-card[data-affect="cheerful"] .mood-motif__core { background: var(--yellow); }
.mood-card[data-affect="inspired"] .mood-motif__core { border-radius: 5px; transform: rotate(45deg); }
.mood-card[data-affect="lonely"] .mood-motif__mark { top: 3px; right: 0; }
.mood-card[data-affect="irritated"] .mood-motif__core { border-style: dashed; }
.mood-card:not([data-state-status="known"]) .mood-motif__core { border-style: dashed; border-color: var(--gray-400); background: transparent; }
.mood-card:not([data-state-status="known"]) .mood-motif__mark { display: none; }
.state-details { margin-top: 1.25rem; border-top: 1px solid var(--gray-200); }
.state-details summary { min-height: 44px; padding-block: .75rem; color: var(--blue); font-size: .8rem; cursor: pointer; }
.state-details summary:focus-visible { outline: 2px solid var(--blue); outline-offset: 3px; border-radius: 2px; }
.summary-hint { margin-left: .65rem; color: var(--gray-600); font-size: .7rem; }
.mood-axes { display: grid; gap: 1rem; margin-top: .75rem; padding: 0; list-style: none; }
.axis-head { display: flex; justify-content: space-between; align-items: baseline; gap: 1rem; color: var(--gray-900); font-size: .8rem; }
.axis-reading { color: var(--gray-600); font-size: .7rem; white-space: nowrap; font-variant-numeric: tabular-nums; }
.axis-reading strong { color: var(--gray-900); font-size: .8rem; font-weight: 500; }
.axis-track { position: relative; height: 5px; margin: .65rem 5px .25rem; border-radius: var(--lp-radius-full); background: var(--gray-200); }
.axis-marker { position: absolute; top: 50%; width: 10px; height: 10px; border: 1px solid var(--blue); border-radius: 50%; background: var(--white); transform: translate(-50%, -50%); }
.state-note { margin-top: 1.25rem; color: var(--gray-600); font-size: .72rem; line-height: 1.8; }
@media (max-width: 767px) {
  .mood-card > header, .mood-body { padding-inline: 1rem; }
  .state-overview { gap: .8rem; }
  .mood-motif { flex-basis: 3.5rem; height: 3.5rem; }
  .summary-hint { display: block; margin: .2rem 0 0 1rem; }
}
</style>
