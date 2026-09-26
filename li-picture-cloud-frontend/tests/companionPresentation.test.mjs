import test from 'node:test'
import assert from 'node:assert/strict'
import { mapCompanionPresentation } from '../src/presentation/companionPresentation.js'
import { createPresentationChannel } from '../src/presentation/companionPresentationChannel.js'

const mood = { energy: 0, joy: 0, loneliness: 0, inspiration: 0, irritation: 0 }
const relationship = { familiarity: 0, trust: 0, closeness: 0, tacit: 0, recentFeedback: 0 }
const home = { companion: { id: '7001', lifeStage: 'LIGHT', revision: '9' }, mood, relationship }
const ready = overrides => ({ homeStatus: 'ready', home, ...overrides })
const map = overrides => mapCompanionPresentation(ready(overrides))

test('null or malformed optional signals degrade safely instead of throwing', () => {
  for (const input of [undefined, null, false, 0, 'unknown']) {
    assert.equal(mapCompanionPresentation(input).availability, 'unobserved')
  }
  for (const value of [null, undefined, false, 'unknown']) {
    const state = map({ feed: value, chat: value, proposal: value })
    assert.equal(state.activity, 'idle')
    assert.equal(state.attention, 'none')
  }
})

test('availability gates identity and all activity, emotion and attention', () => {
  for (const [homeStatus, availability] of [['unobserved', 'unobserved'], ['loading', 'loading'], ['error', 'error'], ['unavailable', 'unavailable']]) {
    const state = map({ homeStatus, feed: { pending: true }, chat: { phase: 'streaming' }, proposal: { status: 'PENDING' } })
    assert.equal(state.availability, availability)
    assert.equal(state.companionId, null)
    assert.equal(state.activity, 'idle')
    assert.equal(state.affect, 'neutral')
    assert.equal(state.attention, 'none')
    assert.equal(state.appearance.allowIdle, false)
  }
  assert.equal(map({ enabled: false }).availability, 'disabled')
  assert.equal(map({ home: { companion: null } }).availability, 'absent')
  for (const bad of [null, {}, { companion: {} }, { companion: { id: NaN } }]) {
    assert.equal(map({ home: bad }).availability, 'error')
  }
})

test('R05 keeps exact business stage and identity, adult visual and static unknown stage', () => {
  for (const stage of ['LIGHT', 'SEEDLING', 'COMPANION', 'FUTURE', null]) {
    const state = map({ home: { ...home, companion: { ...home.companion, lifeStage: stage } } })
    assert.equal(state.version, 1)
    assert.equal(state.companionId, '7001')
    assert.equal(state.lifeStage, stage)
    assert.equal(state.appearance.assetKey, 'lingye-adult-v1')
    assert.equal(state.appearance.visualStage, 'adult')
    assert.equal(state.appearance.allowIdle, ['LIGHT', 'SEEDLING', 'COMPANION'].includes(stage))
  }
})

test('activity priority is deterministic across every combination, independent from affect and attention', () => {
  for (const phase of ['idle', 'waiting', 'streaming']) for (const pending of [true, false]) for (const busy of [true, false]) {
    const state = map({ chat: { phase }, feed: { pending }, proposal: { status: 'PENDING', busy } })
    const expected = phase === 'streaming' ? 'responding' : phase === 'waiting' ? 'thinking' : pending ? 'feeding' : busy ? 'acknowledging' : 'idle'
    assert.equal(state.activity, expected)
    assert.equal(state.attention, 'proposal')
    assert.equal(state.affect, 'neutral')
  }
})

test('complete finite mood uses server threshold and tie ordering; zero energy is neutral', () => {
  for (const [axis, affect] of [['energy', 'energetic'], ['joy', 'cheerful'], ['loneliness', 'lonely'], ['inspiration', 'inspired'], ['irritation', 'irritated']]) {
    for (const value of [4.99, 5, 100, '5.00']) {
      assert.equal(map({ home: { ...home, mood: { ...mood, [axis]: value } } }).affect, Number(value) >= 5 ? affect : 'neutral')
    }
  }
  assert.equal(map({ home: { ...home, mood: { energy: 8, joy: 8, loneliness: 8, inspiration: 8, irritation: 8 } } }).affect, 'energetic')
  assert.equal(map().affect, 'neutral')
  for (const value of [null, undefined, '', ' ', true, NaN, Infinity, -1, 101, 'angry']) {
    assert.equal(map({ home: { ...home, mood: { ...mood, joy: 100, energy: value } } }).affect, 'neutral')
  }
  assert.equal(map({ home: { ...home, mood: { joy: 100 } } }).affect, 'neutral')
})

test('relationship is a separate presentation band, never an emotion or business level', () => {
  const withRelationship = values => map({ home: { ...home, relationship: { ...relationship, ...values } } })
  assert.equal(withRelationship({ familiarity: 19.99 }).rapport, 'neutral')
  assert.equal(withRelationship({ familiarity: 20 }).rapport, 'familiar')
  assert.equal(withRelationship({ trust: 60, closeness: 60, recentFeedback: -100 }).rapport, 'close')
  assert.equal(withRelationship({ trust: 60, closeness: 59.99 }).rapport, 'neutral')
  assert.equal(withRelationship({ familiarity: 100, trust: 101 }).rapport, 'neutral')
  assert.equal(withRelationship({ recentFeedback: -100 }).affect, 'neutral')
  assert.equal(map({ home: { ...home, relationship: null } }).rapport, 'neutral')
})

test('proposal must be currently observed PENDING, not loading, failed, terminal, unknown or inferred from text', () => {
  for (const status of [null, 'DONE', 'IGNORED', 'SUPPRESSED', 'EXPIRED', 'FUTURE']) assert.equal(map({ proposal: { status } }).attention, 'none')
  for (const flags of [{ loading: true }, { error: true }]) assert.equal(map({ proposal: { status: 'PENDING', ...flags } }).attention, 'none')
  assert.equal(map({ proposal: { status: 'PENDING' } }).attention, 'proposal')
})

test('operation failures do not become anger or mask another pending operation', () => {
  const state = map({ feed: { error: true }, chat: { phase: 'waiting', error: true }, proposal: { error: true } })
  assert.equal(state.activity, 'thinking')
  assert.equal(state.affect, 'neutral')
  assert.deepEqual(state.issues, ['feed-failed', 'chat-failed', 'proposal-failed'])
  assert.equal(map({ feed: { outcome: 'GROWN' } }).activity, 'idle')
})

test('stale feed-era mood and rapport degrade without discarding identity or request activity', () => {
  const state = map({ homeFresh: false, home: { ...home, mood: { ...mood, joy: 100 }, relationship: { ...relationship, familiarity: 90 } }, feed: { pending: true } })
  assert.equal(state.freshness, 'stale')
  assert.equal(state.affect, 'neutral')
  assert.equal(state.rapport, 'neutral')
  assert.equal(state.activity, 'feeding')
  assert.equal(state.companionId, '7001')
})

test('mapper is pure, stable, immutable and ignores text, errors, trait and unsupported species hints', () => {
  const input = ready({ home: { ...home, mood: { ...mood, summary: '非常愤怒' }, species: 'imagined' }, chat: { text: '我生气了', errorMessage: 'sad' }, proposal: { content: '开心' } })
  const before = JSON.stringify(input)
  const first = mapCompanionPresentation(input)
  assert.deepEqual(first, mapCompanionPresentation(input))
  assert.equal(JSON.stringify(input), before)
  assert.equal(first.affect, 'neutral')
  assert.ok(Object.isFrozen(first))
  assert.ok(Object.isFrozen(first.appearance))
  assert.ok(Object.isFrozen(first.issues))
  assert.equal(JSON.stringify(first).includes('非常愤怒'), false)
})

test('source lease rejects old pages, closed pages and pre-reset async callbacks', () => {
  const snapshots = []
  const channel = createPresentationChannel(snapshot => snapshots.push(snapshot))
  const old = channel.acquire()
  old.publish(ready())
  const current = channel.acquire()
  old.publish(ready({ feed: { pending: true } }))
  old.close()
  assert.equal(snapshots.at(-1).homeStatus, 'unobserved')
  current.publish(ready({ chat: { phase: 'waiting' } }))
  assert.equal(snapshots.at(-1).chat.phase, 'waiting')
  current.close()
  current.publish(ready())
  assert.equal(snapshots.at(-1).homeStatus, 'unobserved')
  const account = channel.acquire()
  channel.reset()
  account.publish(ready())
  assert.equal(snapshots.at(-1).homeStatus, 'unobserved')
})
