import test from 'node:test'
import assert from 'node:assert/strict'
import { createCompanionObserver } from '../src/presentation/companionObserver.js'

function fixture({ expiresAt, deferredControl = false } = {}) {
  let clock = 0, serial = 0, reply, controlReply, controlHandler
  const timers = new Map(), calls = { open: 0, close: 0, prepare: 0, observe: 0, controls: [] }
  const api = {
    status: async () => ({ enabled: true, textReceiver: { token: 'fixture' } }),
    open: async data => { calls.open++; return { sessionId: 'same-grant', textRemaining: data.textLimit, visionRemaining: 0, expiresAt } },
    close: async () => { calls.close++ },
    control: async (id, data) => {
      calls.controls.push({ id, ...data })
      if (controlHandler) return controlHandler(data)
      if (deferredControl) await new Promise(resolve => { controlReply = resolve })
      return { sequence: data.sequence, suspended: data.suspended, busy: false, textRemaining: 9, visionRemaining: 0, expiresAt }
    },
    prepare: async () => { calls.prepare++; return { snapshotId: `snapshot-${calls.prepare}` } },
    observe: async data => { calls.observe++; return reply ? reply(data) : { ...data, parsed: { action: 'SPEAK', text: 'current' }, modelDispatched: true, textRemaining: 8, visionRemaining: 0, nextObservationSeconds: 90 } }
  }
  const observer = createCompanionObserver({ api, collect: () => ({ page: 'GALLERY', pictureIds: ['11'], rects: [] }), now: () => clock,
    setTimer: (fn, ms) => { const id = ++serial; timers.set(id, { fn, at: clock + ms }); return id }, clearTimer: id => timers.delete(id) })
  observer.setOwner('42')
  const settle = async () => { for (let i = 0; i < 40; i++) await Promise.resolve() }
  return { observer, api, calls, settle, respond(fn) { reply = fn }, control(fn) { controlHandler = fn }, releaseControl() { controlReply?.() }, async advance(ms) {
    clock += ms; for (const [id, timer] of [...timers]) if (timer.at <= clock) { timers.delete(id); timer.fn() }; await settle()
  } }
}
const options = { textLimit: 10, visionLimit: 0, intensity: 'NORMAL', auto: true }

test('a delayed manual status refresh cannot roll back the current UTC-day count', async () => {
  const f = fixture(); await f.observer.start(options); let release
  f.api.status = () => new Promise(resolve => { release = () => resolve({ enabled: true, textReceiver: { token: 'fixture' }, counts: { text: 0, vision: 0, usageDate: '1970-01-01' } }) })
  const refresh = f.observer.refreshStatus()
  f.respond(async data => ({ ...data, modelDispatched: true, parsed: { action: 'SILENT' }, textRemaining: 7, visionRemaining: 0, daily: { text: 1, vision: 0, usageDate: '1970-01-01' } }))
  await f.observer.observeNow(); release(); await refresh
  assert.equal(f.observer.state().status.counts.text, 1); assert.equal(f.observer.state().textRemaining, 7)
})

test('an old busy acknowledgement cannot override a bound completion or trigger overlapping observation', async () => {
  const f = fixture(); let releaseTurn, releaseAck
  f.respond(data => new Promise(resolve => { releaseTurn = () => resolve({ ...data, reason: 'OBSERVATION_SUSPENDED', modelDispatched: true, parsed: null, textRemaining: 7, visionRemaining: 0, nextObservationSeconds: 300, daily: { text: 1, vision: 0, usageDate: '1970-01-01' } }) }))
  f.control(data => data.suspended ? { sequence: data.sequence, suspended: true, busy: true, inFlightSequence: 1, textRemaining: 9, visionRemaining: 0 } : new Promise(resolve => {
    releaseAck = () => resolve({ sequence: data.sequence, suspended: false, busy: true, inFlightSequence: 1, textRemaining: 9, visionRemaining: 0, daily: { text: 0, vision: 0, usageDate: '1970-01-01' } })
  }))
  await f.observer.start(options); await f.advance(90000); f.observer.suppress(true); await f.settle(); f.observer.suppress(false); await f.settle()
  await f.observer.observeNow(); assert.equal(f.calls.observe, 1)
  releaseTurn(); await f.settle(); releaseAck(); await f.settle(); f.respond(null)
  assert.equal(f.observer.state().status.counts.text, 1)
  await f.advance(299999); assert.equal(f.calls.observe, 1); await f.advance(1); assert.equal(f.calls.observe, 2); assert.equal(f.calls.open, 1); assert.equal(f.calls.close, 0)
})

test('UTC date on a fresh control count permits midnight reset without refilling grant balance', async () => {
  const f = fixture(); await f.observer.start(options)
  f.respond(async data => ({ ...data, modelDispatched: true, parsed: { action: 'SILENT' }, textRemaining: 7, visionRemaining: 0, daily: { text: 3, vision: 0, usageDate: '1970-01-01' } }))
  await f.observer.observeNow()
  f.control(data => ({ ...data, busy: false, textRemaining: 9, visionRemaining: 0, daily: { text: 0, vision: 0, usageDate: '1970-01-02' } }))
  f.observer.suppress(true); await f.settle()
  assert.equal(f.observer.state().status.counts.text, 0); assert.equal(f.observer.state().textRemaining, 7)
  f.control(data => ({ ...data, busy: false, textRemaining: 9, visionRemaining: 0, daily: { text: 5, vision: 0, usageDate: '1970-01-01' } }))
  f.observer.suppress(false); await f.settle()
  assert.equal(f.observer.state().status.counts.text, 0)
})

test('hard stop clears the old prepare stage before a fresh explicit opening is hidden', async () => {
  const f = fixture()
  f.api.prepare = (_data, { signal }) => new Promise((_resolve, reject) => signal.addEventListener('abort', () => reject(new Error('canceled')), { once: true }))
  await f.observer.start(options); await f.advance(90000); f.observer.pause()
  let releaseStatus
  f.api.status = ({ signal }) => new Promise((resolve, reject) => {
    releaseStatus = () => resolve({ enabled: true, textReceiver: { token: 'fixture' } })
    signal.addEventListener('abort', () => reject(new Error('canceled')), { once: true })
  })
  const opening = f.observer.start(options); f.observer.suppress(true); releaseStatus(); await opening; await f.settle()
  assert.equal(f.observer.state().enabled, true); assert.equal(f.observer.state().suppressed, true)
  assert.equal(f.calls.open, 2); assert.equal(f.calls.close, 1); assert.equal(f.calls.observe, 0)
})

test('exhausted balance returned by control ends the grant instead of restoring or refilling it', async () => {
  const f = fixture(); f.control(data => ({ sequence: data.sequence, suspended: data.suspended, busy: false, textRemaining: data.suspended ? 9 : 0, visionRemaining: 0 }))
  await f.observer.start(options); f.observer.suppress(true); await f.settle(); f.observer.suppress(false); await f.settle(); await f.advance(900000)
  assert.equal(f.observer.state().enabled, false); assert.equal(f.observer.state().textRemaining, 0); assert.equal(f.calls.open, 1); assert.equal(f.calls.observe, 0)
})

test('an already submitted explicit opening accepts its late same-owner grant in suspended state without preparing', async () => {
  const f = fixture(); let release
  f.api.open = async data => { f.calls.open++; return new Promise(resolve => { release = () => resolve({ sessionId: 'same-grant', textRemaining: data.textLimit, visionRemaining: 0 }) }) }
  const opening = f.observer.start(options); await f.settle(); f.observer.suppress(true)
  release(); await opening; await f.settle()
  assert.equal(f.observer.state().enabled, true); assert.equal(f.observer.state().suppressed, true); assert.equal(f.calls.close, 0); assert.equal(f.calls.prepare, 0)
  f.observer.suppress(false); await f.settle(); await f.advance(89999); assert.equal(f.calls.prepare, 0); await f.advance(1); assert.equal(f.calls.observe, 1); assert.equal(f.calls.open, 1)
})

test('our pause aborts pending prepare without turning cancellation into a terminal preparation failure', async () => {
  const f = fixture()
  f.api.prepare = (_data, { signal }) => new Promise((_resolve, reject) => signal.addEventListener('abort', () => reject(new Error('canceled')), { once: true }))
  await f.observer.start(options); await f.advance(90000); f.observer.suppress(true); await f.settle()
  assert.equal(f.observer.state().enabled, true); assert.equal(f.calls.close, 0); assert.equal(f.calls.observe, 0)
  f.api.prepare = async () => ({ snapshotId: 'fresh' }); f.observer.suppress(false); await f.settle()
  await f.advance(89999); assert.equal(f.calls.observe, 0); await f.advance(1); assert.equal(f.calls.observe, 1)
})

test('hidden or ordinary chat pauses the original grant; returning resumes on cadence without reopen', async () => {
  const f = fixture(); await f.observer.start(options); await f.advance(45000)
  f.observer.suppress(true); await f.settle()
  assert.equal(f.observer.state().enabled, true); assert.equal(f.calls.close, 0)
  await f.advance(900000); assert.equal(f.calls.prepare, 0); assert.equal(f.calls.observe, 0)
  f.observer.suppress(false); await f.settle()
  assert.deepEqual(f.calls.controls, [{ id: 'same-grant', sequence: 1, suspended: true }, { id: 'same-grant', sequence: 2, suspended: false }])
  assert.equal(f.observer.state().textRemaining, 9); assert.equal(f.calls.open, 1)
  await f.advance(89999); assert.equal(f.calls.observe, 0); await f.advance(1); assert.equal(f.calls.observe, 1)
})

test('late paused turn reconciles incurred counts but cannot display speech or overlap a fresh turn', async () => {
  const f = fixture(); let release
  f.respond(data => new Promise(resolve => { release = () => resolve({ ...data, reason: 'OBSERVATION_SUSPENDED', parsed: null, modelDispatched: true, textRemaining: 7, visionRemaining: 0, nextObservationSeconds: 300 }) }))
  await f.observer.start(options); await f.advance(90000); f.observer.suppress(true); await f.settle()
  f.observer.suppress(false); await f.settle(); await f.advance(900000)
  assert.equal(f.calls.observe, 1); release(); await f.settle()
  assert.equal(f.observer.state().enabled, true); assert.equal(f.observer.state().textRemaining, 7)
  assert.equal(f.observer.state().presentation, null); assert.equal(f.observer.state().speeches.length, 0)
  f.respond(null); await f.advance(299999); assert.equal(f.calls.observe, 1); await f.advance(1); assert.equal(f.calls.observe, 2)
  assert.equal(f.calls.open, 1); assert.equal(f.calls.close, 0)
})

test('manual end, owner change and expiry while hidden never resume or open a new grant', async () => {
  for (const boundary of ['manual', 'owner', 'expiry']) {
    const f = fixture({ expiresAt: boundary === 'expiry' ? new Date(90000).toISOString() : undefined })
    await f.observer.start(options); f.observer.suppress(true); await f.settle()
    if (boundary === 'manual') f.observer.pause(); if (boundary === 'owner') f.observer.setOwner('43')
    await f.advance(90000); f.observer.suppress(false); await f.settle(); await f.advance(900000)
    assert.equal(f.observer.state().enabled, false); assert.equal(f.calls.open, 1); assert.equal(f.calls.observe, 0)
  }
})

test('resume does not collect or dispatch until the control acknowledgement is current', async () => {
  const f = fixture({ deferredControl: true }); await f.observer.start(options)
  f.observer.suppress(true); await f.settle(); f.observer.suppress(false); await f.settle()
  await f.advance(900000); assert.equal(f.calls.prepare, 0); f.releaseControl(); await f.settle()
  await f.advance(900000); assert.equal(f.calls.prepare, 0); f.releaseControl(); await f.settle()
  await f.advance(89999); assert.equal(f.calls.prepare, 0); await f.advance(1); assert.equal(f.calls.observe, 1)
})
