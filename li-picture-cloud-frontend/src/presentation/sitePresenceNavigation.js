// Intent-only lifecycle signal, installed before asynchronous authentication guards.
// Never retained or sent to a server.
const listeners = new Set()
export function onSiteNavigationIntent(listener) {
  listeners.add(listener)
  return () => listeners.delete(listener)
}
export function siteNavigationIntent(to) {
  for (const listener of listeners) listener(to)
}
