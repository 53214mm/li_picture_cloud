import { onBeforeUnmount, onMounted, provide, shallowRef, watch } from 'vue'
import { useCompanionChatStore } from '@/stores/companionChat'
import { presenceApi } from '@/api/companionPresence'
import { onSiteNavigationIntent } from '@/presentation/sitePresenceNavigation'
import { createSitePresence, SITE_PRESENCE_KEY } from '@/presentation/sitePresence'

export function useSitePresence({ enabled, user, route, router, panel, legacyOverlay, loggingOut }) {
  const state = shallowRef(null)
  if (!enabled) return { state }
  const chat = useCompanionChatStore()
  const coordinator = createSitePresence({ api: presenceApi, onChange: value => { state.value = value } })
  provide(SITE_PRESENCE_KEY, coordinator)
  let focused = false; let pendingNavigation = null
  function suppress() {
    coordinator.suppress({ isHidden: document.hidden,
      isBlocked: !!panel.value || legacyOverlay.value || loggingOut.value || focused ||
        chat.state?.phase !== 'idle' || !!chat.state?.draft || chat.modeBusy })
  }
  function focus(event) { focused = !!event.target?.closest?.('.chat-card'); suppress() }
  watch(() => user.currentUser?.id, id => coordinator.setOwner(id), { flush: 'sync', immediate: true })
  watch([panel, legacyOverlay, loggingOut, () => chat.state?.phase, () => !!chat.state?.draft, () => chat.modeBusy], suppress, { flush: 'sync' })
  watch(loggingOut, value => {
    if (value) coordinator.logout()
    else coordinator.setOwner(user.currentUser?.id)
  }, { flush: 'sync' })
  const removeBefore = onSiteNavigationIntent(to => { pendingNavigation = to; coordinator.navigationStarted(to.name) })
  const removeAfter = router.afterEach((to, from, failure) => {
    if (pendingNavigation && pendingNavigation !== to) return
    pendingNavigation = null
    focused = false
    suppress()
    coordinator.navigated(failure ? route.name : to.name, { failed: !!failure })
  })
  const removeError = router.onError((error, to) => {
    if (pendingNavigation && pendingNavigation !== to) return
    pendingNavigation = null
    coordinator.navigated(route.name, { failed: true })
  })
  coordinator.navigated(route.name)
  onMounted(() => {
    suppress()
    document.addEventListener('visibilitychange', suppress)
    document.addEventListener('focusin', focus)
  })
  onBeforeUnmount(() => {
    removeBefore(); removeAfter(); removeError(); coordinator.dispose()
    document.removeEventListener('visibilitychange', suppress)
    document.removeEventListener('focusin', focus)
  })
  return { state, coordinator }
}
