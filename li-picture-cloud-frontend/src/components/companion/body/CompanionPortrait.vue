<template>
  <CompanionArtwork class="companion-portrait" :asset="lingyeAssets.portrait"
                    :data-visual-stage="presentation.appearance.visualStage" accessible-label="绫页，中性半身像"
                    :data-presentation-availability="presentation.availability" :data-presentation-activity="presentation.activity"
                    :data-presentation-affect="presentation.affect" :data-presentation-attention="presentation.attention"
                    :data-presentation-rapport="presentation.rapport" :data-presentation-freshness="presentation.freshness" />
</template>
<script setup>
import CompanionArtwork from './CompanionArtwork.vue'
import { lingyeAssets } from './lingyeAssets'
defineProps({ presentation: { type: Object, required: true } })
</script>
<style scoped>
.companion-portrait { width: 104px; height: 104px; flex: 0 0 auto; border-radius: var(--lp-radius-m); background: var(--lp-surface-sunken, #eeebe4); }
@media (width < 768px) { .companion-portrait { width: 72px; height: 72px; } }
</style>
