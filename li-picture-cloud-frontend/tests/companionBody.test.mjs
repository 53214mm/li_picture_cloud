import test from 'node:test'
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import { createHash } from 'node:crypto'
import { resolveCompanionVisual } from '../src/components/companion/body/companionVisual.js'
import { createSpriteClock } from '../src/components/companion/body/spriteClock.js'

test('shipped artwork matches the measured manifest and stays within transfer/decode budgets', async () => {
  const root = new globalThis.URL('../src/assets/companion/lingye/', import.meta.url)
  const manifest = JSON.parse(await readFile(new globalThis.URL('manifest.json', root), 'utf8'))
  let total = 0
  for (const asset of manifest.assets) {
    const data = await readFile(new globalThis.URL(asset.filename, root))
    assert.equal(data.length, asset.bytes)
    assert.equal(createHash('sha256').update(data).digest('hex'), asset.sha256)
    assert.ok(data.length <= asset.budgetKiB * 1024)
    total += data.length
  }
  assert.equal(total, manifest.totalBytes)
  assert.ok(total <= 856 * 1024)
  assert.ok(manifest.totalDecodedBytes <= 10 * 1024 * 1024)
  const home = manifest.assets.find(asset => asset.filename === manifest.fallback)
  const idle = manifest.assets.find(asset => asset.filename.includes('-idle-'))
  assert.equal(idle.width, home.width * manifest.frameDurationsMs.length)
  assert.equal(idle.height, home.height)
})

test('every existing stage receives the same adult representation without translating business stage', () => {
  for (const currentStage of ['LIGHT', 'SEEDLING', 'COMPANION', 'FUTURE_STAGE', null, undefined]) {
    const visual = resolveCompanionVisual(currentStage)
    assert.equal(visual.currentStage, currentStage)
    assert.equal(visual.visualStage, 'adult')
    assert.equal(visual.assetKey, 'lingye-adult-v1')
    assert.equal(visual.allowIdle, ['LIGHT', 'SEEDLING', 'COMPANION'].includes(currentStage))
  }
})

function clockFixture() {
  const pending = new Map()
  const frames = []
  let sequence = 0
  const clock = createSpriteClock({
    durations: [4200, 140],
    onFrame: frame => frames.push(frame),
    schedule: (callback, duration) => {
      const id = ++sequence
      pending.set(id, { callback, duration })
      return id
    },
    cancel: id => pending.delete(id)
  })
  function tick() {
    const [id, timer] = pending.entries().next().value
    pending.delete(id)
    timer.callback()
  }
  return { clock, pending, frames, tick }
}

test('idle schedules only one next frame with its own duration', () => {
  const f = clockFixture()
  assert.equal(f.pending.size, 0)
  f.clock.setPlaying(true)
  f.clock.setPlaying(true)
  assert.equal(f.pending.size, 1)
  assert.equal([...f.pending.values()][0].duration, 4200)
  f.tick()
  assert.equal(f.frames.at(-1), 1)
  assert.equal([...f.pending.values()][0].duration, 140)
  f.tick()
  assert.equal(f.frames.at(-1), 0)
  assert.equal(f.pending.size, 1)
})

test('pausing returns to the neutral frame, cancels work and resumes without catch-up', () => {
  const f = clockFixture()
  f.clock.setPlaying(true)
  f.tick()
  f.clock.setPlaying(false)
  assert.equal(f.frames.at(-1), 0)
  assert.equal(f.pending.size, 0)
  f.clock.setPlaying(false)
  f.clock.setPlaying(true)
  assert.equal([...f.pending.values()][0].duration, 4200)
  assert.equal(f.pending.size, 1)
})

test('unmount is final and a late queued callback cannot schedule again', () => {
  const f = clockFixture()
  f.clock.setPlaying(true)
  const lateCallback = [...f.pending.values()][0].callback
  f.clock.destroy()
  f.clock.destroy()
  lateCallback()
  f.clock.setPlaying(true)
  assert.equal(f.pending.size, 0)
  assert.equal(f.frames.at(-1), 0)
})

test('an obsolete callback from before a pause cannot advance a resumed cycle', () => {
  const f = clockFixture()
  f.clock.setPlaying(true)
  const stale = [...f.pending.values()][0].callback
  f.clock.setPlaying(false)
  f.clock.setPlaying(true)
  stale()
  assert.equal(f.pending.size, 1)
  assert.equal(f.frames.at(-1), 0)
})
