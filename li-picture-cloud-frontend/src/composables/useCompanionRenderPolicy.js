import { onBeforeUnmount, onMounted, shallowRef } from 'vue'
import { mapCompanionRenderPolicy } from '../presentation/companionRenderPolicy.js'

function readSignal(target, key) {
  try {
    return target?.[key]
  } catch {
    // Restricted or unsupported browser APIs are absent signals, not failures.
    return undefined
  }
}

function listen(target, type, callback) {
  const add = readSignal(target, 'addEventListener')
  const remove = readSignal(target, 'removeEventListener')
  // Do not install a listener that this environment cannot clean up. Browsers
  // without the paired event API still receive the immediate read-once policy.
  if (typeof add !== 'function' || typeof remove !== 'function') return () => {}
  const detach = () => {
    try {
      remove.call(target, type, callback)
    } catch {
      // stop() also permanently rejects callbacks if an API becomes unusable.
    }
  }
  try {
    add.call(target, type, callback)
  } catch {
    detach()
    return () => {}
  }
  return detach
}

/**
 * One mounted consumer, no global subscription or timer. The injected browser
 * surface keeps lifecycle behavior testable without a DOM or network requests.
 * start() is idempotent; stop() is terminal, including for late event callbacks.
 */
export function createCompanionRenderPolicyObserver({ onChange, window: viewport, navigator: browserNavigator } = {}) {
  let started = false
  let stopped = false
  let connection
  let previous
  let detachResize = () => {}
  let detachConnection = () => {}

  function sync() {
    if (!started || stopped) return
    const policy = mapCompanionRenderPolicy({
      viewportWidth: readSignal(viewport, 'innerWidth'),
      saveData: readSignal(connection, 'saveData'),
      effectiveType: readSignal(connection, 'effectiveType')
    })
    if (previous?.compact === policy.compact && previous?.reason === policy.reason) return
    previous = policy
    onChange(policy)
  }

  return {
    start() {
      if (started || stopped) return
      started = true
      connection = readSignal(browserNavigator, 'connection')
      detachResize = listen(viewport, 'resize', sync)
      detachConnection = listen(connection, 'change', sync)
      sync()
    },
    stop() {
      if (stopped) return
      stopped = true
      detachResize()
      detachConnection()
    }
  }
}

export function useCompanionRenderPolicy(environment) {
  const policy = shallowRef(mapCompanionRenderPolicy())
  let observer
  onMounted(() => {
    observer = createCompanionRenderPolicyObserver({
      ...(environment ?? { window: globalThis.window, navigator: globalThis.navigator }),
      onChange: value => { policy.value = value }
    })
    observer.start()
  })
  onBeforeUnmount(() => observer?.stop())
  return { policy }
}
