<template>
  <button v-if="visible" type="button" class="proposal-hint" aria-label="查看伙伴提议" data-testid="proposal-hint" @click="interaction.open()">
    <span aria-hidden="true">✦</span><span>有一条提议</span>
  </button>
</template>
<script setup>
import { computed } from 'vue'
import { useCompanionInteractionStore } from '@/stores/companionInteraction'
const props = defineProps({ presentation: { type: Object, required: true } })
const interaction = useCompanionInteractionStore()
const visible = computed(() => props.presentation.version === 1 && props.presentation.availability === 'ready' && props.presentation.attention === 'proposal')
</script>
<style scoped>
.proposal-hint { display: inline-flex; align-items: center; justify-content: center; gap: .35rem; min-height: 44px; padding: .3rem .6rem; border: 1px solid var(--lp-border); border-radius: var(--lp-radius-full); background: var(--lp-surface-elevated); color: var(--lp-text-secondary); font-size: .72rem; }
.proposal-hint:focus-visible { outline: 2px solid var(--lp-accent); outline-offset: 2px; }
</style>
