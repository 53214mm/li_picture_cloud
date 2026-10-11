// Jackson serializes Java long as a decimal string in production. Compare the
// canonical representation directly; never round or coerce arbitrary wire data.
function matchesSequence(actual, expected) {
  if (!Number.isSafeInteger(expected) || expected <= 0) return false
  return typeof actual === 'string'
    ? actual === String(expected)
    : Number.isSafeInteger(actual) && actual === expected
}

// Session creation is deliberately not aborted: an aborted POST can still replace
// the server's one session per owner. Drain it and close its returned ID before
// another transport (including the rehearsal) may open a replacement.
const sessionQueues = new WeakMap()
function serializeSession(api, action) {
  const previous = sessionQueues.get(api) || Promise.resolve()
  const next = previous.catch(() => {}).then(action)
  sessionQueues.set(api, next.catch(() => {}))
  return next
}

// Thin, injectable transport. Session identifiers are ephemeral and never persisted.
export function createPresenceTransport(api, { mode = 'DEMO' } = {}) {
  // Mode is captured once: a shared/default transport can never opt into billing
  // through mutable settings or per-decision arguments.
  if (!['DEMO', 'BYOK'].includes(mode)) throw new Error('Unsupported presence mode')
  let session = null; let generation = 0; let sequence = 0
  async function stop() {
    generation++
    const previous = session; session = null; sequence = 0
    await serializeSession(api, async () => { if (previous?.sessionId) await api.close(previous.sessionId) })
  }
  async function decide(input, { signal, intensity = 'LOW', limit = 12 } = {}) {
    const cycle = generation
    await serializeSession(api, async () => {
      if (signal?.aborted || cycle !== generation) throw new Error('Presence session superseded')
      if (session) return
      const opened = await api.open({ enabled: true, mode, maxDecisions: Math.min(12, limit), muted: false, intensity })
      if (signal?.aborted || cycle !== generation || !opened?.sessionId || opened.mode !== mode) {
        if (opened?.sessionId) await api.close(opened.sessionId)
        throw new Error('Presence session superseded or unexpected')
      }
      session = opened
    })
    if (signal?.aborted || cycle !== generation || !session) throw new Error('Presence session superseded')
    const current = session
    if (!Number.isSafeInteger(sequence + 1)) throw new Error('Presence sequence exhausted')
    const sent = { sessionId: current.sessionId, eventId: input.id, sequence: ++sequence,
      observedAt: new Date(input.occurredAt).toISOString(), eventType: input.eventType, page: input.page }
    const result = await api.decide(sent, { signal })
    if (signal?.aborted || cycle !== generation || result?.sessionId !== current.sessionId || result?.eventId !== sent.eventId || !matchesSequence(result?.sequence, sent.sequence) || result?.mode !== mode) throw new Error('Presence result superseded or mismatched')
    return { action: result.action, text: result.text || ({ WAVE: '挥挥手', NOD: '点点头', SPARKLE: '亮一下' }[result.emote] || ''), source: mode === 'BYOK' ? 'SERVER_BYOK' : 'SERVER_DEMO', reason: result.reason, remainingDecisions: result.remainingDecisions, expiresAt: result.expiresAt }
  }
  return { decide, stop }
}
