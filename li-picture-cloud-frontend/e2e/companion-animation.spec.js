import { test, expect } from '@playwright/test'

const specimen = { id: '7001', lifeStage: 'LIGHT', lifeExperience: '42', level: 1, levelStartExperience: '0', nextLevelExperience: '100', revision: '9', traits: {}, skills: [] }
const proposal = { id: '7010', status: 'PENDING', content: '生气、庆祝、说话都不是动作信号', opportunityType: 'WEEKLY_REVIEW', impulseScore: 2 }
const ok = (route, data) => route.fulfill({ json: { code: 0, data } })
function gate() { let release; const promise = new Promise(resolve => { release = resolve }); return { promise, release } }

async function fixture(page, attention = false) {
  const errors = []
  const images = []
  const calls = []
  page.on('pageerror', error => errors.push(error.message))
  page.on('request', request => {
    if (request.resourceType() === 'image') images.push(request.url())
    if (new URL(request.url()).pathname.startsWith('/api/')) calls.push(request.url())
  })
  await page.route(url => url.pathname.startsWith('/api/'), route => {
    const path = new URL(route.request().url()).pathname
    if (path === '/api/user/current') return ok(route, { id: '42', userName: 'R07', userRole: 'user' })
    if (path === '/api/companion/me') return ok(route, { companion: specimen, mood: null, relationship: null, recentGrowth: [], nutrition: {}, chatPolicy: 'DEMO' })
    if (path === '/api/companion/proposals/active') return ok(route, attention ? proposal : null)
    if (path === '/api/companion/contract') return ok(route, { active: true, quietStart: '23:00', quietEnd: '08:00', maxFrequencyHours: 1 })
    if (path === '/api/space/list/page/vo') return ok(route, { records: [{ id: '51', userId: '42', spaceType: 0, spaceName: '私有空间' }] })
    if (path === '/api/picture/list/page/vo') return ok(route, { records: [{ id: '101', name: 'R07 图片', pictureUrl: '/favicon.svg' }] })
    if (path.includes('tag_category')) return ok(route, { tagList: [], categoryList: [] })
    return ok(route, { records: [], total: 0 })
  })
  await page.addInitScript(() => {
    const original = globalThis.fetch.bind(globalThis)
    globalThis.fetch = (url, options) => {
      if (url !== '/api/companion/chat/stream') return original(url, options)
      return new Promise(resolve => {
        globalThis.r07Open = () => resolve(new Response(new ReadableStream({ start(controller) { globalThis.r07Stream = controller } }), { headers: { 'Content-Type': 'text/event-stream' } }))
      })
    }
  })
  return { errors, images, calls }
}
async function loaded(page) {
  await page.goto('/companion')
  const player = page.locator('.sprite-player')
  await expect(player.locator('.sprite-still')).toHaveJSProperty('naturalWidth', 576)
  return player
}
async function intent(page, value) {
  for (const selector of ['.companion-body', '.companion-portrait', '.sidebar .companion-presence']) {
    await expect(page.locator(selector)).toHaveAttribute('data-animation-intent', value)
  }
  for (const selector of ['.companion-portrait', '.sidebar .companion-presence']) {
    await expect(page.locator(selector)).toHaveAttribute('data-animation-state', 'static')
  }
}
async function chat(page) {
  await page.getByLabel('对伙伴说的话').fill('这段文本不影响动画')
  await page.locator('.sprite-player').scrollIntoViewIfNeeded()
  // Submit the real Vue form while keeping the Home renderer visible.
  await page.locator('.chat-input').evaluate(form => form.requestSubmit())
  await expect(page.locator('.companion-body')).toHaveAttribute('data-presentation-activity', 'thinking')
}
async function chunk(page, value) {
  await page.evaluate(text => globalThis.r07Stream.enqueue(new TextEncoder().encode(text)), value)
}
async function hidden(page, value) {
  await page.evaluate(isHidden => {
    Object.defineProperty(globalThis.document, 'hidden', { configurable: true, value: isHidden })
    globalThis.document.dispatchEvent(new Event('visibilitychange'))
  }, value)
}

test('a finite proposal cue plays actual atlas frames once, then idle, with static secondary surfaces', async ({ page }, testInfo) => {
  const state = await fixture(page, true)
  await page.clock.install()
  await page.clock.pauseAt(new Date())
  const player = await loaded(page)
  await intent(page, 'attention')
  await expect(player).toHaveAttribute('data-animation-state', 'attention')
  await page.clock.runFor(600)
  await expect(player).toHaveAttribute('data-frame', '1')
  await expect(player.locator('.sprite-atlas')).toHaveCSS('visibility', 'visible')
  await page.screenshot({ path: testInfo.outputPath('attention-desktop.png') })
  await page.clock.runFor(140)
  await expect(player).toHaveAttribute('data-frame', '0')
  await page.clock.runFor(180)
  await expect(player).toHaveAttribute('data-frame', '1')
  await page.clock.runFor(140)
  await expect(player).toHaveAttribute('data-animation-state', 'idle')
  await page.getByRole('button', { name: '暂停动作' }).click()
  await page.getByRole('button', { name: '恢复动作' }).click()
  await expect(player).toHaveAttribute('data-animation-state', 'idle')
  await page.clock.runFor(4200)
  await expect(player).toHaveAttribute('data-frame', '1')
  expect(state.calls.filter(url => url.endsWith('/companion/me'))).toHaveLength(1)
  expect(state.calls.filter(url => url.endsWith('/proposals/active'))).toHaveLength(1)
  expect(state.errors).toEqual([])
})

test('focus renders a small pose and blink; streaming handoff preserves the cycle', async ({ page }, testInfo) => {
  const state = await fixture(page)
  await page.clock.install()
  await page.clock.pauseAt(new Date())
  const player = await loaded(page)
  await expect(player).toHaveAttribute('data-playback', 'playing')
  await chat(page)
  await intent(page, 'focus')
  await expect(player).toHaveAttribute('data-animation-state', 'focus')
  await page.clock.runFor(800)
  await page.evaluate(() => globalThis.r07Open())
  await chunk(page, 'data:生气 庆祝 睡觉\n\n')
  await expect(page.locator('.companion-body')).toHaveAttribute('data-presentation-activity', 'responding')
  await page.clock.runFor(800)
  await expect(player).toHaveAttribute('data-animation-offset', '2')
  await expect(player.locator('.sprite-atlas')).toHaveCSS('transform', 'matrix(1, 0, 0, 1, 0, 2)')
  await page.screenshot({ path: testInfo.outputPath('focus-desktop.png') })
  await page.clock.runFor(220)
  await expect(player).toHaveAttribute('data-frame', '1')
  await page.clock.runFor(140)
  await expect(player).toHaveAttribute('data-frame', '0')
  await expect(player).toHaveAttribute('data-animation-offset', '0')
  await chunk(page, 'event:done\ndata:\n\n')
  await expect(player).toHaveAttribute('data-animation-state', 'idle')
  await page.setViewportSize({ width: 390, height: 900 })
  await player.scrollIntoViewIfNeeded()
  await page.screenshot({ path: testInfo.outputPath('idle-mobile.png') })
  expect(state.errors).toEqual([])
})

test('Chat, Feed and Proposal compete through R06 and recover without replaying the hint', async ({ page }) => {
  const state = await fixture(page, true)
  const feed = gate()
  const action = gate()
  await page.route('**/api/companion/feed', async route => {
    await feed.promise
    await ok(route, { outcome: 'FAMILIARITY', companion: specimen, growth: { id: '9001', createdTime: '2026-09-27T00:00:00Z', lifeExperienceDelta: 0, skillExperienceDelta: {}, traitDelta: {} } })
  })
  await page.route('**/api/companion/proposals/7010/scold', async route => { await action.promise; await ok(route, { ...proposal, status: 'SUPPRESSED' }) })
  const player = await loaded(page)
  await expect(player).toHaveAttribute('data-playback', 'playing')
  await page.getByRole('button', { name: /R07 图片/ }).click()
  await page.getByRole('button', { name: '喂给伙伴' }).click()
  await page.getByTestId('proposal-scold').click()
  await expect(page.locator('.companion-body')).toHaveAttribute('data-presentation-activity', 'feeding')
  await intent(page, 'focus')
  await chat(page)
  await page.evaluate(() => globalThis.r07Open())
  await chunk(page, 'data:正在回应\n\n')
  await expect(page.locator('.companion-body')).toHaveAttribute('data-presentation-activity', 'responding')
  await expect(player).toHaveAttribute('data-animation-state', 'focus')
  await chunk(page, 'event:done\ndata:\n\n')
  await expect(page.locator('.companion-body')).toHaveAttribute('data-presentation-activity', 'feeding')
  feed.release()
  await expect(page.locator('.companion-body')).toHaveAttribute('data-presentation-activity', 'acknowledging')
  await expect(player).toHaveAttribute('data-animation-state', 'focus')
  action.release()
  await intent(page, 'idle')
  await expect(player).toHaveAttribute('data-animation-state', 'idle')
  expect(state.errors).toEqual([])
})

test('hidden and user-paused renderers use the latest state on return, with no hint catch-up', async ({ page }) => {
  const state = await fixture(page, true)
  await page.clock.install()
  await page.clock.pauseAt(new Date())
  const player = await loaded(page)
  await expect(player).toHaveAttribute('data-animation-state', 'attention')
  await hidden(page, true)
  await expect(player).toHaveAttribute('data-animation-state', 'static')
  await page.clock.runFor(60000)
  await chat(page)
  await expect(player).toHaveAttribute('data-animation-state', 'static')
  await hidden(page, false)
  await expect(player).toHaveAttribute('data-animation-state', 'focus')
  await expect(player).toHaveAttribute('data-frame', '0')
  await page.getByRole('button', { name: '暂停动作' }).click()
  await page.evaluate(() => globalThis.r07Open())
  await chunk(page, 'event:error\ndata:失败\n\n')
  await hidden(page, true)
  await hidden(page, false)
  await expect(player).toHaveAttribute('data-animation-state', 'static')
  await page.getByRole('button', { name: '恢复动作' }).click()
  await expect(player).toHaveAttribute('data-animation-state', 'idle')
  expect(state.errors).toEqual([])
})

test('reduced motion and atlas failure suppress every action without resource retry', async ({ page }) => {
  const state = await fixture(page, true)
  await page.emulateMedia({ reducedMotion: 'reduce' })
  const player = await loaded(page)
  await chat(page)
  await expect(player).toHaveAttribute('data-animation-state', 'static')
  expect(state.images.filter(url => url.includes('adult-idle'))).toHaveLength(0)
  await page.route('**/*adult-idle*.webp*', route => route.request().resourceType() === 'image' ? route.abort() : route.continue())
  await page.emulateMedia({ reducedMotion: 'no-preference' })
  await expect(page.getByRole('button', { name: '暂停动作' })).toHaveCount(0)
  await expect.poll(() => state.images.filter(url => url.includes('adult-idle')).length).toBe(1)
  await page.evaluate(() => globalThis.r07Open())
  await chunk(page, 'event:done\ndata:\n\n')
  await hidden(page, true)
  await hidden(page, false)
  await page.emulateMedia({ reducedMotion: 'reduce' })
  await page.emulateMedia({ reducedMotion: 'no-preference' })
  await expect(player).toHaveAttribute('data-animation-state', 'static')
  await expect(player.locator('.sprite-still')).toHaveCSS('visibility', 'visible')
  expect(state.images.filter(url => url.includes('adult-idle'))).toHaveLength(1)
  expect(state.errors).toEqual([])
})
