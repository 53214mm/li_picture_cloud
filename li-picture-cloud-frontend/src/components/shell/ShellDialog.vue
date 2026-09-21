<template>
  <Teleport to="body">
    <dialog ref="dialog" class="shell-dialog" :class="`shell-dialog--${side}`" :aria-labelledby="titleId" @cancel.prevent="emit('close')" @click="onBackdrop" @keydown="trapFocus">
      <section class="shell-dialog__panel">
        <header>
          <h2 :id="titleId">{{ title }}</h2>
          <button ref="closeButton" type="button" class="close-button" aria-label="关闭菜单" @click="emit('close')">关闭</button>
        </header>
        <slot />
      </section>
    </dialog>
  </Teleport>
</template>
<script setup>
import { ref, watch, nextTick, onBeforeUnmount, useId } from 'vue'
const props = defineProps({ open: Boolean, title: { type: String, required: true }, side: { type: String, default: 'left' }, returnFocus: { type: Function, default: null } })
const emit = defineEmits(['close'])
const dialog = ref(null)
const closeButton = ref(null)
const titleId = useId()
let opener
let previousOverflow
let locked = false
function release() {
  if (!locked) return
  document.body.style.overflow = previousOverflow
  locked = false
  if (opener?.isConnected && opener.getClientRects().length) opener.focus()
  else props.returnFocus?.()
}
function onBackdrop(event) {
  if (event.target === dialog.value) emit('close')
}
function trapFocus(event) {
  if (event.key !== 'Tab') return
  const items = [...dialog.value.querySelectorAll('a[href],button:not([disabled]),summary,[tabindex="0"]')].filter(el => el.getClientRects().length)
  const first = items[0]
  const last = items.at(-1)
  if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last.focus() }
  else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first.focus() }
}
watch(() => props.open, async open => {
  if (!open) { dialog.value?.close(); release(); return }
  opener = document.activeElement
  await nextTick()
  if (!props.open || !dialog.value) return
  previousOverflow = document.body.style.overflow
  locked = true
  document.body.style.overflow = 'hidden'
  // Native modal dialog provides top-layer placement and makes the background inert.
  dialog.value.showModal()
  closeButton.value.focus()
}, { flush: 'post' })
onBeforeUnmount(() => { dialog.value?.close(); release() })
</script>
<style scoped>
.shell-dialog { position: fixed; inset: 0; width: 100%; height: 100dvh; max-width: none; max-height: none; margin: 0; padding: 0; border: 0; background: transparent; color: var(--lp-text-primary); }
.shell-dialog::backdrop { background: var(--lp-scrim); }
.shell-dialog__panel { width: min(360px, calc(100% - 32px)); height: 100%; overflow-y: auto; overscroll-behavior: contain; background: var(--lp-surface-elevated); padding: max(20px, env(safe-area-inset-top)) 20px max(20px, env(safe-area-inset-bottom)); box-shadow: var(--lp-shadow-l); }
.shell-dialog--right .shell-dialog__panel { margin-left: auto; }
header { display: flex; justify-content: space-between; align-items: center; gap: 16px; margin-bottom: 24px; }
h2 { font-size: 1rem; }
.close-button { min-height: 44px; padding: 8px 12px; color: var(--lp-text-secondary); border-radius: var(--lp-radius-s); }
.close-button:hover { background: var(--lp-bg-subtle); }
</style>
