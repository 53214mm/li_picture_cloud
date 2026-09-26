<template>
  <CompanionArtwork class="companion-presence" :asset="lingyeAssets.shell" decorative
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
.companion-presence { width: 40px; height: 44px; }
</style>
