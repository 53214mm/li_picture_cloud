<template>
  <CompanionArtwork class="companion-presence" :asset="lingyeAssets.shell" decorative />
</template>
<script setup>
import CompanionArtwork from './CompanionArtwork.vue'
import { lingyeAssets } from './lingyeAssets'
</script>
<style scoped>
.companion-presence { width: 40px; height: 44px; }
</style>
