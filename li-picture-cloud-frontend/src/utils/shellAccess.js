// A failed bootstrap is not a guest session. Never mount a protected page in either state.
export function routeAccess(meta, user) {
  if (!meta.requiresAuth) return 'allow'
  if (!user.authReady) return user.authBootstrapError ? 'auth-error' : 'loading'
  if (!user.isLoggedIn) return 'login'
  if (meta.requiresAdmin && !user.isAdmin) return 'forbidden'
  return 'allow'
}

export function loginDestination(value, resolve) {
  const fallback = '/space/my'
  if (typeof value !== 'string' || !value.startsWith('/') || value.startsWith('//') || value.includes('\\') || [...value].some(char => char.charCodeAt(0) <= 32)) return fallback
  // Encoded separators must not turn a local pathname into a network-path reference.
  if (/%(?:2f|5c|00|0a|0d)/i.test(value.split(/[?#]/)[0])) return fallback
  try {
    const target = resolve(value)
    if (!target.matched.length || ['login', 'register', 'unavailable'].includes(target.name)) return fallback
    return value
  } catch {
    return fallback
  }
}
