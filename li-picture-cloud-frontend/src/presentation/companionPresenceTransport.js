// Jackson serializes Java long as a decimal string in production. Compare the
// canonical representation directly; never round or coerce arbitrary wire data.
function matchesSequence(actual, expected) {
  if (!Number.isSafeInteger(expected) || expected <= 0) return false
  return typeof actual === 'string'
    ? actual === String(expected)
    : Number.isSafeInteger(actual) && actual === expected
}

// Thin, injectable transport. Session identifiers are ephemeral and never persisted.
export function createPresenceTransport(api) {
  let session = null; let generation = 0; let sequence = 0
  async function stop() {
    generation++
    const previous = session; session = null; sequence = 0
    if (previous?.sessionId) await api.close(previous.sessionId)
  }
  async function decide(input, { signal, intensity = 'LOW', limit = 12 } = {}) {
    const cycle = generation
    if (!session) {
      const opened = await api.open({ enabled: true, mode: 'DEMO', maxDecisions: Math.min(12, limit), muted: false, intensity }, { signal })
      if (signal?.aborted || cycle !== generation) {
        if (opened?.sessionId) await api.close(opened.sessionId)
        throw new Error('Presence session superseded')
      }
      if (!opened?.sessionId || opened.mode !== 'DEMO') throw new Error('Unexpected presence session')
      session = opened
    }
    const current = session
    if (!Number.isSafeInteger(sequence + 1)) throw new Error('Presence sequence exhausted')
    const sent = { sessionId: current.sessionId, eventId: input.id, sequence: ++sequence,
      observedAt: new Date(input.occurredAt).toISOString(), eventType: input.eventType, page: input.page }
    const result = await api.decide(sent, { signal })
    if (signal?.aborted || cycle !== generation || result?.sessionId !== current.sessionId || result?.eventId !== sent.eventId || !matchesSequence(result?.sequence, sent.sequence) || result?.mode !== 'DEMO') throw new Error('Presence result superseded or mismatched')
    return { action: result.action, text: result.text || ({ WAVE: '挥挥手', NOD: '点点头', SPARKLE: '亮一下' }[result.emote] || ''), source: 'SERVER_DEMO', reason: result.reason, remainingDecisions: result.remainingDecisions, expiresAt: result.expiresAt }
  }
  return { decide, stop }
}
