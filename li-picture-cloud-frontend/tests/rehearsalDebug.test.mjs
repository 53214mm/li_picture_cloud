import test from 'node:test'
import assert from 'node:assert/strict'
import { createPresenceSession } from '../src/presentation/companionPresence.js'
import * as history from '../src/presentation/rehearsalHistory.js'
import { snapshotRehearsalTrace } from '../src/presentation/rehearsalTrace.js'
import { createPresenceTransport } from '../src/presentation/companionPresenceTransport.js'

const request = { sessionId: 's', eventId: 'one', sequence: 1, observedAt: '1970-01-01T00:00:01.000Z', eventType: 'PAGE_ENTER', page: 'COMPANION' }
function trace(sent = request, rawBody = '{"action":"SILENT","emote":null,"text":null}') {
  return { event: { ...sent, scope: 'REHEARSAL' }, messages: [{ role: 'system', content: '业务提示' }, { role: 'user', content: 'eventType=PAGE_ENTER;page=COMPANION' }], parameters: { provider: 'DEEPSEEK', model: 'test-model', stream: true, maxTokens: 160, collectionTimeoutSeconds: 8, maxOutputChars: 1024, thinking: 'NOT_SENT', temperature: 'NOT_SENT' }, rawBody, rawBodyComplete: true, rawBodyTruncated: false, parsedDecision: rawBody ? { action: 'SILENT', emote: null, text: null } : null, stream: { supported: true, httpStatus: 200, transportTimeoutMillis: 5000, contentFrames: rawBody ? 1 : 0, reasoningPresent: true, finishReason: rawBody ? 'stop' : 'length', terminal: 'FINISH_REASON' }, redactedFields: [] }
}

test('server cooldown refuses a decision without exhausting client decisions and exposes next allowance', async () => {
  const session = createPresenceSession({ now: () => 1000, decide: async () => ({ action: 'SILENT', source: 'SERVER_BYOK', reason: 'COOLDOWN', remainingDecisions: 6, nextAllowedAt: '1970-01-01T00:00:16Z', attemptConsumed: false }) })
  session.configure({ enabled: true, limit: 1 })
  await session.run({ id: 'first', kind: 'ARRIVAL', occurredAt: 1000 })
  assert.equal(session.state.used, 0)
  assert.equal(session.state.nextAllowedAt, 16000)
})
test('journal survives route exit, refuses late completions, isolates account and caps retained records', () => {
  const journal = history.createRehearsalHistory()
  journal.ownerChanged('a')
  const id = journal.start({ kind: 'ARRIVAL', mode: 'BYOK', at: 1000 })
  journal.leave()
  assert.equal(journal.records.length, 1)
  assert.equal(journal.records[0].category, 'CANCELLED')
  journal.finish(id, { action: 'SPEAK', reason: 'DECIDED', source: 'SERVER_BYOK', text: 'late' })
  assert.equal(journal.records[0].category, 'CANCELLED')
  journal.ownerChanged('b')
  assert.equal(journal.records.length, 0)
  journal.finish(id, { action: 'SPEAK', reason: 'DECIDED', source: 'SERVER_BYOK' })
  assert.equal(journal.records.length, 0)
  for (let i = 0; i < 52; i++) journal.start({ kind: 'GALLERY', mode: 'LOCAL', at: i })
  assert.equal(journal.records.length, 50)
  journal.clear()
  assert.equal(journal.records.length, 0)
})
test('empty output is failure, actual DECIDED SILENT alone is genuine silence and client gates never claim a call', () => {
  assert.equal(history.classifyRehearsalResult({ source: 'SERVER_BYOK', reason: 'EMPTY_OUTPUT', action: 'SILENT' }), 'FAILED')
  assert.equal(history.classifyRehearsalResult({ source: 'SERVER_BYOK', reason: 'COOLDOWN', action: 'SILENT' }), 'GATED')
  assert.equal(history.classifyRehearsalResult({ source: 'SERVER_BYOK', reason: 'DECIDED', action: 'SILENT' }), 'DECIDED')
  assert.equal(history.classifyRehearsalResult(null), 'NOT_REQUESTED')
})

test('journal keeps immutable per-request input rather than live settings and refuses late trace after clearing', () => {
  const journal = history.createRehearsalHistory(); journal.ownerChanged('a')
  const input = { id: 'one', kind: 'ARRIVAL', eventType: 'PAGE_ENTER', page: 'COMPANION', occurredAt: 1000 }
  const settings = { muted: false, intensity: 'LOW', cooldownSeconds: 5, limit: 12 }
  const id = journal.start({ kind: 'ARRIVAL', mode: 'BYOK', at: 1000, input, settings })
  input.page = 'GALLERY'; settings.muted = true
  assert.equal(journal.records[0].inputSnapshot.page, 'COMPANION')
  assert.equal(journal.records[0].settingsSnapshot.muted, false)
  journal.clear(); journal.finish(id, { source: 'SERVER_BYOK', reason: 'DECIDED', trace: { rawBody: 'late' } })
  assert.equal(journal.records.length, 0)
})

test('trace whitelist freezes exact request and body and never copies credentials or private reasoning', () => {
  const raw = trace(); raw.apiKey = 'PRIVATE_KEY'; raw.reasoning_content = 'PRIVATE_REASONING'; raw.parameters.Authorization = 'PRIVATE_HEADER'; raw.stream.reasoning = 'PRIVATE_REASONING'
  const copy = snapshotRehearsalTrace(raw, request)
  raw.messages[0].content = 'changed'; raw.event.page = 'GALLERY'; raw.rawBody = 'changed'
  assert.equal(copy.messages[0].content, '业务提示'); assert.equal(copy.event.page, 'COMPANION')
  assert.equal(copy.rawBody, '{"action":"SILENT","emote":null,"text":null}')
  assert.doesNotMatch(JSON.stringify(copy), /PRIVATE_KEY|PRIVATE_REASONING|PRIVATE_HEADER|changed/)
  assert.ok(Object.isFrozen(copy.messages[0])); assert.ok(Object.isFrozen(copy.stream))
  assert.throws(() => snapshotRehearsalTrace(trace({ ...request, eventId: 'wrong' }), request), /Mismatched/)
})
test('zero body and no parsed decision survive journal while account change drops late trace', () => {
  const journal = history.createRehearsalHistory(); journal.ownerChanged('a')
  const id = journal.start({ kind: 'ARRIVAL', mode: 'BYOK', at: 1000 })
  journal.phase(id, 'SUBMITTED', request)
  journal.finish(id, { action: 'SILENT', source: 'SERVER_BYOK', reason: 'EMPTY_OUTPUT', trace: trace(request, '') })
  assert.equal(journal.records[0].trace.rawBody, ''); assert.equal(journal.records[0].trace.parsedDecision, null)
  assert.equal(journal.records[0].category, 'FAILED')
  journal.ownerChanged('b'); journal.finish(id, { source: 'SERVER_BYOK', reason: 'DECIDED', trace: trace() })
  assert.equal(journal.records.length, 0)
})
test('SITE drops supplied debug while manual transport binds it to the actual sent request', async () => {
  const phases = []
  const api = { open: async data => ({ sessionId: 's', mode: data.mode }), close: async () => {}, decide: async sent => ({ ...sent, mode: 'BYOK', action: 'SILENT', reason: 'DECIDED', debug: trace(sent) }) }
  const input = { id: 'one', kind: 'ARRIVAL', eventType: 'PAGE_ENTER', page: 'COMPANION', occurredAt: 1000 }
  const site = await createPresenceTransport(api, { mode: 'BYOK' }).decide(input)
  assert.equal(site.trace, null)
  const manual = await createPresenceTransport(api, { mode: 'BYOK', scope: 'REHEARSAL' }).decide(input, { onPhase: (...args) => phases.push(args) })
  assert.equal(manual.trace.event.observedAt, '1970-01-01T00:00:01.000Z')
  assert.equal(phases.find(([stage]) => stage === 'SUBMITTED')[1].eventId, 'one')
  api.decide = async sent => ({ ...sent, mode: 'BYOK', action: 'SILENT', reason: 'DECIDED', debug: trace({ ...sent, page: 'GALLERY' }) })
  await assert.rejects(createPresenceTransport(api, { mode: 'BYOK', scope: 'REHEARSAL' }).decide(input), /Mismatched/)
})
test('hiding SPEAK changes presentation only and retains original body and parsed decision', async () => {
  const raw = trace(); raw.rawBody = '{"action":"SPEAK","emote":null,"text":"你好"}'; raw.parsedDecision = { action: 'SPEAK', emote: null, text: '你好' }
  const session = createPresenceSession({ manualDebug: true, now: () => 1000, decide: async () => ({ action: 'SPEAK', text: '你好', source: 'SERVER_BYOK', reason: 'DECIDED', trace: snapshotRehearsalTrace(raw) }) })
  session.configure({ enabled: true, muted: true })
  await session.run({ id: 'one', kind: 'ARRIVAL', occurredAt: 1000 })
  assert.equal(session.state.result.originalAction, 'SPEAK'); assert.equal(session.state.result.text, '')
  assert.equal(session.state.result.trace.parsedDecision.text, '你好'); assert.match(session.state.result.trace.rawBody, /你好/)
})
test('expired manual response retains dispatch and trace while suppressing presentation', async () => {
  let clock = 1000, release, calls = 0
  const journal = history.createRehearsalHistory(); journal.ownerChanged('a')
  const input = { id: 'one', kind: 'ARRIVAL', occurredAt: 1000 }
  const session = createPresenceSession({ manualDebug: true, now: () => clock, decide: () => { calls++; return new Promise(resolve => { release = resolve }) } })
  session.configure({ enabled: true })
  const id = journal.start({ kind: 'ARRIVAL', mode: 'BYOK', at: 1000, input, settings: session.state })
  journal.phase(id, 'SUBMITTED', request)
  const pending = session.run(input); clock = 31001
  release({ action: 'SPEAK', text: 'late', source: 'SERVER_BYOK', reason: 'DECIDED', modelDispatched: true, attemptConsumed: true, trace: trace() })
  await pending
  journal.finish(id, session.state.result, session.state.reason)
  const row = journal.records[0]
  assert.equal(calls, 1); assert.equal(row.category, 'DECIDED'); assert.equal(row.modelDispatched, true)
  assert.ok(row.trace); assert.equal(row.text, ''); assert.equal(row.action, 'SPEAK')
  assert.equal(row.presentationSnapshot.suppression, 'EXPIRED'); assert.match(row.presentationSnapshot.label, /过期/)
})
