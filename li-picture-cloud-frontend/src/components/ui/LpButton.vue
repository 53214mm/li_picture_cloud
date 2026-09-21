<template>
  <button
    class="lp-button"
    :class="[`lp-button--${resolvedVariant}`, `lp-button--${size}`]"
    :type="type"
    :disabled="disabled"
  >
    <slot />
  </button>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  variant: {
    type: String,
    default: 'primary',
    validator: (value) => ['primary', 'quiet', 'secondary', 'danger'].includes(value)
  },
  size: {
    type: String,
    default: 'md',
    validator: (value) => ['sm', 'md'].includes(value)
  },
  type: {
    type: String,
    default: 'button',
    validator: (value) => ['button', 'submit', 'reset'].includes(value)
  },
  disabled: {
    type: Boolean,
    default: false
  }
})

const resolvedVariant = computed(() => (props.variant === 'secondary' ? 'quiet' : props.variant))
</script>

<style scoped>
.lp-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: var(--lp-space-2);
  border: 1px solid transparent;
  border-radius: var(--lp-radius-s);
  font-weight: var(--lp-font-weight-semibold);
  line-height: 1;
  cursor: pointer;
  transition:
    color var(--lp-dur-fast) var(--lp-ease-standard),
    background-color var(--lp-dur-fast) var(--lp-ease-standard),
    border-color var(--lp-dur-fast) var(--lp-ease-standard),
    transform var(--lp-dur-fast) var(--lp-ease-standard);
}

.lp-button--sm {
  min-height: 36px;
  padding: var(--lp-space-2) var(--lp-space-3);
  font-size: var(--lp-font-size-small);
}

.lp-button--md {
  min-height: 44px;
  padding: var(--lp-space-3) var(--lp-space-5);
  font-size: var(--lp-font-size-body);
}

.lp-button--primary {
  background: var(--lp-accent);
  color: var(--lp-on-accent);
}

.lp-button--primary:hover:not(:disabled) {
  background: var(--lp-accent-strong);
}

.lp-button--quiet {
  border-color: var(--lp-border);
  background: var(--lp-surface);
  color: var(--lp-text-primary);
}

.lp-button--quiet:hover:not(:disabled) {
  border-color: var(--lp-border-strong);
  background: var(--lp-bg-subtle);
}

.lp-button--danger {
  background: var(--lp-danger);
  color: var(--lp-on-accent);
}

.lp-button:hover:not(:disabled) {
  transform: translateY(-1px);
}

.lp-button:active:not(:disabled) {
  transform: translateY(0);
}

.lp-button:disabled {
  cursor: not-allowed;
  opacity: 0.48;
}
</style>
