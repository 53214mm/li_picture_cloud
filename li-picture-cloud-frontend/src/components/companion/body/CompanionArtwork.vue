<template>
  <span class="companion-artwork" :role="decorative ? undefined : 'img'"
        :aria-label="decorative ? undefined : accessibleLabel" :aria-hidden="decorative ? 'true' : undefined"
        :data-artwork-state="failed ? 'fallback' : 'image'">
    <img v-if="!failed" :src="asset.src" :width="asset.width" :height="asset.height"
         alt="" :loading="loading" decoding="async" draggable="false" @error="failed = true" />
    <span v-else class="artwork-placeholder" aria-hidden="true">绫</span>
  </span>
</template>
<script setup>
import { ref, watch } from 'vue'
const props = defineProps({
  asset: { type: Object, required: true },
  accessibleLabel: { type: String, default: '绫页' },
  decorative: Boolean,
  loading: { type: String, default: 'lazy' }
})
const failed = ref(false)
watch(() => props.asset.src, () => { failed.value = false })
</script>
<style scoped>
.companion-artwork { display: grid; place-items: center; width: 100%; height: 100%; overflow: hidden; }
.companion-artwork img { display: block; width: 100%; height: 100%; object-fit: contain; }
.artwork-placeholder { display: grid; place-items: center; width: 70%; aspect-ratio: 1; border: 1px solid currentColor; border-radius: 50%; color: var(--lp-text-secondary); opacity: .65; font-size: 1em; }
</style>
