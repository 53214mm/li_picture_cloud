<template>
  <figure class="companion-body" :data-visual-stage="visual.visualStage">
    <div class="body-ground">
      <SpritePlayer v-if="animate && visual.allowIdle" :still="lingyeAssets.home" :atlas="lingyeAssets.idle"
                    :paused="paused || userPaused" :visible="visible" @availability="availability = $event" />
      <CompanionArtwork v-else class="body-static" :asset="lingyeAssets.home" loading="eager"
                        accessible-label="绫页，纸翼蛾族的成年全身像" />
    </div>
    <figcaption>
      <span><strong>绫页</strong><span class="body-species">纸翼蛾族</span></span>
      <button v-if="animate && visual.allowIdle && !availability.reducedMotion && !availability.failed"
              type="button" class="body-motion" :aria-pressed="userPaused" @click="userPaused = !userPaused">
        {{ userPaused ? '恢复动作' : '暂停动作' }}
      </button>
      <span v-else class="body-static-label">静态立绘</span>
    </figcaption>
  </figure>
</template>
<script setup>
import { computed, ref } from 'vue'
import CompanionArtwork from './CompanionArtwork.vue'
import SpritePlayer from './SpritePlayer.vue'
import { lingyeAssets } from './lingyeAssets'
import { resolveCompanionVisual } from './companionVisual'
const props = defineProps({
  currentStage: { type: String, default: null },
  animate: { type: Boolean, default: true },
  paused: Boolean,
  visible: { type: Boolean, default: true }
})
const visual = computed(() => resolveCompanionVisual(props.currentStage))
const userPaused = ref(false)
const availability = ref({ reducedMotion: true, failed: false })
</script>
<style scoped>
.companion-body { width: 100%; margin: 0; align-self: center; padding: 1rem 1rem .75rem; color: var(--lp-text-primary); background: radial-gradient(ellipse at 50% 38%, #faf8ef 0, #ece5d5 70%, #e1d7c4 100%); }
.body-ground { position: relative; max-width: 21rem; margin-inline: auto; }
.body-ground::before { content: ''; position: absolute; bottom: 6%; left: 27%; width: 46%; height: 3%; border-radius: 50%; background: #70695824; filter: blur(3px); }
.body-static { aspect-ratio: 3 / 4; position: relative; }
figcaption { display: flex; align-items: center; justify-content: space-between; flex-wrap: wrap; gap: .25rem .75rem; min-height: 44px; padding-inline: .25rem; }
figcaption strong { font-size: .95rem; }
.body-species { margin-left: .5rem; color: var(--lp-text-secondary); font-size: .7rem; }
.body-motion { min-height: 44px; padding: .25rem .5rem; border: 1px solid var(--lp-border-strong); border-radius: var(--lp-radius-s); color: var(--lp-text-primary); background: #ffffff80; font-size: .75rem; cursor: pointer; }
.body-motion:focus-visible { outline: 2px solid var(--lp-accent); outline-offset: 2px; }
.body-static-label { color: var(--lp-text-secondary); font-size: .7rem; }
@media (width < 768px) { .body-ground { max-width: 17rem; } }
</style>
