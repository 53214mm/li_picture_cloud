<template>
  <label class="lp-input" :class="{ 'lp-input--error': Boolean(error) }">
    <span v-if="label" class="lp-input__label">{{ label }}</span>
    <input
      v-bind="$attrs"
      :id="resolvedId"
      class="lp-input__control"
      :value="modelValue"
      :disabled="disabled"
      :aria-invalid="error ? 'true' : undefined"
      :aria-describedby="descriptionId"
      @input="$emit('update:modelValue', $event.target.value)"
    />
    <span v-if="error" :id="errorId" class="lp-input__message lp-input__message--error">
      {{ error }}
    </span>
    <span v-else-if="hint" :id="hintId" class="lp-input__message">{{ hint }}</span>
  </label>
</template>

<script>
let nextInputId = 0
</script>

<script setup>
import { computed } from 'vue'

defineOptions({ inheritAttrs: false })

const props = defineProps({
  modelValue: {
    type: [String, Number],
    default: ''
  },
  id: {
    type: String,
    default: ''
  },
  label: {
    type: String,
    default: ''
  },
  hint: {
    type: String,
    default: ''
  },
  error: {
    type: String,
    default: ''
  },
  disabled: {
    type: Boolean,
    default: false
  }
})

defineEmits(['update:modelValue'])

const fallbackId = `lp-input-${++nextInputId}`
const resolvedId = computed(() => props.id || fallbackId)
const hintId = computed(() => `${resolvedId.value}-hint`)
const errorId = computed(() => `${resolvedId.value}-error`)
const descriptionId = computed(() => {
  if (props.error) return errorId.value
  if (props.hint) return hintId.value
  return undefined
})
</script>

<style scoped>
.lp-input {
  display: grid;
  gap: var(--lp-space-2);
  width: 100%;
}

.lp-input__label {
  color: var(--lp-text-primary);
  font-size: var(--lp-font-size-small);
  font-weight: var(--lp-font-weight-medium);
}

.lp-input__control {
  width: 100%;
  min-height: 44px;
  padding: var(--lp-space-3) var(--lp-space-4);
  border: 1px solid var(--lp-border-strong);
  border-radius: var(--lp-radius-s);
  outline: none;
  background: var(--lp-surface-elevated);
  color: var(--lp-text-primary);
  transition:
    border-color var(--lp-dur-fast) var(--lp-ease-standard),
    box-shadow var(--lp-dur-fast) var(--lp-ease-standard),
    background-color var(--lp-dur-fast) var(--lp-ease-standard);
}

.lp-input__control::placeholder {
  color: var(--lp-text-tertiary);
}

.lp-input__control:hover:not(:disabled) {
  border-color: var(--lp-text-secondary);
}

.lp-input__control:focus-visible {
  border-color: var(--lp-focus-ring);
  outline: 2px solid var(--lp-focus-ring);
  outline-offset: 2px;
}

.lp-input--error .lp-input__control {
  border-color: var(--lp-danger);
}

.lp-input__control:disabled {
  cursor: not-allowed;
  background: var(--lp-bg-subtle);
  color: var(--lp-text-secondary);
  opacity: 0.72;
}

.lp-input__message {
  color: var(--lp-text-secondary);
  font-size: var(--lp-font-size-caption);
  line-height: 1.4;
}

.lp-input__message--error {
  color: var(--lp-danger);
}
</style>
