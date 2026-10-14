import { watch, onMounted, onBeforeUnmount, provide } from 'vue'
import { useCompanionObserverStore } from '@/stores/companionObserver'
import { useCompanionChatStore } from '@/stores/companionChat'
import { SITE_PRESENCE_KEY } from '@/presentation/sitePresence'

export function useCompanionObserver({ enabled, route, panel, legacyOverlay, loggingOut }) {
  if (!enabled) return
  const store = useCompanionObserverStore(); const chat = useCompanionChatStore()
  // Legacy page notifications update material only. They cannot run the old SITE generation path.
  provide(SITE_PRESENCE_KEY, { pictureOpened: store.update, navigated: store.update, uploaded: store.update })
  function suppress() {
    store.coordinator.hold(!!panel.value && panel.value !== 'companion' || legacyOverlay.value)
    const card = !document.hidden && route.name === 'companion' ? document.querySelector('.chat-card') : null
    const visibleDraft = !!chat.state?.draft && (panel.value === 'companion' || !!card?.getClientRects?.().length)
    store.coordinator.suppress(document.hidden || panel.value === 'companion' || loggingOut.value || visibleDraft || !!chat.modeBusy || !!chat.state?.phase && chat.state.phase !== 'idle')
    store.update()
  }
  watch(() => route.fullPath, () => { store.page(route.name); suppress() }, { flush: 'post', immediate: true })
  watch([panel, legacyOverlay, loggingOut, () => chat.state?.phase, () => chat.state?.draft, () => chat.modeBusy], suppress, { flush: 'sync' })
  watch(loggingOut, value => { if (value) store.coordinator.clear() }, { flush: 'sync' })
  let observer
  onMounted(() => {
    suppress(); void store.coordinator.refreshStatus()
    document.addEventListener('visibilitychange', suppress)
    document.addEventListener('scroll', store.update, { capture: true, passive: true })
    window.addEventListener('resize', store.update, { passive: true })
    // Attribute changes/ref replacements are dependencies; ordinary clicks do not reset the observation clock.
    observer = new MutationObserver(suppress)
    observer.observe(document.querySelector('#workspace-main') || document.body, { subtree: true, childList: true, attributes: true, attributeFilter: ['data-observation-picture', 'data-observation-version', 'style', 'hidden'] })
  })
  onBeforeUnmount(() => { observer?.disconnect(); document.removeEventListener('visibilitychange', suppress); document.removeEventListener('scroll', store.update, true); window.removeEventListener('resize', store.update); store.coordinator.hold(true) })
}
