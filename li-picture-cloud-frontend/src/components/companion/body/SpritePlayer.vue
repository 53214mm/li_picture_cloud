<template>
  <div ref="host" class="sprite-player" role="img" :aria-label="accessibleLabel"
       :data-playback="animation.playing ? 'playing' : 'static'" :data-frame="animation.frame"
       :data-animation-state="animation.state" :data-animation-offset="animation.offsetY"
       :data-artwork-state="baseFailed ? 'fallback' : 'image'"
       :style="{ aspectRatio: `${still.width} / ${still.height}` }">
    <img v-if="!baseFailed" class="sprite-still" :src="still.src" :width="still.width" :height="still.height"
         :style="{ visibility: animation.playing ? 'hidden' : 'visible' }"
         alt="" decoding="async" draggable="false" @load="baseReady = true" @error="baseFailed = true" />
    <span v-else class="sprite-placeholder" aria-hidden="true">绫页</span>
    <img v-if="requested && !atlasFailed" class="sprite-atlas" :src="atlas.src"
         alt="" decoding="async" draggable="false" :style="atlasStyle"
         @load="atlasReady = true" @error="atlasFailed = true" />
  </div>
</template>
<script setup>
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { createCompanionAnimator } from '@/presentation/companionAnimation'
import { useSpriteVisibility } from './useSpriteVisibility'
const props = defineProps({
  still: { type: Object, required: true },
  atlas: { type: Object, required: true },
  presentation: { type: Object, required: true },
  accessibleLabel: { type: String, default: '绫页，纸翼蛾族的成年全身像' },
  paused: Boolean,
  visible: { type: Boolean, default: true }
})
const emit = defineEmits(['availability'])
const host = ref(null)
const animation = ref({ state: 'static', frame: 0, offsetY: 0, playing: false })
const baseReady = ref(false)
const baseFailed = ref(false)
const requested = ref(false)
const atlasReady = ref(false)
const atlasFailed = ref(false)
const { canPlay, reducedMotion } = useSpriteVisibility(host)
const eligible = computed(() => !props.paused && props.visible && canPlay.value && baseReady.value && !baseFailed.value)
const playing = computed(() => eligible.value && atlasReady.value && !atlasFailed.value)
const atlasStyle = computed(() => ({
  width: `${props.atlas.columns * 100}%`,
  transform: `translate(-${animation.value.frame * 100 / props.atlas.columns}%, ${animation.value.offsetY}px)`,
  visibility: animation.value.playing ? 'visible' : 'hidden'
}))
const animator = createCompanionAnimator({ onChange: value => { animation.value = value } })
watch(eligible, value => { if (value) requested.value = true })
watch([() => props.presentation, playing], ([presentation, playable]) => {
  animator.update(presentation, playable)
}, { immediate: true, flush: 'sync' })
watch([reducedMotion, baseFailed, atlasFailed], () => {
  emit('availability', { reducedMotion: reducedMotion.value, failed: baseFailed.value || atlasFailed.value })
}, { immediate: true })
onBeforeUnmount(() => animator.destroy())
</script>
<style scoped>
.sprite-player { position: relative; width: 100%; overflow: hidden; isolation: isolate; }
.sprite-still { display: block; width: 100%; height: 100%; object-fit: contain; }
.sprite-atlas { position: absolute; inset: 0 auto auto 0; max-width: none; height: 100%; pointer-events: none; }
.sprite-placeholder { position: absolute; inset: 20%; display: grid; place-items: center; border: 1px solid var(--lp-border-strong); border-radius: 50% 50% 40% 40%; color: var(--lp-text-secondary); }
</style>
