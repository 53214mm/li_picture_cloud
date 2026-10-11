import test from 'node:test'
import assert from 'node:assert/strict'
import { collectSafeLayout } from '../src/presentation/safeVisualLayout.js'
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
