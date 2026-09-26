<template>
  <router-link to="/companion" class="presence" :aria-current="active ? 'page' : undefined" aria-label="伙伴空间">
    <span class="presence__mark"><CompanionPresence :presentation="presentation" /></span>
    <span class="presence__label"><span class="presence__full">伙伴空间</span><span class="presence__short">伙伴</span></span>
    <ShellIcon name="chevron" class="presence__arrow" />
  </router-link>
</template>
<script setup>
import ShellIcon from '@/components/shell/ShellIcon.vue'
import CompanionPresence from '@/components/companion/body/CompanionPresence.vue'
import { storeToRefs } from 'pinia'
import { useCompanionPresentationStore } from '@/stores/companionPresentation'
const { presentation } = storeToRefs(useCompanionPresentationStore())
defineProps({ active: Boolean })
</script>
<style scoped>
/* Static navigation artwork; it does not imply ownership or online status. */
.presence { display: flex; align-items: center; gap: var(--lp-space-3); padding: var(--lp-space-4) var(--lp-space-3); min-height: 80px; border: 1px solid var(--lp-border-strong); border-radius: var(--lp-radius-l); color: var(--lp-accent-strong); background: var(--lp-accent-soft); transition: border-color var(--lp-dur-fast) var(--lp-ease-standard), background-color var(--lp-dur-fast) var(--lp-ease-standard); }
.presence:hover, .presence[aria-current] { border-color: var(--lp-accent); background: var(--lp-surface-elevated); }
.presence__mark { display: grid; place-items: center; width: 40px; height: 44px; flex-shrink: 0; border-radius: var(--lp-radius-full) var(--lp-radius-full) var(--lp-radius-m) var(--lp-radius-m); background: var(--lp-surface); }
.presence__label { flex: 1; font-size: 0.875rem; font-weight: 600; white-space: nowrap; }
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
