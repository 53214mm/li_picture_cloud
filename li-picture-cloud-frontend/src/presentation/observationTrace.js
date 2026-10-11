const scalar = value => value == null || ['string', 'number', 'boolean'].includes(typeof value) ? value : undefined
const pick = (value, fields) => Object.fromEntries(fields.filter(key => value && Object.hasOwn(value, key)).map(key => [key, scalar(value[key])]))
const freeze = value => { if (value && typeof value === 'object') { Object.values(value).forEach(freeze); Object.freeze(value) } return value }
export function observationRecord(request, response, at) {
  if (response.sessionId !== request.sessionId || response.snapshotId !== request.snapshotId || Number(response.sequence) !== request.sequence) throw new Error('RESPONSE_BINDING_FAILED')
  const safe = pick(response, ['sessionId', 'snapshotId', 'sequence', 'reason', 'modelDispatched', 'visionDispatched', 'attemptReserved', 'textRemaining', 'visionRemaining', 'nextObservationSeconds'])
  safe.parsed = response.parsed ? pick(response.parsed, ['action', 'emote', 'text', 'nextObservationSeconds', 'focus', 'topic']) : null
  safe.daily = pick(response.daily, ['text', 'vision', 'nextAt'])
  const trace = response.trace
  if (trace) {
    const snapshot = trace.snapshot
    safe.trace = {
      snapshot: snapshot ? { ...pick(snapshot, ['page', 'chatEpoch', 'capturedAt']),
        pictures: (snapshot.pictures || []).slice(0, 6).map(row => pick(row, ['id', 'name', 'category', 'tags', 'updatedAt'])),
        chat: (snapshot.chat || []).slice(0, 6).map(row => pick(row, ['id', 'role', 'content', 'at'])),
        memories: (snapshot.memories || []).slice(0, 2).map(row => pick(row, ['id', 'pictureId', 'content', 'at'])) } : null,
      messages: (trace.messages || []).slice(0, 2).map(row => pick(row, ['role', 'content'])),
      parameters: pick(trace.parameters, ['provider', 'model', 'stream', 'thinkingMode', 'tokenLimitParameter', 'maxTokens', 'maxCompletionTokens', 'collectionTimeoutSeconds', 'maxOutputChars']),
      ...pick(trace, ['rawBody', 'complete', 'truncated']),
      stream: pick(trace.stream, ['supported', 'httpStatus', 'transportTimeoutMillis', 'contentFrames', 'reasoningPresent', 'finishReason', 'terminal']),
      vision: pick(trace.vision, ['source', 'digest', 'capturedAt', 'cached', 'caption', 'rawBody', 'httpStatus', 'finishReason', 'skip', 'requestBody'])
    }
  } else safe.trace = null
  return freeze({ at, request: pick(request, ['sessionId', 'snapshotId', 'sequence', 'capturePixelDigest']), response: safe })
}
// A local attempt is not a fabricated server/model response. Unknown dispatch stays null.
export function observationAttempt({ id, request, at, stage, reason, sent = false, outcome = null, visualSkip = null }) {
  return freeze({ id, kind: 'LOCAL_ATTEMPT', at, request: pick(request, ['sessionId', 'snapshotId', 'sequence']), response: {
    stage, reason, visualSkip, parsed: null, trace: null,
    modelDispatched: outcome ? outcome.modelDispatched ?? null : sent ? null : false,
    visionDispatched: outcome ? outcome.visionDispatched ?? null : sent ? null : false,
    attemptReserved: outcome ? outcome.attemptReserved ?? null : sent ? null : false
  } })
}
