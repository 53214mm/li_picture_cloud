<template>
  <figure class="companion-body" :data-visual-stage="visual.visualStage" :data-animation-intent="animationIntent.state"
          :data-presentation-availability="presentation.availability" :data-presentation-activity="presentation.activity"
          :data-presentation-affect="presentation.affect" :data-presentation-attention="presentation.attention"
          :data-presentation-rapport="presentation.rapport" :data-presentation-freshness="presentation.freshness">
    <button type="button" class="body-ground" :class="{ 'is-drop-target': interaction.dragging }"
            aria-label="和绫页互动" aria-haspopup="dialog" title="和绫页互动"
            @click="interaction.open()" @pointerenter="hovered = $event.pointerType === 'mouse'" @pointerleave="hovered = false"
            @focus="focused = true" @blur="focused = false" @dragover="interaction.dragOver" @drop.stop="interaction.drop">
      <SpritePlayer v-if="animate && animationIntent.state !== 'static'" :still="lingyeAssets.home" :atlas="lingyeAssets.idle"
                    :key="presentation.companionId" :presentation="presentation"
                    :interaction-request="interactionRequest"
                    :paused="paused || userPaused" :visible="visible" @availability="availability = $event" />
      <CompanionArtwork v-else class="body-static" :asset="lingyeAssets.home" loading="eager"
                        accessible-label="绫页，纸翼蛾族的成年全身像" />
    </button>
    <figcaption>
      <span><strong>绫页</strong><span class="body-species">纸翼蛾族</span></span>
      <button v-if="animate && animationIntent.state !== 'static' && !availability.reducedMotion && !availability.failed"
              type="button" class="body-motion" :aria-pressed="userPaused" @click="userPaused = !userPaused">
        {{ userPaused ? '恢复动作' : '暂停动作' }}
      </button>
      <span v-else class="body-static-label">静态立绘</span>
    </figcaption>
  </figure>
</template>
<script setup>
import { computed, ref, watch } from 'vue'
import CompanionArtwork from './CompanionArtwork.vue'
import SpritePlayer from './SpritePlayer.vue'
import { lingyeAssets } from './lingyeAssets'
import { mapCompanionAnimation } from '@/presentation/companionAnimation'
import { useCompanionInteractionStore } from '@/stores/companionInteraction'
const interaction = useCompanionInteractionStore()
const hovered = ref(false)
const focused = ref(false)
const interactionRequest = ref(0)
watch(() => hovered.value || focused.value, (active, previous) => {
  if (active && !previous) interactionRequest.value += 1
})
const props = defineProps({
  presentation: { type: Object, required: true },
  animate: { type: Boolean, default: true },
  paused: Boolean,
  visible: { type: Boolean, default: true }
})
const visual = computed(() => props.presentation.appearance)
const animationIntent = computed(() => mapCompanionAnimation(props.presentation))
const userPaused = ref(false)
const availability = ref({ reducedMotion: true, failed: false })
</script>
<style scoped>
.companion-body { width: 100%; margin: 0; align-self: center; padding: 1rem 1rem .75rem; color: var(--lp-text-primary); background: radial-gradient(ellipse at 50% 38%, #faf8ef 0, #ece5d5 70%, #e1d7c4 100%); }
.body-ground { display: block; width: 100%; padding: 0; border: 0; background: transparent; position: relative; max-width: 21rem; margin-inline: auto; border-radius: var(--lp-radius-m); }
.body-ground:hover, .body-ground:focus-visible { outline: 2px solid var(--lp-accent); outline-offset: 2px; }
.body-ground.is-drop-target { outline: 2px dashed var(--lp-accent); }
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
