// Intent-only lifecycle signal, installed before asynchronous authentication guards,
// so a page can drop stale loads as soon as a navigation starts. Never retained or sent to a server.
const listeners = new Set()
export function onSiteNavigationIntent(listener) {
  listeners.add(listener)
  return () => listeners.delete(listener)
}
export function siteNavigationIntent(to) {
  for (const listener of listeners) listener(to)
}
