<template>
  <main id="workspace-main" tabindex="-1" class="app-main" :class="[`frame--${frame}`, `workspace--${workspace}`]">
    <slot />
  </main>
</template>
<script setup>
defineProps({
  frame: { type: String, default: 'legacy', validator: value => ['legacy', 'managed'].includes(value) },
  workspace: { type: String, default: 'wide', validator: value => ['text', 'wide', 'fluid'].includes(value) }
})
</script>
<style scoped>
.app-main { min-width: 0; width: 100%; flex: 1; }
/* No padding or hard width on legacy frames: existing pages own their layout. */
.frame--managed { margin-inline: auto; padding: var(--lp-gutter); }
.frame--managed.workspace--text { max-width: var(--lp-content-text); }
.frame--managed.workspace--wide { max-width: var(--lp-content-wide); }
.frame--managed.workspace--fluid { max-width: none; }
@media (width < 768px) { .frame--managed { padding: var(--lp-space-4); } }
@media (width < 480px) { .frame--managed { padding: var(--lp-space-3); } }
</style>
