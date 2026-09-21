<template>
  <section
    class="lp-state-block"
    :class="`lp-state-block--${status}`"
    :role="status === 'error' ? 'alert' : 'status'"
    :aria-live="status === 'error' ? 'assertive' : 'polite'"
    :aria-busy="status === 'loading' ? 'true' : undefined"
  >
    <slot name="icon">
      <span v-if="status === 'loading'" class="lp-state-block__spinner lp-motion-spin" aria-hidden="true" />
      <span v-else class="lp-state-block__mark" aria-hidden="true">
        {{ status === 'error' ? '!' : '○' }}
      </span>
    </slot>
    <div class="lp-state-block__copy">
      <h3>{{ title || defaultTitle }}</h3>
      <p v-if="message">{{ message }}</p>
    </div>
    <div v-if="$slots.action" class="lp-state-block__action">
      <slot name="action" />
    </div>
  </section>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  status: {
    type: String,
    default: 'empty',
    validator: (value) => ['loading', 'empty', 'error'].includes(value)
  },
  title: {
    type: String,
    default: ''
  },
  message: {
    type: String,
    default: ''
  }
})

const labels = {
  loading: '正在加载',
  empty: '暂无内容',
  error: '出现问题'
}

const defaultTitle = computed(() => labels[props.status])
</script>

<style scoped>
.lp-state-block {
  display: grid;
  justify-items: center;
  gap: var(--lp-space-3);
  width: 100%;
  padding: var(--lp-space-7) var(--lp-space-5);
  border-radius: var(--lp-radius-m);
  background: var(--lp-bg-subtle);
  color: var(--lp-text-secondary);
  text-align: center;
}

.lp-state-block--error {
  background: var(--lp-danger-soft);
}

.lp-state-block__spinner,
.lp-state-block__mark {
  width: var(--lp-space-5);
  height: var(--lp-space-5);
}

.lp-state-block__spinner {
  border: 2px solid var(--lp-border-strong);
  border-top-color: var(--lp-accent);
  border-radius: var(--lp-radius-full);
}

.lp-state-block__mark {
  display: grid;
  place-items: center;
  border: 1px solid currentColor;
  border-radius: var(--lp-radius-full);
  font-size: var(--lp-font-size-small);
  font-weight: var(--lp-font-weight-semibold);
}

.lp-state-block--error .lp-state-block__mark {
  color: var(--lp-danger);
}

.lp-state-block__copy {
  display: grid;
  gap: var(--lp-space-1);
  max-width: 40ch;
}

.lp-state-block__copy h3 {
  font-size: var(--lp-font-size-body);
}

.lp-state-block__copy p {
  font-size: var(--lp-font-size-small);
}

.lp-state-block__action {
  margin-top: var(--lp-space-1);
}
</style>
