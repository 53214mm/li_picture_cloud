import { ref, onMounted, onBeforeUnmount } from 'vue'

// Compatibility boundary for existing v-if/Teleport overlays. Do not make their
// ancestor main inert: it also contains the dialog itself. Only Shell chrome uses
// this flag. Replace this adapter with explicit overlay ownership as pages migrate.
export function useLegacyOverlayIsolation() {
  const active = ref(false)
  let observer
  function sync() {
    active.value = [...document.querySelectorAll('.modal-overlay, .share-overlay, .panel-overlay, .fullscreen-overlay')]
      .some(element => element.getClientRects().length > 0)
  }
  onMounted(() => {
    sync()
    observer = new MutationObserver(sync)
    observer.observe(document.body, { subtree: true, childList: true })
  })
  onBeforeUnmount(() => observer?.disconnect())
  return active
}
