import test from 'node:test'
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import { URL } from 'node:url'
import { mapCompanionPresentation } from '../src/presentation/companionPresentation.js'
import { mapCompanionAnimation, createCompanionAnimator } from '../src/presentation/companionAnimation.js'

const mood = { energy: 0, joy: 0, loneliness: 0, inspiration: 0, irritation: 0 }
const relationship = { familiarity: 0, trust: 0, closeness: 0, tacit: 0, recentFeedback: 0 }
const traits = { curiosity: 0, enthusiasm: 0, playfulness: 0, empathy: 0, creativity: 0 }
const home = { companion: { id: '7', lifeStage: 'LIGHT', traits }, mood, relationship }
const map = (override = {}, flags = {}) => mapCompanionPresentation({ homeStatus: 'ready', home: { ...home, ...override }, ...flags })

function clock() {
  const pending = new Map()
  let sequence = 0
  let value
  const animator = createCompanionAnimator({
    onChange: next => { value = next },
    schedule(callback, duration) { const id = ++sequence; pending.set(id, { callback, duration }); return id },
    cancel(id) { pending.delete(id) }
  })
  return { animator, pending, get value() { return value }, get timer() { return [...pending.values()][0] },
    tick() { assert.equal(pending.size, 1); const [id, timer] = [...pending][0]; pending.delete(id); timer.callback() } }
}

test('R11 zero values are observed neutrality, distinct from missing or malformed snapshots', () => {
  const known = map().disposition
  assert.equal(known.mood.status, 'known')
  assert.equal(known.mood.label, '平静相伴')
  assert.equal(known.mood.idleTone, 'neutral')
  assert.equal(known.traits.label, '倾向平衡')
  assert.equal(known.traits.axes.length, 5)
  for (const field of ['mood', 'relationship']) {
    for (const [raw, status] of [[null, 'missing'], [undefined, 'missing'], [{}, 'invalid'], [[], 'invalid'], [true, 'invalid'], ['0', 'invalid']]) {
      const view = map({ [field]: raw }).disposition[field]
      assert.equal(view.status, status)
      assert.deepEqual(view.axes, [])
      assert.notEqual(view.label, known[field].label)
    }
  }
})

test('R11 rejects every malformed axis as a complete snapshot, but accepts explicit numeric strings', () => {
  for (const [field, base, keys, min] of [
    ['mood', mood, Object.keys(mood), 0], ['relationship', relationship, Object.keys(relationship), 0], ['traits', traits, Object.keys(traits), -100]
  ]) for (const key of keys) {
    const signed = min === -100 || key === 'recentFeedback'
    for (const bad of [null, undefined, '', ' ', true, false, 'NaN', NaN, Infinity, [], {}, signed ? -100.01 : -0.01, 100.01]) {
      const raw = { ...base, [key]: bad }
      const view = map(field === 'traits' ? { companion: { ...home.companion, traits: raw } } : { [field]: raw }).disposition[field]
      assert.equal(view.status, 'invalid', `${field}.${key} ${String(bad)}`)
      assert.equal(view.axes.length, 0)
    }
    const raw = { ...base, [key]: signed ? '-100.00' : '100.00' }
    const view = map(field === 'traits' ? { companion: { ...home.companion, traits: raw } } : { [field]: raw }).disposition[field]
    assert.equal(view.status, 'known')
    assert.equal(view.axes.find(axis => axis.key === key).value, signed ? -100 : 100)
  }
})

test('R11 stale, unavailable and account-cleared signals cannot leak previous state or trait values', () => {
  for (const flags of [{ homeFresh: false }, { homeStatus: 'loading' }, { enabled: false }, { homeStatus: 'error' }, { homeStatus: 'unobserved' }]) {
    const p = map({ mood: { ...mood, joy: 100 }, companion: { ...home.companion, traits: { ...traits, empathy: 90 } } }, flags)
    for (const view of Object.values(p.disposition)) {
      assert.equal(view.status, flags.homeFresh === false ? 'stale' : 'unavailable')
      assert.equal(view.axes.length, 0)
    }
    assert.equal(p.disposition.mood.idleTone, 'neutral')
    assert.equal(mapCompanionAnimation(p).idleTone, 'neutral')
  }
})

test('R11 high-mood idle choices use existing affect ordering and never infer sleep or a proposal', () => {
  for (const [axis, tone] of [['energy', 'energetic'], ['joy', 'cheerful'], ['loneliness', 'lonely'], ['inspiration', 'inspired'], ['irritation', 'irritated']]) {
    for (const value of [0, 5, 59.99, 60, 100]) {
      const p = map({ mood: { ...mood, [axis]: value, summary: '睡觉、庆祝、发起聊天' } })
      assert.equal(p.disposition.mood.idleTone, value >= 60 ? tone : 'neutral')
      assert.equal(mapCompanionAnimation(p).state, 'idle')
      assert.equal(p.attention, 'none')
    }
  }
  assert.equal(map({ mood: { energy: 70, joy: 70, loneliness: 70, inspiration: 70, irritation: 70 } }).disposition.mood.idleTone, 'energetic')
})

test('R11 relationship keeps R06 rules and signed feedback is centered without becoming irritation', () => {
  const p = map({ relationship: { ...relationship, trust: 60, closeness: 60, recentFeedback: -100 } })
  assert.equal(p.rapport, 'close')
  assert.equal(p.affect, 'neutral')
  assert.equal(p.disposition.relationship.label, '相处渐近')
  assert.equal(p.disposition.relationship.axes.find(axis => axis.key === 'recentFeedback').position, 0)
  assert.equal(map().disposition.relationship.axes.find(axis => axis.key === 'recentFeedback').position, 50)
  assert.equal(map({ relationship: { ...relationship, recentFeedback: 100 } }).disposition.relationship.axes.find(axis => axis.key === 'recentFeedback').position, 100)
})

test('R11 personality keeps existing 10/60 bands, bipolar position and stable tie order', () => {
  const view = map({ companion: { ...home.companion, traits: { curiosity: -100, enthusiasm: 100, playfulness: 9.99, empathy: -10, creativity: 59.99 } } }).disposition.traits
  assert.equal(view.label, '明显偏谨慎 · 明显偏热情')
  assert.deepEqual(view.axes.map(axis => axis.position), [0, 100, 54.995, 45, 79.995])
  assert.deepEqual(view.axes.map(axis => axis.label), ['明显偏谨慎', '明显偏热情', '保持中性', '略偏理性', '略偏创造'])
  assert.equal(map({ companion: { ...home.companion, traits: { ...traits, creativity: 60 } } }).disposition.traits.label, '明显偏创造')
})

test('R11 is deeply frozen, deterministic and does not mutate or retain domain text', () => {
  const raw = { mood: { ...mood, joy: 90, summary: 'private text' }, companion: { ...home.companion, traits: { ...traits, curiosity: -60 } } }
  const before = JSON.stringify(raw)
  const p = map(raw)
  assert.equal(JSON.stringify(raw), before)
  assert.deepEqual(p, map(raw))
  assert.equal(JSON.stringify(p).includes('private text'), false)
  assert.ok(Object.isFrozen(p.disposition))
  for (const view of Object.values(p.disposition)) {
    assert.ok(Object.isFrozen(view))
    assert.ok(Object.isFrozen(view.axes))
    for (const axis of view.axes) assert.ok(Object.isFrozen(axis))
  }
})

test('R11 idle changes keep one timer, invalidate old callbacks and neutralize immediately when stale', () => {
  const c = clock()
  const energetic = map({ mood: { ...mood, energy: 80 } })
  c.animator.update(energetic, true)
  assert.equal(c.timer.duration, 2800)
  const first = c.timer
  c.animator.update(map({ mood: { ...mood, energy: 90 } }), true)
  assert.equal(c.timer, first)
  c.tick()
  assert.equal(c.value.offsetY, -2)
  const late = c.timer.callback
  c.animator.update(map({}, { homeFresh: false }), true)
  assert.equal(c.timer.duration, 4200)
  assert.equal(c.value.offsetY, 0)
  late()
  assert.equal(c.pending.size, 1)
  c.animator.update(energetic, false)
  assert.equal(c.value.state, 'static')
  assert.equal(c.pending.size, 0)
  c.animator.update(energetic, true)
  assert.equal(c.value.offsetY, 0)
  c.animator.destroy()
  assert.equal(c.pending.size, 0)
})

test('R11 mood updates cannot reset focus or replay proposal and direct-interaction cues', () => {
  const c = clock()
  const current = (axis, flags = {}) => map({ mood: { ...mood, [axis]: 90 } }, flags)
  c.animator.update(current('joy', { chat: { phase: 'waiting' } }), true)
  const timer = c.timer
  c.animator.update(current('energy', { chat: { phase: 'streaming' } }), true)
  assert.equal(c.timer, timer)
  c.animator.update(current('energy', { proposal: { status: 'PENDING' } }), true)
  for (let i = 0; i < 4; i++) c.tick()
  assert.equal(c.value.state, 'idle')
  assert.equal(c.timer.duration, 2800)
  c.animator.update(current('joy', { proposal: { status: 'PENDING' } }), true)
  assert.equal(c.value.state, 'idle')
  assert.equal(c.timer.duration, 3600)
  c.animator.update(current('energy'), true, 1)
  assert.equal(c.value.state, 'greeting')
  c.animator.update(current('inspiration'), true, 1)
  for (let i = 0; i < 3; i++) c.tick()
  assert.equal(c.value.state, 'idle')
  assert.equal(c.timer.duration, 4000)
  c.animator.destroy()
})

// The added visible state must not move figcaption away from a figure edge.
test('R11 body keeps a valid trailing caption and no live region for passive mood', async () => {
  const source = await readFile(new URL('../src/components/companion/body/CompanionBody.vue', import.meta.url), 'utf8')
  assert.match(source, /<p v-if="showDisposition"[^>]*>[^]*?<\/p>\s*<figcaption>/)
  assert.match(source, /<\/figcaption>\s*<\/figure>/)
  assert.doesNotMatch(source, /aria-live|role="(?:status|alert)"/)
})
