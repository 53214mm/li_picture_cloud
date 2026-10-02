<template>
  <section class="stats-card" aria-labelledby="companion-stats-title">
    <header class="stats-header">
      <div>
        <span class="eyebrow">当前形态</span>
        <h2 id="companion-stats-title">{{ stage.label }}</h2>
        <p>{{ stage.description }}</p>
      </div>
      <span class="level-chip">等级 {{ companion.level }}</span>
    </header>

    <div class="life-block">
      <div class="meter-label">
        <strong>生命经验</strong>
        <span>{{ companion.lifeExperience }} / {{ companion.nextLevelExperience }} 生命经验</span>
      </div>
      <div class="meter" role="progressbar" aria-label="生命经验进度" aria-valuemin="0"
           aria-valuemax="100" :aria-valuenow="Math.round(lifeProgress)">
        <span :style="{ width: `${lifeProgress}%` }"></span>
      </div>
    </div>

    <div class="stats-grid">
      <section aria-labelledby="trait-title" :data-state-status="traits.status">
        <h3 id="trait-title">人格倾向</h3>
        <div class="trait-overview" data-testid="traits-overview">
          <div class="trait-sketch" aria-hidden="true">
            <template v-if="traits.status === 'known'">
              <span v-for="axis in traits.axes" :key="axis.key" class="trait-sketch__line">
                <span :style="{ left: `${axis.position}%` }"></span>
              </span>
            </template>
            <span v-else class="trait-sketch__unknown"></span>
          </div>
          <div class="trait-copy">
            <h4 data-testid="traits-label">{{ traits.label }}</h4>
            <p>{{ traits.description }}</p>
          </div>
        </div>
        <p class="section-note">倾向没有高低之分，它们共同塑造伙伴的表达方式。</p>
        <details v-if="traits.status === 'known'" class="trait-details" data-testid="traits-details">
          <summary>查看性格细节<span class="summary-hint">五组双向倾向</span></summary>
          <div class="trait-list">
            <div v-for="axis in traits.axes" :key="axis.key" class="trait-row"
                 :data-testid="`trait-axis-${axis.key}`">
              <div class="trait-summary">
                <strong>{{ axis.label }}</strong>
                <span class="trait-value" :data-testid="`trait-value-${axis.key}`">{{ axis.value }}</span>
              </div>
              <div class="trait-track" role="meter"
                   :aria-label="`${axis.negative}到${axis.positive}的倾向`"
                   :aria-valuetext="axis.label" aria-valuemin="-100" aria-valuemax="100"
                   :aria-valuenow="axis.value">
                <span class="neutral-mark"></span>
                <span class="trait-marker" :style="{ left: `${axis.position}%` }"></span>
              </div>
              <div class="trait-poles" aria-hidden="true">
                <span>{{ axis.negative }} · −100</span>
                <span>{{ axis.positive }} · 100</span>
              </div>
            </div>
          </div>
          <p class="trait-note">中点为 0，左右只表示不同方向，不代表优劣。</p>
        </details>
      </section>

      <section aria-labelledby="skill-title">
        <h3 id="skill-title">成长技能</h3>
        <p class="section-note">喂养不同图片，会让伙伴积累不同方向的能力。</p>
        <div class="skill-list">
          <div v-for="skill in companion.skills || []" :key="skill.code" class="skill-row"
               :data-testid="`skill-${skill.code}`">
            <div class="meter-label">
              <strong>{{ SKILL_LABEL[skill.code] || skill.code }}</strong>
              <span>Lv.{{ skill.level }}</span>
            </div>
            <div class="meter skill-meter" role="progressbar"
                 :aria-label="`${SKILL_LABEL[skill.code] || skill.code}经验`"
                 aria-valuemin="0" :aria-valuemax="Number(skill.nextLevelExperience)"
                 :aria-valuenow="Number(skill.experience)">
              <span :style="{ width: `${skillProgress(skill)}%` }"></span>
            </div>
            <small>{{ skill.experience }} / {{ skill.nextLevelExperience }} 经验</small>
          </div>
        </div>
      </section>
    </div>
  </section>
</template>

<script setup>
import { computed } from 'vue'
import { LIFE_STAGE, SKILL_LABEL } from '@/constants/companion'

const props = defineProps({
  companion: { type: Object, required: true },
  presentation: { type: Object, required: true }
})

const traits = computed(() => props.presentation.disposition.traits)

const stage = computed(() => LIFE_STAGE[props.companion.lifeStage] || {
  label: props.companion.lifeStage || '未知形态',
  description: '伙伴仍在形成自己的样子。'
})

const lifeProgress = computed(() => {
  const start = Number(props.companion.levelStartExperience)
  const next = Number(props.companion.nextLevelExperience)
  const current = Number(props.companion.lifeExperience)
  return next <= start ? 100 : Math.min(100, Math.max(0, (current - start) / (next - start) * 100))
})

function skillProgress(skill) {
  const next = Number(skill.nextLevelExperience)
  return next <= 0 ? 100 : Math.min(100, Math.max(0, Number(skill.experience) / next * 100))
}
</script>

<style scoped>
.stats-card { border: 2px solid var(--black); background: var(--white); }
.stats-header { display: flex; justify-content: space-between; gap: 1.5rem; padding: 1.5rem; background: var(--yellow); border-bottom: 2px solid var(--black); }
.stats-header h2 { font-size: 2rem; line-height: 1.1; }
.stats-header p { max-width: 34rem; margin-top: .4rem; color: var(--gray-900); }
.eyebrow { display: block; margin-bottom: .35rem; font-size: .7rem; font-weight: 800; letter-spacing: .12em; text-transform: uppercase; }
.level-chip { align-self: flex-start; padding: .5rem .75rem; border: 2px solid var(--black); background: var(--white); font-weight: 800; white-space: nowrap; }
.life-block { padding: 1.25rem 1.5rem; border-bottom: 2px solid var(--black); }
.meter-label, .trait-summary { display: flex; justify-content: space-between; align-items: baseline; gap: .75rem; }
.meter-label span, .section-note, .skill-row small { color: var(--gray-600); font-size: .78rem; }
.meter { height: 12px; margin-top: .55rem; border: 2px solid var(--black); background: var(--white); overflow: hidden; }
.meter > span { display: block; height: 100%; background: var(--blue); transition: width .25s ease-out; }
.stats-grid { display: grid; grid-template-columns: 1fr 1fr; }
.stats-grid > section { min-width: 0; padding: 1.5rem; }
.stats-grid > section + section { border-left: 2px solid var(--black); }
.stats-grid h3 { font-size: 1.15rem; }
.section-note { margin-top: .25rem; min-height: 2.4em; }
.trait-overview { display: flex; align-items: center; gap: 1rem; margin-block: 1.25rem; }
.trait-copy { min-width: 0; }
.trait-copy h4 { color: var(--gray-900); font-size: 1.18rem; font-weight: 500; line-height: 1.6; }
.trait-copy p { margin-top: .4rem; color: var(--gray-600); font-size: .8rem; line-height: 1.9; overflow-wrap: anywhere; }
.trait-sketch { display: grid; align-content: center; gap: 9px; flex: 0 0 4rem; height: 4rem; padding: 5px; border: 1px solid var(--gray-200); border-radius: var(--lp-radius-s); background: var(--gray-100); }
.trait-sketch__line { position: relative; display: block; height: 1px; margin-inline: 4px; background: var(--gray-400); }
.trait-sketch__line > span { position: absolute; top: 50%; width: 5px; height: 5px; border-radius: 50%; background: var(--blue); transform: translate(-50%, -50%); }
.trait-sketch__unknown { height: 32px; margin: 4px; border: 1px dashed var(--gray-400); border-radius: 50%; }
.trait-details { margin-top: 1rem; border-top: 1px solid var(--gray-200); }
.trait-details summary { min-height: 44px; padding-block: .75rem; color: var(--blue); font-size: .8rem; cursor: pointer; }
.trait-details summary:focus-visible { outline: 2px solid var(--blue); outline-offset: 3px; border-radius: 2px; }
.summary-hint { margin-left: .65rem; color: var(--gray-600); font-size: .7rem; }
.trait-list, .skill-list { display: grid; gap: 1rem; margin-top: 1.25rem; }
.trait-list { margin-top: .75rem; }
.trait-summary { font-size: .8rem; }
.trait-summary strong { color: var(--gray-900); font-weight: 500; }
.trait-value { color: var(--gray-600); font-variant-numeric: tabular-nums; }
.trait-track { position: relative; height: 5px; margin: .7rem 5px .25rem; border-radius: var(--lp-radius-full); background: var(--gray-200); }
.neutral-mark { position: absolute; left: 50%; top: -3px; bottom: -3px; width: 1px; background: var(--gray-400); }
.trait-marker { position: absolute; top: 50%; width: 10px; height: 10px; border: 1px solid var(--blue); border-radius: 50%; background: var(--white); transform: translate(-50%, -50%); }
.trait-poles { display: flex; justify-content: space-between; gap: .75rem; margin-top: .5rem; color: var(--gray-600); font-size: .7rem; }
.trait-note { margin-top: 1.25rem; color: var(--gray-600); font-size: .72rem; line-height: 1.8; }
.skill-row { padding-bottom: .9rem; border-bottom: 1px solid var(--gray-200); }
.skill-meter { height: 9px; }
.skill-meter > span { background: var(--red); }
.skill-row small { display: block; margin-top: .35rem; text-align: right; font-variant-numeric: tabular-nums; }
@media (max-width: 767px) {
  .stats-header { align-items: flex-start; flex-direction: column; padding: 1.25rem; }
  .stats-header h2 { font-size: 1.65rem; }
  .life-block, .stats-grid > section { padding: 1.25rem; }
  .stats-grid { grid-template-columns: 1fr; }
  .stats-grid > section + section { border-left: 0; border-top: 2px solid var(--black); }
  .section-note { min-height: 0; }
  .trait-overview { gap: .8rem; }
  .trait-sketch { flex-basis: 3.5rem; height: 3.5rem; }
  .summary-hint { display: block; margin: .2rem 0 0 1rem; }
}
</style>
