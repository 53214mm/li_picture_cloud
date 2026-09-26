import { computed, onBeforeUnmount, onMounted, ref } from 'vue'

const overlaySelector = 'dialog[open], .modal-overlay, .share-overlay, .panel-overlay, .fullscreen-overlay'

// Only an actual Home player subscribes. Static Shell/Portrait have no observers.
export function useSpriteVisibility(host) {
  const reducedMotion = ref(true)
  const foreground = ref(false)
  const intersecting = ref(false)
  const unobstructed = ref(false)
  let media, intersection, mutations
  const syncForeground = () => { foreground.value = !document.hidden }
  const syncMotion = () => { reducedMotion.value = media.matches }
  const syncOverlay = () => {
    unobstructed.value = !!host.value && !host.value.closest('[inert], [hidden]') &&
      !Array.from(document.querySelectorAll(overlaySelector)).some(el => el.getClientRects().length)
  }

  onMounted(() => {
    media = window.matchMedia('(prefers-reduced-motion: reduce)')
    syncForeground()
    syncMotion()
    syncOverlay()
    document.addEventListener('visibilitychange', syncForeground)
    media.addEventListener('change', syncMotion)
    // Without IntersectionObserver the safe fallback is a static first frame.
    if (typeof IntersectionObserver !== 'undefined') {
      intersection = new IntersectionObserver(entries => {
        intersecting.value = entries[0]?.isIntersecting === true
      })
      intersection.observe(host.value)
    }
    mutations = new MutationObserver(records => {
      const relevant = records.some(record => {
        if (record.type === 'attributes') {
          return record.target.matches(overlaySelector) || record.target.tagName === 'DIALOG' || record.target.contains(host.value)
        }
        return [...record.addedNodes, ...record.removedNodes].some(node =>
          node.nodeType === 1 && (node.matches(overlaySelector) || node.querySelector(overlaySelector)))
      })
      if (relevant) syncOverlay()
    })
    mutations.observe(document.body, {
      subtree: true, childList: true, attributes: true,
      attributeFilter: ['open', 'inert', 'hidden', 'class', 'style']
    })
  })
  onBeforeUnmount(() => {
    document.removeEventListener('visibilitychange', syncForeground)
    media?.removeEventListener('change', syncMotion)
    intersection?.disconnect()
    mutations?.disconnect()
  })

  return {
    reducedMotion,
    canPlay: computed(() => foreground.value && intersecting.value && unobstructed.value && !reducedMotion.value)
  }
}
