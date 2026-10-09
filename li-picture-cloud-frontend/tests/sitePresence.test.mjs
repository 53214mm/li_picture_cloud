import test from 'node:test'
import assert from 'node:assert/strict'
import { createSitePresence } from '../src/presentation/sitePresence.js'
import { createPresenceTransport } from '../src/presentation/companionPresenceTransport.js'
import { sanitizePresenceEvent } from '../src/presentation/companionPresence.js'
const deferred = () => { let resolve; const promise = new Promise(done => { resolve = done }); return { promise, resolve } }
async function flush() { for (let i = 0; i < 24; i++) await Promise.resolve() }
function fixture(overrides = {}) {
  let time = 1000; let count = 0; const calls = []
  const api = {
    open: async data => { calls.push(['open', data]); return { sessionId: `s${++count}`, mode: 'DEMO' } },
    decide: async data => { calls.push(['decide', data]); return { ...data, sequence: String(data.sequence), mode: 'DEMO', action: data.eventType === 'PAGE_ENTER' ? 'SILENT' : 'EMOTE', emote: 'NOD' } },
    close: async id => { calls.push(['close', id]) }, ...overrides
  }
  const site = createSitePresence({ api, now: () => time })
  site.setOwner('42'); site.navigated('gallery')
  return { site, api, calls, advance: () => { time += 45001 } }
}
test('default off and unsupported pages never open sessions or read any content', async () => {
  const f = fixture(); await f.site.navigated('companion'); await f.site.navigated('picture-detail'); await f.site.pictureOpened(); await flush()
  assert.deepEqual(f.calls, []); assert.equal(f.site.state.input, null)
  await f.site.navigated('admin-users'); f.site.configure({ enabled: true }); await f.site.pictureOpened(); await flush()
  assert.equal(f.site.state.enabled, false); assert.deepEqual(f.calls, [])
})
test('only committed curated page and successful picture events have exact bounded wire fields', async () => {
  const f = fixture(); f.site.configure({ enabled: true }); await f.site.navigated('companion')
  const page = f.calls.find(([name]) => name === 'decide')[1]
  assert.deepEqual(Object.keys(page).sort(), ['eventId','eventType','observedAt','page','sequence','sessionId'].sort())
  assert.equal(page.page, 'COMPANION'); assert.equal(page.eventType, 'PAGE_ENTER'); assert.equal(f.site.state.result.action, 'SILENT')
  f.advance(); f.site.navigationStarted('picture-detail'); await f.site.navigated('picture-detail'); assert.equal(f.site.state.result, null)
  assert.equal(await f.site.pictureOpened(), true); assert.equal(f.site.state.input.kind, 'PICTURE_VIEW'); assert.equal(f.site.state.result.text, '点点头')
  f.advance(); assert.equal(await f.site.pictureOpened(), false); assert.equal(f.site.state.used, 2)
  assert.ok(f.site.state.input.id.length <= 64)
})
test('route navigation, settings and unknown-page suspension retain budget and cooldown', async () => {
  const f = fixture(); f.site.configure({ enabled: true, limit: 2 }); await f.site.navigated('companion')
  await f.site.navigated('gallery'); assert.equal(f.site.state.used, 1); assert.match(f.site.state.reason, /冷却/)
  f.site.configure({ muted: true }); await f.site.navigated('admin-users'); assert.equal(f.site.state.visible, false)
  f.advance(); await f.site.navigated('gallery'); assert.equal(f.site.state.used, 2)
  f.advance(); await f.site.navigated('companion'); assert.equal(f.site.state.used, 2); assert.equal(f.site.state.result, null)
})
for (const cause of ['hidden', 'chat/modal', 'intent', 'owner', 'logout', 'dispose', 'newer']) {
  test(`${cause} invalidates in-flight output without replay`, async () => {
    const pending = deferred(); let sent
    const f = fixture({ decide: data => { sent = data; return pending.promise } })
    f.site.configure({ enabled: true }); const running = f.site.navigated('companion'); await flush(); assert.ok(sent)
    if (cause === 'hidden') f.site.suppress({ isHidden: true })
    if (cause === 'chat/modal') f.site.suppress({ isBlocked: true })
    if (cause === 'intent') f.site.navigationStarted('gallery')
    if (cause === 'owner') f.site.setOwner('43')
    if (cause === 'logout') f.site.logout()
    if (cause === 'dispose') f.site.dispose()
    if (cause === 'newer') await f.site.navigated('gallery')
    pending.resolve({ ...sent, sequence: '1', mode: 'DEMO', action: 'SPEAK', text: 'late' }); await running
    assert.equal(f.site.state.result, null)
    f.site.suppress({ isHidden: false, isBlocked: false }); await flush(); assert.equal(f.site.state.result, null)
  })
}
test('canceled navigation clears stale result but does not replay current page', async () => {
  const f = fixture(); f.site.configure({ enabled: true }); await f.site.navigated('companion')
  f.advance(); f.site.navigationStarted('gallery'); await f.site.navigated('companion', { failed: true })
  assert.equal(f.site.state.used, 1); assert.equal(f.site.state.result, null); assert.equal(f.site.state.visible, true)
})
test('rehearsal intent turns off live coordinator and drains close before another adapter opens', async () => {
  const opening = deferred(); const calls = []; let count = 0
  const api = { open: async () => { calls.push('open'); return ++count === 1 ? opening.promise : { sessionId: 'new', mode: 'DEMO' } }, close: async id => { calls.push(`close:${id}`) }, decide: async data => { calls.push('decide'); return { ...data, mode: 'DEMO', action: 'SILENT' } } }
  const site = createSitePresence({ api }); site.setOwner('1'); site.navigated('gallery'); site.configure({ enabled: true }); const old = site.navigated('companion'); await flush()
  site.navigationStarted('companion-rehearsal'); assert.equal(site.state.enabled, false)
  const rehearsal = createPresenceTransport(api)
  const next = rehearsal.decide(sanitizePresenceEvent({ id: 'rehearsal-1', kind: 'GALLERY', occurredAt: Date.now() }))
  await flush(); assert.deepEqual(calls, ['open'])
  opening.resolve({ sessionId: 'old', mode: 'DEMO' }); await old; await next
  assert.deepEqual(calls, ['open', 'close:old', 'open', 'decide'])
})
test('event IDs enforce server 64-character bound', () => {
  assert.ok(sanitizePresenceEvent({ id: 'a'.repeat(64), kind: 'GALLERY', occurredAt: 1 }, 1))
  assert.equal(sanitizePresenceEvent({ id: 'a'.repeat(65), kind: 'GALLERY', occurredAt: 1 }, 1), null)
})
test('new owner can report its independently authorized picture without previous-owner dedupe', async () => {
  const f = fixture(); await f.site.navigated('picture-detail'); await f.site.pictureOpened()
  f.site.setOwner('other'); f.site.configure({ enabled: true })
  assert.equal(await f.site.pictureOpened(), true); assert.equal(f.site.state.used, 1)
})
