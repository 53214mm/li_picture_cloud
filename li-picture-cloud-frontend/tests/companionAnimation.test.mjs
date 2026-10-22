import test from 'node:test'
import assert from 'node:assert/strict'
import { mapCompanionPresentation } from '../src/presentation/companionPresentation.js'
import { mapCompanionAnimation, createCompanionAnimator } from '../src/presentation/companionAnimation.js'

// Retain legacy controller lifecycle coverage using an explicit synthetic motion capability.
// Production projections disable this atlas; the static-identity suite verifies that boundary.
const legacyMotion = p => ({ ...p, appearance: { ...p.appearance, allowIdle: true } })
function presentation(overrides = {}) {
  return { ...legacyMotion(mapCompanionPresentation({ homeStatus: 'ready', home: { companion: { id: '7', lifeStage: 'LIGHT' } } })), ...overrides }
}
function fixture() {
  const pending = new Map()
  const changes = []
  let sequence = 0
  const animator = createCompanionAnimator({
    onChange: value => changes.push(value),
    schedule(callback, duration) { const id = ++sequence; pending.set(id, { callback, duration }); return id },
    cancel(id) { pending.delete(id) }
  })
  return {
    animator, pending, changes,
    update(value = presentation(), playable = true) { animator.update(value, playable) },
    get current() { return changes.at(-1) },
    tick() {
      assert.equal(pending.size, 1)
      const [id, timer] = pending.entries().next().value
      pending.delete(id)
      timer.callback()
    }
  }
}

test('animation consumes all R06 conflict combinations without reordering its winner', () => {
  for (const phase of ['idle', 'waiting', 'streaming']) for (const pending of [false, true]) {
    const p = mapCompanionPresentation({ homeStatus: 'ready', home: { companion: { id: '7', lifeStage: 'LIGHT' } }, chat: { phase }, feed: { pending } })
    const intent = mapCompanionAnimation(legacyMotion(p))
    assert.equal(intent.state, p.activity === 'idle' ? 'idle' : 'focus')
    assert.equal(intent.companionId, '7')
    assert.ok(Object.isFrozen(intent))
  }
})

test('unsupported or unavailable inputs are static; text and affect cannot invent clips', () => {
  for (const p of [null, undefined, {}, presentation({ version: 2 }), presentation({ companionId: null }), presentation({ activity: 'speaking' }), presentation({ appearance: {} }), presentation({ appearance: { assetKey: 'other', visualStage: 'adult', allowIdle: true } })]) {
    assert.equal(mapCompanionAnimation(p).state, 'static')
  }
  for (const availability of ['disabled', 'unobserved', 'loading', 'unavailable', 'error', 'absent']) {
    assert.equal(mapCompanionAnimation(presentation({ availability, activity: 'responding' })).state, 'static')
  }
  for (const affect of ['neutral', 'energetic', 'cheerful', 'lonely', 'inspired', 'irritated']) {
    assert.equal(mapCompanionAnimation(presentation({ affect, rapport: 'close', freshness: 'stale', issues: ['feed-failed'], content: '生气 睡觉 庆祝' })).state, 'idle')
  }
})

test('idle cadence is unchanged, repeated snapshots do not restart or add timers', () => {
  const f = fixture()
  f.update()
  const first = [...f.pending.values()][0]
  f.update(presentation({ affect: 'inspired' }))
  assert.equal([...f.pending.values()][0], first)
  assert.equal(first.duration, 4200)
  f.tick()
  assert.equal(f.current.frame, 1)
  assert.equal([...f.pending.values()][0].duration, 140)
  f.tick()
  assert.equal(f.current.frame, 0)
})

test('focus interrupts idle immediately and request handoffs keep one cycle', () => {
  const f = fixture()
  f.update(presentation())
  const late = [...f.pending.values()][0].callback
  f.update(presentation({ activity: 'thinking' }))
  assert.equal(f.current.state, 'focus')
  const first = [...f.pending.values()][0]
  for (const activity of ['responding', 'feeding']) f.update(presentation({ activity }))
  late()
  assert.equal([...f.pending.values()][0], first)
  assert.equal(f.pending.size, 1)
  f.update(presentation())
  assert.equal(f.current.state, 'idle')
})

test('suspension discards in-flight hints and resumes the current loop from neutral', () => {
  const f = fixture()
  f.update(presentation())
  f.tick()
  f.update(presentation(), false)
  assert.deepEqual(f.current, { state: 'static', frame: 0, offsetY: 0, playing: false })
  assert.equal(f.pending.size, 0)
  f.update(presentation({ activity: 'feeding' }))
  assert.equal(f.current.state, 'focus')
  assert.equal(f.current.frame, 0)
  assert.equal(f.current.offsetY, 0)
  f.tick()
  assert.equal(f.current.offsetY, 2)
  f.update(presentation())
  assert.equal(f.current.state, 'idle')
})

test('unavailability, identity change, pause and destroy invalidate late callbacks', () => {
  const f = fixture()
  f.update()
  const late = [...f.pending.values()][0].callback
  f.update(presentation({ activity: 'feeding' }))
  f.update(presentation({ availability: 'error' }))
  assert.equal(f.pending.size, 0)
  f.update(presentation({ companionId: '8' }))
  late()
  assert.equal(f.pending.size, 1)
  const resumed = [...f.pending.values()][0].callback
  f.update(presentation({ companionId: '8' }), false)
  f.update(presentation({ companionId: '8' }))
  resumed()
  assert.equal(f.pending.size, 1)
  f.animator.destroy()
  const count = f.changes.length
  f.animator.destroy()
  f.update()
  resumed()
  late()
  assert.equal(f.pending.size, 0)
  assert.equal(f.changes.length, count)
})

test('direct interaction is a finite R07 cue, subordinate to facts and never queued', () => {
  const f = fixture()
  f.animator.update(presentation(), true, 1)
  assert.equal(f.current.state, 'greeting')
  for (let i = 0; i < 3; i++) f.tick()
  assert.equal(f.current.state, 'idle')
  f.animator.update(presentation(), true, 1)
  assert.equal(f.current.state, 'idle')
  f.animator.update(presentation({ activity: 'feeding' }), true, 2)
  assert.equal(f.current.state, 'focus')
  f.animator.update(presentation(), true, 2)
  assert.equal(f.current.state, 'idle')
  f.animator.update(presentation(), false, 3)
  f.animator.update(presentation(), true, 3)
  assert.equal(f.current.state, 'idle')
  f.animator.update(presentation(), true, 5)
  assert.equal(f.current.state, 'greeting')
  f.animator.update(presentation({ activity: 'thinking' }), true, 5)
  assert.equal(f.current.state, 'focus')
  f.animator.update(presentation(), true, 5)
  assert.equal(f.current.state, 'idle')
})
