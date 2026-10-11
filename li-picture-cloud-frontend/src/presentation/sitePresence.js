import { createPresenceSession } from './companionPresence.js'
import { createPresenceTransport } from './companionPresenceTransport.js'

export const SITE_PRESENCE_KEY = Symbol('site-presence')
const pages = Object.freeze({ companion: 'ARRIVAL', gallery: 'GALLERY', 'picture-detail': 'PICTURE_VIEW' })

// Memory-only, shell-owned coordinator. The route name is used locally only;
// picture payloads, IDs, URLs, page text and form contents never enter this API.
export function createSitePresence({ api, now = Date.now, onChange = () => {} }) {
  const transport = createPresenceTransport(api)
  let owner = null; let routeName = null; let sequence = 0; let disposed = false
  let pictureSeen = false
  let hidden = false; let blocked = false; let navigating = false
  const session = createPresenceSession({ now, cooldownMs: 45000, onChange,
    decide: (input, options) => transport.decide(input, { ...options, limit: session.state.limit }) })
  function stop() { transport.stop().catch(() => { /* Expiry is server bounded; no autonomous server work. */ }) }
  function permitted() { return !disposed && !!owner && !!pages[routeName] && !hidden && !blocked && !navigating }
  function sync() { session.configure({ visible: permitted() }) }
  function setOwner(value) {
    const next = value == null ? null : String(value)
    if (next === owner) return
    owner = next; pictureSeen = false; session.reset(); stop(); sync()
  }
  function configure(patch) {
    session.configure({ ...patch, ...(patch.enabled && !permitted() ? { enabled: false } : {}) })
    stop()
  }
  function suppress({ isHidden = hidden, isBlocked = blocked } = {}) {
    if (hidden === isHidden && blocked === isBlocked) return
    hidden = isHidden; blocked = isBlocked; sync()
  }
  function navigationStarted(toName) {
    navigating = true; sync()
    // The isolated rehearsal shares the backend's single-owner session slot.
    if (toName === 'companion-rehearsal') configure({ enabled: false })
  }
  function event(kind) {
    // A newer event invalidates pending output even if cooldown/budget discards it.
    session.cancel({ input: null })
    if (!permitted() || !session.state.enabled) return Promise.resolve(false)
    return session.run({ id: `site-${++sequence}`, kind, occurredAt: now() })
  }
  function navigated(name, { failed = false } = {}) {
    const previous = routeName
    routeName = name; navigating = false; sync()
    if (!failed) pictureSeen = false
    if (name === 'companion-rehearsal') configure({ enabled: false })
    if (!failed && previous !== name && ['companion', 'gallery'].includes(name)) return event(pages[name])
    return Promise.resolve(false)
  }
  function pictureOpened() {
    if (routeName !== 'picture-detail' || pictureSeen) return Promise.resolve(false)
    pictureSeen = true
    return event('PICTURE_VIEW')
  }
  function logout() { pictureSeen = false; session.reset(); stop(); owner = null }
  function dispose() { disposed = true; logout() }
  return { configure, setOwner, suppress, navigationStarted, navigated, pictureOpened, logout, dispose,
    get state() { return session.state }, get supported() { return !!pages[routeName] } }
}
