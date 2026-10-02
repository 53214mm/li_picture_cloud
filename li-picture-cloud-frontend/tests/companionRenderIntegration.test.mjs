import test from 'node:test'
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import { mapCompanionRenderPolicy } from '../src/presentation/companionRenderPolicy.js'
import { mapCompanionPresentation } from '../src/presentation/companionPresentation.js'
import { createCompanionAnimator } from '../src/presentation/companionAnimation.js'

const presentation = mapCompanionPresentation({ homeStatus: 'ready', home: { companion: { id: '7', lifeStage: 'LIGHT' } }, proposal: { status: 'PENDING' } })
function clock() {
  const pending = new Map()
  let current, sequence = 0
  const animator = createCompanionAnimator({
    onChange: value => { current = value },
    schedule(callback, ms) { const id = ++sequence; pending.set(id, { callback, ms }); return id },
    cancel(id) { pending.delete(id) }
  })
  return { animator, pending, get current() { return current }, tick() {
    assert.equal(pending.size, 1)
    const [id, timer] = [...pending][0]; pending.delete(id); timer.callback()
  } }
}

test('R13 downgrade stops the same clock and never renews a consumed proposal quota', () => {
  const c = clock()
  const update = viewportWidth => c.animator.update(presentation, mapCompanionRenderPolicy({ viewportWidth }).allowMotion)
  update(390)
  assert.equal(c.pending.size, 0)
  update(1024)
  assert.equal(c.current.state, 'attention')
  const late = [...c.pending.values()][0].callback
  update(767)
  assert.equal(c.current.state, 'static')
  assert.equal(c.pending.size, 0)
  update(768)
  assert.equal(c.current.state, 'idle')
  late()
  assert.equal(c.pending.size, 1)
  for (const width of [320, 1440, 390, 820]) update(width)
  assert.equal(c.current.state, 'idle')
  c.animator.destroy()
})

test('R13 never overrides user pause or an existing visibility gate', () => {
  const c = clock()
  for (const policy of [mapCompanionRenderPolicy({ viewportWidth: 1440 }), mapCompanionRenderPolicy({ viewportWidth: 1440, saveData: true }), mapCompanionRenderPolicy({ viewportWidth: 320 })]) {
    for (const [userPaused, visible] of [[true, true], [false, false], [true, false]]) {
      c.animator.update(presentation, policy.allowMotion && !userPaused && visible)
      assert.equal(c.pending.size, 0)
    }
  }
  c.animator.update({ ...presentation, attention: 'none' }, true)
  assert.equal(c.current.state, 'idle')
  c.animator.destroy()
})

test('R13 keeps the player keyed only to the companion and routes downgrade through pause', async () => {
  const source = await readFile(new globalThis.URL('../src/components/companion/body/CompanionBody.vue', import.meta.url), 'utf8')
  assert.match(source, /:paused="paused \|\| userPaused \|\| !renderPolicy\.allowMotion"/)
  assert.match(source, /:key="presentation\.companionId"/)
  assert.doesNotMatch(source, /<SpritePlayer[^>]+v-if="[^"\n]*(?:renderPolicy|compact)/)
  assert.match(source, /renderPolicy\.allowMotion[^]*!availability\.reducedMotion/)
  assert.match(source, /\.body-ground \{ width: 96px; max-width: 100%; \}/)
  assert.match(source, /@click="interaction\.open\(\)"/)
})

test('R13 mobile room flows naturally and keeps photo and interaction actions out of fixed overlays', async () => {
  const room = await readFile(new globalThis.URL('../src/components/companion/CompanionHabitat.vue', import.meta.url), 'utf8')
  const mobile = room.slice(room.indexOf('@media (width < 768px)'))
  assert.match(mobile, /\.habitat-scene \{ min-height: 0; display: grid;/)
  for (const selector of ['room-heading', 'room-status', 'room-invitation', 'photo-nook']) assert.match(mobile, new RegExp(`\\.${selector} \\{ position: static;`))
  assert.doesNotMatch(mobile, /min-height: 680px|position: fixed/)
  assert.match(room, /aria-label="照片留位，前往照片桌"/)
  assert.match(room, /emit\('visit', 'chat'\)/)
  assert.match(room, /emit\('visit', 'feed'\)/)
  const shell = await readFile(new globalThis.URL('../src/layouts/AppLayout.vue', import.meta.url), 'utf8')
  assert.match(shell, /\.sidebar \{ display: none; \}/)
  assert.match(shell, /safe-area-inset-bottom/)
  assert.match(shell, /min-height: 44px/)
})
