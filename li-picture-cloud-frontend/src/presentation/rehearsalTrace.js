const kinds = new Set(['ARRIVAL', 'PICTURE_VIEW', 'UPLOAD_SUCCESS', 'GALLERY'])
const events = new Set(['PAGE_ENTER', 'PICTURE_OPENED', 'UPLOAD_SUCCEEDED'])
const pages = new Set(['COMPANION', 'GALLERY', 'PICTURE_DETAIL', 'UPLOAD'])
const id = value => typeof value === 'string' && /^[A-Za-z0-9_-]{1,64}$/.test(value) ? value : null
const number = (value, max = 4096) => Number.isSafeInteger(value) && value >= 0 && value <= max ? value : null
const text = (value, max) => typeof value === 'string' ? value.slice(0, max) : null
export function snapshotRehearsalInput(input) {
  if (!input || !id(input.id) || !kinds.has(input.kind) || !events.has(input.eventType) || !pages.has(input.page)) return null
  return Object.freeze({ id: input.id, kind: input.kind, eventType: input.eventType, page: input.page, occurredAt: input.occurredAt })
}
export function snapshotRehearsalSettings(settings) {
  if (!settings) return null
  return Object.freeze({ muted: settings.muted === true, intensity: settings.intensity === 'NORMAL' ? 'NORMAL' : 'LOW', cooldownSeconds: number(settings.cooldownSeconds, 30), limit: number(settings.limit, 12) })
}
export function snapshotPresenceRequest(input) {
  if (!input || !id(input.sessionId) || !id(input.eventId) || !events.has(input.eventType) || !pages.has(input.page) || !Number.isSafeInteger(Number(input.sequence)) || Number(input.sequence) < 1 || !Number.isFinite(Date.parse(input.observedAt))) return null
  return Object.freeze({ sessionId: input.sessionId, eventId: input.eventId, sequence: String(input.sequence), observedAt: input.observedAt, eventType: input.eventType, page: input.page })
}
// Explicit allowlist: never copy an entire API object, route, headers or reasoning.
export function snapshotRehearsalTrace(value, request = null) {
  if (!value || typeof value !== 'object') return null
  const event = snapshotPresenceRequest(value.event)
  if (!event || value.event.scope !== 'REHEARSAL') return null
  if (request && ['sessionId', 'eventId', 'sequence', 'observedAt', 'eventType', 'page'].some(key => String(event[key]) !== String(request[key]))) throw new Error('Mismatched rehearsal trace')
  const parameters = value.parameters ? Object.freeze({ provider: text(value.parameters.provider, 160), model: text(value.parameters.model, 160), stream: value.parameters.stream === true, maxTokens: number(value.parameters.maxTokens, 256), collectionTimeoutSeconds: number(value.parameters.collectionTimeoutSeconds, 8), maxOutputChars: number(value.parameters.maxOutputChars, 1024), thinking: value.parameters.thinking === 'NOT_SENT' ? 'NOT_SENT' : null, temperature: value.parameters.temperature === 'NOT_SENT' ? 'NOT_SENT' : null }) : null
  const messages = Array.isArray(value.messages) && value.messages.length <= 2 ? Object.freeze(value.messages.filter(turn => ['system', 'user'].includes(turn?.role) && typeof turn.content === 'string').map(turn => Object.freeze({ role: turn.role, content: turn.content.slice(0, 8192) }))) : null
  const parsed = value.parsedDecision && ['SILENT', 'EMOTE', 'SPEAK'].includes(value.parsedDecision.action) ? Object.freeze({ action: value.parsedDecision.action, emote: ['WAVE', 'NOD', 'SPARKLE'].includes(value.parsedDecision.emote) ? value.parsedDecision.emote : null, text: text(value.parsedDecision.text, 100) }) : null
  const stream = value.stream ? Object.freeze({ supported: value.stream.supported === true, httpStatus: number(value.stream.httpStatus, 599), transportTimeoutMillis: number(value.stream.transportTimeoutMillis, 3600000), contentFrames: number(value.stream.contentFrames), reasoningPresent: typeof value.stream.reasoningPresent === 'boolean' ? value.stream.reasoningPresent : null, finishReason: ['stop', 'length', 'content_filter', 'tool_calls', 'function_call', 'OTHER'].includes(value.stream.finishReason) ? value.stream.finishReason : null, terminal: ['FINISH_REASON', 'DONE', 'EOF', 'ERROR', 'TIMEOUT', 'CANCELLED'].includes(value.stream.terminal) ? value.stream.terminal : null }) : null
  return Object.freeze({ event: Object.freeze({ ...event, scope: 'REHEARSAL' }), parameters, messages, rawBody: text(value.rawBody, 1024), rawBodyComplete: value.rawBodyComplete === true, rawBodyTruncated: value.rawBodyTruncated === true, parsedDecision: parsed, stream, redactedFields: Object.freeze(Array.isArray(value.redactedFields) ? value.redactedFields.filter(field => field === 'CREDENTIAL').slice(0, 1) : []) })
}
