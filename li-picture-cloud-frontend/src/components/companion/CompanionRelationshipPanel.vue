<template>
  <section class="relationship-card" aria-labelledby="relationship-title"
           :data-rapport="presentation.rapport" :data-state-status="relationship.status">
    <header>
      <div>
        <span class="eyebrow">关系状态</span>
        <h2 id="relationship-title">你们之间的联结</h2>
      </div>
    </header>
    <div class="relationship-body">
      <div class="state-overview" data-testid="relationship-overview">
        <div class="relationship-motif" aria-hidden="true">
          <span class="relationship-motif__link relationship-motif__link--first"></span>
          <span class="relationship-motif__link relationship-motif__link--second"></span>
          <span class="relationship-motif__ground"></span>
        </div>
        <div class="state-copy">
          <h3 data-testid="relationship-label">{{ relationship.label }}</h3>
          <p>{{ relationship.description }}</p>
        </div>
      </div>
      <details v-if="relationship.status === 'known'" class="state-details" data-testid="relationship-details">
        <summary>查看关系细节<span class="summary-hint">相处中的积累</span></summary>
        <ul class="relationship-axes">
          <li v-for="axis in relationship.axes" :key="axis.key" class="axis-row"
              :data-testid="`relationship-axis-${axis.key}`">
            <div class="axis-head">
              <span>{{ axis.label }}</span>
              <strong :data-testid="`relationship-value-${axis.key}`">{{ axis.value }}</strong>
            </div>
            <div class="axis-track" aria-hidden="true">
              <span v-if="axis.key === 'recentFeedback'" class="neutral-mark"></span>
              <span class="axis-marker" :style="{ left: `${axis.position}%` }"></span>
            </div>
            <div class="axis-range" aria-hidden="true">
              <span>{{ axis.key === 'recentFeedback' ? '−100' : '0' }}</span>
              <span v-if="axis.key === 'recentFeedback'">0</span>
              <span>100</span>
            </div>
            <span v-if="axis.key === 'recentFeedback'" class="range-description">近期反馈范围为 −100 到 100，0 位于中间。</span>
          </li>
        </ul>
        <p class="state-note">关系随相处积累。近期反馈单独保留正负方向，不用它推断伙伴此刻的情绪。</p>
      </details>
    </div>
  </section>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({ presentation: { type: Object, required: true } })
const relationship = computed(() => props.presentation.disposition.relationship)
</script>

<style scoped>
.relationship-card { min-width: 0; border: 1px solid var(--gray-200); border-radius: var(--lp-radius-s); background: var(--white); }
.relationship-card > header { padding: 1.25rem 1.5rem; border-bottom: 1px solid var(--gray-200); }
.relationship-card h2 { font-size: 1.15rem; font-weight: 500; }
.eyebrow { color: var(--gray-600); font-size: .68rem; letter-spacing: .1em; }
.relationship-body { padding: 1.25rem 1.5rem 1.5rem; }
.state-overview { display: flex; align-items: center; gap: 1rem; min-height: 6rem; }
.state-copy { min-width: 0; }
.state-copy h3 { color: var(--gray-900); font-size: 1.18rem; font-weight: 500; line-height: 1.6; }
.state-copy p { margin-top: .4rem; color: var(--gray-600); font-size: .8rem; line-height: 1.9; overflow-wrap: anywhere; }
.relationship-motif { position: relative; flex: 0 0 4rem; height: 4rem; color: var(--blue); }
.relationship-motif__link { position: absolute; top: 8px; width: 27px; height: 38px; border: 1px solid currentColor; border-radius: 50%; background: var(--gray-100); }
.relationship-motif__link--first { left: 3px; transform: rotate(-18deg); }
.relationship-motif__link--second { right: 3px; transform: rotate(18deg); background: transparent; }
.relationship-motif__ground { position: absolute; bottom: 8px; left: 8px; right: 8px; height: 1px; background: var(--gray-200); }
.relationship-card[data-rapport="familiar"] .relationship-motif__link--first { left: 9px; }
.relationship-card[data-rapport="familiar"] .relationship-motif__link--second { right: 9px; }
.relationship-card[data-rapport="close"] .relationship-motif__link--first { left: 12px; background: var(--yellow); }
.relationship-card[data-rapport="close"] .relationship-motif__link--second { right: 12px; }
.relationship-card:not([data-state-status="known"]) .relationship-motif__link { border-style: dashed; border-color: var(--gray-400); background: transparent; }
.state-details { margin-top: 1.25rem; border-top: 1px solid var(--gray-200); }
.state-details summary { min-height: 44px; padding-block: .75rem; color: var(--blue); font-size: .8rem; cursor: pointer; }
.state-details summary:focus-visible { outline: 2px solid var(--blue); outline-offset: 3px; border-radius: 2px; }
.summary-hint { margin-left: .65rem; color: var(--gray-600); font-size: .7rem; }
.relationship-axes { display: grid; gap: 1rem; margin-top: .75rem; padding: 0; list-style: none; }
.axis-head { display: flex; justify-content: space-between; align-items: baseline; gap: 1rem; color: var(--gray-900); font-size: .8rem; }
.axis-head strong { font-size: .8rem; font-weight: 500; font-variant-numeric: tabular-nums; }
.axis-track { position: relative; height: 5px; margin: .65rem 5px .25rem; border-radius: var(--lp-radius-full); background: var(--gray-200); }
.neutral-mark { position: absolute; left: 50%; top: -3px; bottom: -3px; width: 1px; background: var(--gray-400); }
.axis-marker { position: absolute; top: 50%; width: 10px; height: 10px; border: 1px solid var(--blue); border-radius: 50%; background: var(--white); transform: translate(-50%, -50%); }
.axis-range { display: flex; justify-content: space-between; margin-top: .4rem; color: var(--gray-600); font-size: .65rem; font-variant-numeric: tabular-nums; }
.range-description { display: block; margin-top: .4rem; color: var(--gray-600); font-size: .7rem; line-height: 1.8; }
.state-note { margin-top: 1.25rem; color: var(--gray-600); font-size: .72rem; line-height: 1.8; }
@media (max-width: 767px) {
  .relationship-card > header, .relationship-body { padding-inline: 1rem; }
  .state-overview { gap: .8rem; }
  .relationship-motif { flex-basis: 3.5rem; height: 3.5rem; }
  .summary-hint { display: block; margin: .2rem 0 0 1rem; }
}
</style>
