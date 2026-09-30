import test from 'node:test'
import assert from 'node:assert/strict'
import { createFeedingSession, FEED_RECOVERY_KEY } from '../src/presentation/companionFeeding.js'

function storage() {
  const values = new Map()
  return { getItem: key => values.get(key) ?? null, setItem: (key, value) => values.set(key, value), removeItem: key => values.delete(key) }
}
function deferred() { let resolve; const promise = new Promise(r => { resolve = r }); return { promise, resolve } }
function setup(overrides = {}) {
  let state
  let key = 0
  const saved = overrides.storage || storage()
  const session = createFeedingSession({ actor: '42', companionId: '7001', storage: saved, keyFactory: () => `request-key-${String(++key).padStart(5, '0')}`, send: async () => ({ outcome: 'GROWN' }), onChange: value => { state = value }, ...overrides })
  return { session, saved, get state() { return state } }
}

test('selection never sends; submit locks synchronously and receipt prevents implicit repeat', async () => {
  const held = deferred(); const requests = []
  const f = setup({ send: attempt => { requests.push(attempt); return held.promise } })
  assert.equal(f.session.select('11'), true)
  assert.equal(requests.length, 0)
  const pending = f.session.submit()
  assert.equal(f.state.phase, 'submitting')
  assert.equal(f.session.select('12'), false)
  assert.equal(f.session.clear(), false)
  await f.session.submit()
  assert.equal(requests.length, 1)
  assert.deepEqual(JSON.parse(f.saved.getItem(FEED_RECOVERY_KEY)), { version: 1, actor: '42', companionId: '7001', pictureId: '11', idempotencyKey: 'request-key-00001' })
  const result = { outcome: 'FAMILIARITY', growth: { lifeExperienceDelta: 0 } }
  held.resolve(result)
  assert.equal(await pending, result)
  assert.equal(f.state.phase, 'succeeded')
  assert.equal(f.state.result, result)
  assert.equal(f.saved.getItem(FEED_RECOVERY_KEY), null)
  await f.session.submit()
  assert.equal(requests.length, 1)
  assert.equal(f.session.clear(), true)
  assert.equal(f.state.phase, 'empty')
})

test('uncertain outcomes lock selection and reuse the original key across repeated failures', async () => {
  const requests = []
  const f = setup({ send: async attempt => { requests.push(attempt); if (requests.length < 3) throw Object.assign(new Error('暂未确认'), { status: 503 }); return { outcome: 'GROWN' } } })
  f.session.select('11')
  await f.session.submit()
  assert.equal(f.state.phase, 'uncertain')
  assert.equal(f.session.select('12'), false)
  await f.session.submit(); await f.session.submit()
  assert.equal(f.state.phase, 'succeeded')
  assert.equal(f.state.retried, true)
  assert.equal(new Set(requests.map(request => request.idempotencyKey)).size, 1)
})

test('definitive rejection clears recovery and a new confirmation gets a new key', async () => {
  for (const status of [400, 401, 403, 404]) {
    const requests = []
    const f = setup({ send: async attempt => { requests.push(attempt); throw Object.assign(new Error('不可使用'), { status }) } })
    f.session.select('11'); await f.session.submit()
    assert.equal(f.state.phase, 'rejected')
    assert.equal(f.saved.getItem(FEED_RECOVERY_KEY), null)
    assert.equal(f.session.select('12'), true)
    await f.session.submit()
    assert.notEqual(requests[0].idempotencyKey, requests[1].idempotencyKey)
  }
})

test('unmount preserves intent, ignores late completion, and restores without sending automatically', async () => {
  const held = deferred(); const saved = storage()
  const first = setup({ storage: saved, send: () => held.promise })
  first.session.select('11')
  const pending = first.session.submit()
  first.session.destroy()
  held.resolve({ outcome: 'GROWN' })
  assert.equal(await pending, null)
  assert.equal(first.state.phase, 'submitting')
  const requests = []
  const next = setup({ storage: saved, send: async attempt => { requests.push(attempt); return { outcome: 'GROWN' } } })
  assert.equal(next.state.phase, 'uncertain')
  assert.equal(next.state.recovered, true)
  assert.equal(next.state.pictureId, '11')
  assert.equal(requests.length, 0)
  await next.session.submit()
  assert.equal(requests[0].idempotencyKey, 'request-key-00001')
})

test('recovery validates actor, companion, IDs and key; account invalidation clears it', async () => {
  const valid = { version: 1, actor: '42', companionId: '7001', pictureId: '11', idempotencyKey: 'safe-key-12345678' }
  for (const value of ['{', ...[{ actor: '43' }, { companionId: '7002' }, { pictureId: '../11' }, { pictureId: null }, { pictureId: 11 }, { idempotencyKey: '' }, { idempotencyKey: 'short-key' }, { idempotencyKey: 'UPPERCASE-12345678' }, { idempotencyKey: 'x'.repeat(65) }, { version: 2 }].map(patch => JSON.stringify({ ...valid, ...patch }))]) {
    const saved = storage(); saved.setItem(FEED_RECOVERY_KEY, value)
    const f = setup({ storage: saved })
    assert.equal(f.state.phase, 'empty')
    assert.equal(saved.getItem(FEED_RECOVERY_KEY), null)
  }
  const f = setup({ send: async () => { throw new Error('network') } })
  f.session.select('11'); await f.session.submit()
  f.session.destroy({ clearRecovery: true })
  assert.equal(f.saved.getItem(FEED_RECOVERY_KEY), null)
  assert.equal(f.session.select('12'), false)
})

test('failed recovery read is reported even when later writes work', () => {
  const saved = { ...storage(), getItem: () => { throw new Error('read blocked') } }
  const f = setup({ storage: saved })
  assert.equal(f.state.phase, 'empty')
  assert.equal(f.state.recoveryAvailable, false)
})

test('blocked browser storage retains same-page safe retry and exposes the recovery limitation', async () => {
  const keys = []
  const fail = () => { throw new Error('storage blocked') }
  const f = setup({ storage: { getItem: fail, setItem: fail, removeItem: fail }, send: async attempt => { keys.push(attempt.idempotencyKey); throw new Error('network') } })
  f.session.select('11'); await f.session.submit(); await f.session.submit()
  assert.equal(f.state.recoveryAvailable, false)
  assert.equal(f.state.phase, 'uncertain')
  assert.equal(keys[0], keys[1])
  assert.equal(f.session.select('12'), false)
})
