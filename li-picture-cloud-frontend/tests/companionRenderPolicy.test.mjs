import test from 'node:test'
import assert from 'node:assert/strict'
import { createRenderer, isShallow } from 'vue'
import { mapCompanionRenderPolicy } from '../src/presentation/companionRenderPolicy.js'
import { createCompanionRenderPolicyObserver, useCompanionRenderPolicy } from '../src/composables/useCompanionRenderPolicy.js'

function eventSurface(signals = {}) {
  const listeners = new Map()
  const registered = []
  const removed = []
  return Object.assign(signals, {
    registered,
    removed,
    addEventListener(type, callback) {
      if (!listeners.has(type)) listeners.set(type, new Set())
      listeners.get(type).add(callback)
      registered.push({ type, callback })
    },
    removeEventListener(type, callback) {
      listeners.get(type)?.delete(callback)
      removed.push({ type, callback })
    },
    dispatch(type) { for (const callback of listeners.get(type) ?? []) callback() },
    count(type) { return listeners.get(type)?.size ?? 0 }
  })
}

function fixture({ width = 1024, connection = eventSurface({ saveData: false, effectiveType: '4g' }) } = {}) {
  const viewport = eventSurface({ innerWidth: width })
  const changes = []
  const environment = { window: viewport, navigator: connection === null ? {} : { connection } }
  const observer = createCompanionRenderPolicyObserver({ ...environment, onChange: value => changes.push(value) })
  return { viewport, connection, changes, observer, environment, get current() { return changes.at(-1) } }
}

test('rendering budget uses the 768px breakpoint and returns an immutable policy', () => {
  for (const width of [1, 320, 767, 767.5, 768, 1024, 1440]) {
    const input = Object.freeze({ viewportWidth: width, saveData: false, effectiveType: '4g' })
    const policy = mapCompanionRenderPolicy(input)
    const compact = width < 768
    assert.deepEqual(policy, {
      compact, allowMotion: !compact, reason: compact ? 'compact' : 'full', label: compact ? '轻量显示' : '常规显示'
    })
    assert.ok(Object.isFrozen(policy))
  }
})

test('unmeasured and malformed widths conservatively use compact static geometry', () => {
  for (const viewportWidth of [undefined, null, 0, -1, NaN, Infinity, -Infinity, '', '768', true, false, [], {}, 768n, Symbol('width')]) {
    assert.deepEqual(mapCompanionRenderPolicy({ viewportWidth, saveData: true, effectiveType: '2g' }), {
      compact: true, allowMotion: false, reason: 'unmeasured', label: '静态立绘'
    })
  }
  for (const input of [undefined, null, [], true, false, '', '1024', 1024, Symbol('signals')]) {
    assert.equal(mapCompanionRenderPolicy(input).reason, 'unmeasured')
  }
})

test('explicit Save-Data and slow connection restrictions apply even on wide screens', () => {
  for (const viewportWidth of [768, 1024]) {
    assert.deepEqual(mapCompanionRenderPolicy({ viewportWidth, saveData: true }), {
      compact: false, allowMotion: false, reason: 'save-data', label: '节省流量'
    })
    for (const effectiveType of ['slow-2g', '2g']) {
      assert.deepEqual(mapCompanionRenderPolicy({ viewportWidth, effectiveType }), {
        compact: false, allowMotion: false, reason: 'slow-connection', label: '静态立绘'
      })
      assert.equal(mapCompanionRenderPolicy({ viewportWidth, saveData: true, effectiveType }).reason, 'save-data')
    }
  }
  assert.equal(mapCompanionRenderPolicy({ viewportWidth: 767, saveData: true, effectiveType: '2g' }).reason, 'compact')
})

test('absent or malformed network fields never coerce into a restriction', () => {
  for (const saveData of [undefined, null, false, 0, 1, '', 'true', [], {}]) {
    for (const effectiveType of [undefined, null, 0, true, '', '3g', '4g', 'wifi', '2G', ' 2g ', [], {}]) {
      const policy = mapCompanionRenderPolicy({ viewportWidth: 1024, saveData, effectiveType })
      assert.equal(policy.reason, 'full')
      assert.equal(policy.allowMotion, true)
      assert.equal(policy.compact, false)
    }
  }
})

test('budget ignores domain state, reduced-motion and other player-owned visibility inputs', () => {
  assert.deepEqual(mapCompanionRenderPolicy({
    viewportWidth: 1024,
    activity: 'responding', mood: { energy: 100 }, lifeStage: 'LIGHT',
    reducedMotion: true, paused: true, foreground: false, intersecting: false, unobstructed: false
  }), mapCompanionRenderPolicy({ viewportWidth: 1024 }))
})

test('observer measures immediately on start without duplicate listeners or unchanged publications', () => {
  const f = fixture()
  assert.equal(f.changes.length, 0)
  assert.equal(f.viewport.count('resize'), 0)
  f.observer.start()
  assert.equal(f.current.reason, 'full')
  assert.equal(f.viewport.count('resize'), 1)
  assert.equal(f.connection.count('change'), 1)
  f.observer.start()
  f.observer.start()
  f.viewport.dispatch('resize')
  f.connection.dispatch('change')
  assert.equal(f.viewport.registered.length, 1)
  assert.equal(f.connection.registered.length, 1)
  assert.equal(f.changes.length, 1)
  f.viewport.innerWidth = 1200
  f.viewport.dispatch('resize')
  assert.equal(f.changes.length, 1)
  f.observer.stop()
})

test('resize and connection events re-evaluate the current measured policy', () => {
  const f = fixture()
  f.observer.start()
  f.viewport.innerWidth = 767
  f.viewport.dispatch('resize')
  assert.equal(f.current.reason, 'compact')
  f.viewport.innerWidth = 768
  f.viewport.dispatch('resize')
  assert.equal(f.current.reason, 'full')
  f.connection.saveData = true
  f.connection.dispatch('change')
  assert.equal(f.current.reason, 'save-data')
  f.connection.saveData = false
  f.connection.effectiveType = 'slow-2g'
  f.connection.dispatch('change')
  assert.equal(f.current.reason, 'slow-connection')
  f.connection.effectiveType = '4g'
  f.connection.dispatch('change')
  assert.equal(f.current.reason, 'full')
  f.viewport.innerWidth = NaN
  f.viewport.dispatch('resize')
  assert.equal(f.current.reason, 'unmeasured')
  f.observer.stop()
})

test('absent browser or network capability is safe and still allows known-width observation', () => {
  for (const connection of [null, undefined, {}, { effectiveType: '2g' }, { saveData: true }]) {
    const f = fixture({ width: 1024, connection: null })
    if (connection !== null) f.environment.navigator.connection = connection
    const changes = []
    const observer = createCompanionRenderPolicyObserver({ ...f.environment, onChange: value => changes.push(value) })
    observer.start()
    assert.equal(changes.at(-1).reason, connection?.saveData === true ? 'save-data' : connection?.effectiveType === '2g' ? 'slow-connection' : 'full')
    f.viewport.innerWidth = 320
    f.viewport.dispatch('resize')
    assert.equal(changes.at(-1).reason, 'compact')
    observer.stop()
    assert.equal(f.viewport.count('resize'), 0)
  }
  const changes = []
  const observer = createCompanionRenderPolicyObserver({ onChange: value => changes.push(value) })
  observer.start()
  assert.equal(changes[0].reason, 'unmeasured')
  observer.stop()
})

test('unsupported listener methods use read-once measurements and do not register without cleanup', () => {
  for (const eventAPI of [
    {},
    { addEventListener: false, removeEventListener: true },
    { addEventListener() { assert.fail('must not subscribe without removeEventListener') } },
    { addEventListener() { throw new Error('unsupported') }, removeEventListener() {} }
  ]) {
    const changes = []
    const observer = createCompanionRenderPolicyObserver({
      window: { innerWidth: 1024, ...eventAPI },
      navigator: { connection: { saveData: true, ...eventAPI } },
      onChange: value => changes.push(value)
    })
    assert.doesNotThrow(() => observer.start())
    assert.equal(changes[0].reason, 'save-data')
    assert.doesNotThrow(() => observer.stop())
  }
})

test('unavailable browser signal getters degrade safely without a network assumption', () => {
  const changes = []
  const observer = createCompanionRenderPolicyObserver({
    window: { get innerWidth() { throw new Error('unavailable') } },
    navigator: { get connection() { throw new Error('unavailable') } },
    onChange: value => changes.push(value)
  })
  assert.doesNotThrow(() => observer.start())
  assert.equal(changes[0].reason, 'unmeasured')
  observer.stop()
})

test('terminal teardown removes exact listeners and rejects late events and restart attempts', () => {
  const f = fixture()
  f.observer.start()
  const lateResize = f.viewport.registered[0].callback
  const lateConnection = f.connection.registered[0].callback
  f.observer.stop()
  f.observer.stop()
  assert.equal(f.viewport.count('resize'), 0)
  assert.equal(f.connection.count('change'), 0)
  assert.deepEqual(f.viewport.removed, f.viewport.registered)
  assert.deepEqual(f.connection.removed, f.connection.registered)
  f.viewport.innerWidth = 320
  f.connection.saveData = true
  lateResize()
  lateConnection()
  f.observer.start()
  assert.equal(f.changes.length, 1)
  assert.equal(f.viewport.registered.length, 1)
  assert.equal(f.connection.registered.length, 1)
  const neverStarted = fixture()
  neverStarted.observer.stop()
  neverStarted.observer.start()
  assert.equal(neverStarted.changes.length, 0)
  assert.equal(neverStarted.viewport.registered.length, 0)
})

test('the Vue composable begins unmeasured, starts at mount, and cleans up at unmount', () => {
  const f = fixture({ width: 767 })
  let policy
  const renderer = createRenderer({
    createComment: text => ({ text }),
    insert() {}, remove() {}, parentNode() {}, nextSibling() {}
  })
  const app = renderer.createApp({
    setup() {
      policy = useCompanionRenderPolicy(f.environment).policy
      assert.ok(isShallow(policy))
      assert.equal(policy.value.reason, 'unmeasured')
      assert.equal(f.viewport.registered.length, 0)
      return () => null
    }
  })
  app.mount({})
  assert.equal(policy.value.reason, 'compact')
  assert.equal(f.viewport.count('resize'), 1)
  f.viewport.innerWidth = 1024
  f.viewport.dispatch('resize')
  assert.equal(policy.value.reason, 'full')
  app.unmount()
  assert.equal(f.viewport.count('resize'), 0)
  assert.equal(f.connection.count('change'), 0)
  f.connection.saveData = true
  f.connection.registered[0].callback()
  assert.equal(policy.value.reason, 'full')
})
