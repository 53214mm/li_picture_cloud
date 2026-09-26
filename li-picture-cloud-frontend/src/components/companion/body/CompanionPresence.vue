<template>
  <CompanionArtwork class="companion-presence" :asset="lingyeAssets.shell" decorative
                    :data-animation-intent="animationIntent.state" data-animation-state="static"
                    :data-presentation-availability="presentation.availability" :data-presentation-activity="presentation.activity"
                    :data-presentation-affect="presentation.affect" :data-presentation-attention="presentation.attention"
                    :data-presentation-rapport="presentation.rapport" :data-presentation-freshness="presentation.freshness" />
</template>
<script setup>
import CompanionArtwork from './CompanionArtwork.vue'
import { lingyeAssets } from './lingyeAssets'
import { computed } from 'vue'
import { mapCompanionAnimation } from '@/presentation/companionAnimation'
const props = defineProps({ presentation: { type: Object, required: true } })
const animationIntent = computed(() => mapCompanionAnimation(props.presentation))
</script>
<style scoped>
.companion-presence { width: 40px; height: 44px; }
</style>
