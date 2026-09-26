<template>
  <div class="presence" :class="{ 'is-drop-target': interaction.dragging }" :aria-current="active ? 'page' : undefined"
       @dragover="interaction.dragOver" @drop.stop="interaction.drop">
    <button type="button" class="presence__mark" aria-label="和绫页互动" aria-haspopup="dialog" title="和绫页互动"
            @click="interaction.open()"><CompanionPresence :presentation="presentation" /></button>
    <router-link to="/companion" class="presence__label" aria-label="伙伴空间" :aria-current="active ? 'page' : undefined">
      <span class="presence__full">{{ interaction.dragging ? '拖入以选图' : '伙伴空间' }}</span><span class="presence__short">伙伴</span>
      <ShellIcon name="chevron" class="presence__arrow" />
    </router-link>
  </div>
</template>
<script setup>
import ShellIcon from '@/components/shell/ShellIcon.vue'
import CompanionPresence from '@/components/companion/body/CompanionPresence.vue'
import { storeToRefs } from 'pinia'
import { useCompanionPresentationStore } from '@/stores/companionPresentation'
import { useCompanionInteractionStore } from '@/stores/companionInteraction'
const interaction = useCompanionInteractionStore()
const { presentation } = storeToRefs(useCompanionPresentationStore())
defineProps({ active: Boolean })
</script>
<style scoped>
/* Static navigation artwork; it does not imply ownership or online status. */
.presence { display: flex; align-items: center; gap: var(--lp-space-3); padding: var(--lp-space-4) var(--lp-space-3); min-height: 80px; border: 1px solid var(--lp-border-strong); border-radius: var(--lp-radius-l); color: var(--lp-accent-strong); background: var(--lp-accent-soft); transition: border-color var(--lp-dur-fast) var(--lp-ease-standard), background-color var(--lp-dur-fast) var(--lp-ease-standard); }
.presence:hover, .presence[aria-current] { border-color: var(--lp-accent); background: var(--lp-surface-elevated); }
.presence__mark { display: grid; place-items: center; width: 40px; height: 44px; flex-shrink: 0; border-radius: var(--lp-radius-full) var(--lp-radius-full) var(--lp-radius-m) var(--lp-radius-m); background: var(--lp-surface); }
.presence__label { flex: 1; display: flex; align-items: center; justify-content: space-between; min-height: 44px; gap: 8px; font-size: 0.875rem; font-weight: 600; white-space: nowrap; }
.presence__mark:hover, .presence__mark:focus-visible { outline: 2px solid var(--lp-accent); outline-offset: 3px; }
.presence.is-drop-target { outline: 2px dashed var(--lp-accent); outline-offset: -4px; }
.presence__arrow { width: 12px; height: 12px; }
.presence__short { display: none; }
@media (768px <= width < 1024px) {
  .presence { flex-direction: column; gap: 8px; padding: 12px 4px; }
  .presence__label { font-size: 0.75rem; }
  .presence__full, .presence__arrow { display: none; }
  .presence__short { display: inline; }
}
@media (prefers-reduced-motion: reduce) { .presence { transition: none; } }
</style>
