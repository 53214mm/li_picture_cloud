<template>
  <component
    :is="as"
    class="lp-surface"
    :class="[`lp-surface--${variant}`, { 'lp-surface--hairline': hairline }]"
  >
    <slot />
  </component>
</template>

<script setup>
defineProps({
  as: {
    type: String,
    default: 'div'
  },
  variant: {
    type: String,
    default: 'default',
    validator: (value) => ['default', 'elevated'].includes(value)
  },
  hairline: {
    type: Boolean,
    default: false
  }
})
</script>

<style scoped>
.lp-surface {
  border-radius: var(--lp-radius-m);
  background: var(--lp-surface);
}

.lp-surface--elevated {
  background: var(--lp-surface-elevated);
  box-shadow: var(--lp-shadow-m);
}

.lp-surface--hairline {
  border: 1px solid var(--lp-border);
}
</style>
