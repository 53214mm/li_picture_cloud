import test from 'node:test'
import assert from 'node:assert/strict'
import { captureObservationRegion } from '../src/presentation/realObservationCapture.js'
import { createCompanionObserver } from '../src/presentation/companionObserver.js'

test('capture rejects external URLs, non-PNG and oversized input before any browser API', async () => {
  let created = 0
  const options = { documentRef: { createElement() { created++; throw new Error('unexpected') } }, ImageCtor: class { constructor() { created++; throw new Error('unexpected') } } }
  for (const value of ['https://evil.invalid/screen.png', 'data:image/svg+xml;base64,AAAA', 'data:image/png;base64,' + 'a'.repeat(1048576)]) await assert.rejects(captureObservationRegion(value, options), /INVALID_CAPTURE_SOURCE/)
  assert.equal(created, 0)
})

test('off and text-only observation never capture pixels or request vision', async () => {
  let captures = 0, observations = 0
  const api = { status: async () => ({ enabled: true, textReceiver: { token: 'text' } }), open: async () => ({ sessionId: 'session', textRemaining: 2, visionRemaining: 0 }),
    prepare: async () => ({ snapshotId: 'snapshot', visualSkip: 'VISION_OFF' }), observe: async input => { observations++; assert.equal(input.capturePixelDigest, undefined); return { ...input, reason: null, parsed: { action: 'SILENT' }, textRemaining: 1, visionRemaining: 0 } }, close: async () => {} }
  const observer = createCompanionObserver({ api, capture: async () => { captures++; throw new Error('unexpected') } })
  observer.setOwner(1); await observer.observeNow(); assert.equal(observations, 0)
  await observer.start({ textLimit: 2, visionLimit: 0, intensity: 'LOW', auto: false }); await observer.observeNow()
  assert.equal(captures, 0); assert.equal(observations, 1); observer.dispose()
})

test('failed browser capture makes zero model dispatch and late capture cannot revive closed grant', async () => {
  let observations = 0, complete
  const api = { status: async () => ({ enabled: true, textReceiver: { token: 'text' }, visionReceiver: { token: 'vision' } }), open: async () => ({ sessionId: 'session', textRemaining: 2, visionRemaining: 2 }),
    prepare: async () => ({ snapshotId: 'snapshot', preview: 'data:image/png;base64,AAAA' }), observe: async () => { observations++; throw new Error('unexpected') }, close: async () => {} }
  const observer = createCompanionObserver({ api, capture: () => new Promise(resolve => { complete = resolve }) })
  observer.setOwner(1); await observer.start({ textLimit: 2, visionLimit: 2, intensity: 'LOW', auto: false }); const pending = observer.observeNow()
  for (let i = 0; i < 10; i++) await Promise.resolve()
  observer.pause(); complete({ png: 'data:image/png;base64,AAAA', pixelDigest: 'mock' }); await pending
  assert.equal(observations, 0); assert.equal(observer.state().preview, null); observer.dispose()
  const failing = createCompanionObserver({ api, capture: async () => { throw new Error('CAPTURE_UNAVAILABLE') } }); failing.setOwner(1)
  await failing.start({ textLimit: 2, visionLimit: 2, intensity: 'LOW', auto: false }); await failing.observeNow(); assert.equal(observations, 0); failing.dispose()
})

test('withdrawing visual consent while preview is pending prevents even canvas capture', async () => {
  let resolve, captures = 0
  const observer = createCompanionObserver({ api: { preview: () => new Promise(done => { resolve = done }) }, collect: () => ({ page: 'GALLERY', pictureIds: ['101'], rects: [{ x: 1, y: 1, width: 100, height: 100 }] }), capture: async () => { captures++; return { png: 'data:image/png;base64,AAAA', pixelDigest: 'fixture' } } })
  observer.setOwner(1); const pending = observer.previewOnly()
  observer.withdrawVisualConsent(); resolve({ preview: 'data:image/png;base64,AAAA' }); await pending
  assert.equal(captures, 0); assert.equal(observer.state().preview, null); observer.dispose()
})
