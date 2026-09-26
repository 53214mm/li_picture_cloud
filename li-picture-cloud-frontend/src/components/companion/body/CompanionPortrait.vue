<template>
  <CompanionArtwork class="companion-portrait" :asset="lingyeAssets.portrait"
                    :data-visual-stage="visual.visualStage" accessible-label="绫页，中性半身像" />
</template>
<script setup>
import { computed } from 'vue'
import CompanionArtwork from './CompanionArtwork.vue'
import { lingyeAssets } from './lingyeAssets'
import { resolveCompanionVisual } from './companionVisual'
const props = defineProps({ currentStage: { type: String, default: null } })
const visual = computed(() => resolveCompanionVisual(props.currentStage))
</script>
<style scoped>
.companion-portrait { width: 104px; height: 104px; flex: 0 0 auto; border-radius: var(--lp-radius-m); background: var(--lp-surface-sunken, #eeebe4); }
@media (width < 768px) { .companion-portrait { width: 72px; height: 72px; } }
</style>
