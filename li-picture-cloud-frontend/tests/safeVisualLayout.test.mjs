import test from 'node:test'
import assert from 'node:assert/strict'
import { collectSafeLayout } from '../src/presentation/safeVisualLayout.js'
import { createCompanionObserver } from '../src/presentation/companionObserver.js'

for (const stage of ['prepare', 'preview']) test(`a new invalid visible ID invalidates pending ${stage} without exposing its value`, async () => {
  const card = id => ({ dataset: { observationPicture: id, observationVersion: 'v1' }, checkVisibility: () => true, closest: () => null, getBoundingClientRect: () => ({ left: 0, top: 0, right: 100, bottom: 100, width: 100, height: 100 }) })
  let nodes = [card('101')], release, observes = 0, captures = 0
  const collect = visual => collectSafeLayout('gallery', visual, { querySelectorAll: () => nodes }, { innerWidth: 640, innerHeight: 360 })
  const before = collect(true)
  const api = { status: async () => ({ enabled: true, textReceiver: { token: 'text' }, visionReceiver: { token: 'vision' } }),
    open: async () => ({ sessionId: 'session', textRemaining: 2, visionRemaining: 2 }), close: async () => {},
    prepare: async () => new Promise(resolve => { release = resolve }), preview: async () => new Promise(resolve => { release = resolve }),
    observe: async data => { observes++; return { ...data, modelDispatched: true, textRemaining: 1, visionRemaining: 1, parsed: { action: 'SILENT' } } } }
  const observer = createCompanionObserver({ api, collect, setTimer: () => 1, clearTimer: () => {}, capture: async png => { captures++; return { png, pixelDigest: 'fake' } } })
  observer.setOwner(1)
  if (stage === 'prepare') await observer.start({ textLimit: 2, visionLimit: 2, intensity: 'LOW', auto: false })
  const pending = stage === 'prepare' ? observer.observeNow() : observer.previewOnly()
  nodes = [...nodes, card('private-invalid-id')]; const after = collect(true); observer.update(after)
  release({ snapshotId: 'snapshot', preview: 'data:image/png;base64,AAAA' }); await pending
  assert.notEqual(before.dependency, after.dependency); assert.equal(after.dependency.includes('private-invalid-id'), false)
  assert.equal(captures, 0); assert.equal(observes, 0); assert.equal(observer.state().preview, null); observer.dispose()
})

test('two visible anchors for one authorized picture produce one visual rectangle and one exact ID', () => {
  const card = version => ({ dataset: { observationPicture: '11', observationVersion: version }, checkVisibility: () => true, closest: () => null, getBoundingClientRect: () => ({ left: 0, top: 0, right: 100, bottom: 100, width: 100, height: 100 }) })
  const result = collectSafeLayout('picture-detail', true, { querySelectorAll: () => [card('old'), card('current')] }, { innerWidth: 640, innerHeight: 360 })
  assert.deepEqual(result.pictureIds, ['11']); assert.deepEqual(result.rects, [{ x: 0, y: 0, width: 100, height: 100 }])
})

test('hidden pages never inspect even the approved DOM whitelist', () => {
  assert.deepEqual(collectSafeLayout('gallery', true, { hidden: true, querySelectorAll() { assert.fail('background collection') } }, {}), { page: 'GALLERY', pictureIds: [], rects: [], dependency: '' })
})
test('sensitive/unknown pages never inspect DOM or capture pixels', () => {
  const doc = { querySelectorAll() { throw new Error('must not inspect sensitive page') } }
  for (const page of ['model-gateway', 'login', 'payment', 'admin-users']) assert.deepEqual(collectSafeLayout(page, true, doc, {}), { page: 'UNKNOWN', pictureIds: [], rects: [], dependency: '' })
})
test('visible snowflake refs stay exact strings; visual off has no preview geometry; text/photos are never read', () => {
  const node = { dataset: { observationPicture: '2109132400221044738', observationVersion: '2026-10-11' }, get textContent() { throw new Error('private text read') }, getBoundingClientRect: () => ({ left: 10, top: 20, right: 110, bottom: 100, width: 100, height: 80 }), closest: () => null }
  const doc = { querySelectorAll: () => [node] }; const window = { innerWidth: 640, innerHeight: 360, getComputedStyle: () => ({ display: 'block', visibility: 'visible', opacity: '1' }) }
  const off = collectSafeLayout('gallery', false, doc, window); assert.deepEqual(off.pictureIds, ['2109132400221044738']); assert.deepEqual(off.rects, [])
  const on = collectSafeLayout('gallery', true, doc, window); assert.deepEqual(on.rects, [{ x: 10, y: 20, width: 100, height: 80 }]); assert.equal(Object.hasOwn(on, 'textContent'), false)
})

test('CSS-hidden images with a visible-size rectangle and hidden ancestors are excluded', () => {
  const hidden = { dataset: { observationPicture: '101' }, closest: () => null, getBoundingClientRect: () => ({ left: 0, top: 0, right: 100, bottom: 100, width: 100, height: 100 }), checkVisibility: () => false }
  const doc = { querySelectorAll: () => [hidden] }, win = { innerWidth: 640, innerHeight: 360 }
  for (const vision of [false, true]) assert.deepEqual(collectSafeLayout('gallery', vision, doc, win).pictureIds, [])
})
