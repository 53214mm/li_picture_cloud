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
      <section aria-labelledby="trait-title">
        <h3 id="trait-title">人格倾向</h3>
        <p class="section-note">倾向没有高低之分，它们共同塑造伙伴的表达方式。</p>
        <div class="trait-list">
          <div v-for="axis in TRAIT_AXES" :key="axis.key" class="trait-row">
            <div class="trait-summary">
              <span>{{ axis.negative }}</span>
              <strong>{{ describeTrait(companion.traits?.[axis.key] ?? 0, axis) }}</strong>
              <span>{{ axis.positive }}</span>
            </div>
            <div class="trait-track" role="meter"
                 :aria-label="`${axis.negative}到${axis.positive}的倾向`"
                 aria-valuemin="-100" aria-valuemax="100"
                 :aria-valuenow="Number(companion.traits?.[axis.key] ?? 0)">
              <span class="neutral-mark"></span>
              <span class="trait-marker"
                    :style="{ left: `${traitPosition(companion.traits?.[axis.key] ?? 0)}%` }"></span>
            </div>
          </div>
        </div>
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
import { LIFE_STAGE, SKILL_LABEL, TRAIT_AXES } from '@/constants/companion'
import { describeTrait, traitPosition } from '@/utils/companion'

const props = defineProps({ companion: { type: Object, required: true } })

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
.trait-list, .skill-list { display: grid; gap: 1rem; margin-top: 1.25rem; }
.trait-summary { font-size: .72rem; }
.trait-summary strong { color: var(--gray-900); font-size: .78rem; }
.trait-track { position: relative; height: 16px; margin-top: .4rem; border: 2px solid var(--black); background: linear-gradient(90deg, #e9efff, var(--white) 50%, #fff2d1); }
.neutral-mark { position: absolute; left: 50%; top: 0; bottom: 0; width: 1px; background: var(--gray-400); }
.trait-marker { position: absolute; top: 50%; width: 12px; height: 12px; border: 2px solid var(--black); border-radius: 50%; background: var(--red); transform: translate(-50%, -50%); }
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
}
</style>
