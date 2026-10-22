import { test, expect } from '@playwright/test'
import { freezeClock } from './helpers/clock.js'

const specimen = { id: '7001', lifeStage: 'LIGHT', lifeExperience: '42', level: 1, levelStartExperience: '0', nextLevelExperience: '100', revision: '9', traits: {}, skills: [] }
const ok = (route, data) => route.fulfill({ json: { code: 0, data } })
function gate() { let release; const promise = new Promise(resolve => { release = resolve }); return { promise, release } }

async function fixture(page) {
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
  const player = page.locator('.companion-body')
  await expect(player.locator('.body-static img')).toHaveJSProperty('naturalWidth', 576)
  return player
}
async function intent(page, value) {
  for (const selector of ['.companion-body', '.chat-card .companion-portrait', '.sidebar .companion-presence']) {
    await expect(page.locator(selector)).toHaveAttribute('data-animation-intent', value)
  }
  for (const selector of ['.chat-card .companion-portrait', '.sidebar .companion-presence']) {
    await expect(page.locator(selector)).toHaveAttribute('data-animation-intent', 'static')
  }
}
async function chat(page) {
  await page.getByLabel('对伙伴说的话').fill('这段文本不影响动画')
  await page.locator('.companion-body').scrollIntoViewIfNeeded()
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

test('thinking and streaming preserve activity facts while artwork stays static', async ({ page }, testInfo) => {
  const state = await fixture(page)
  await freezeClock(page)
  const player = await loaded(page)
  await expect(player).toHaveAttribute('data-animation-intent', 'static')
  await chat(page)
  await intent(page, 'static')
  await expect(player).toHaveAttribute('data-animation-intent', 'static')
  await page.clock.runFor(800)
  await page.evaluate(() => globalThis.r07Open())
  await chunk(page, 'data:生气 庆祝 睡觉\n\n')
  await expect(page.locator('.companion-body')).toHaveAttribute('data-presentation-activity', 'responding')
  await page.clock.runFor(800)
  await expect(page.locator('.sprite-player')).toHaveCount(0)
  await expect(player.locator('.sprite-atlas')).toHaveCount(0)
  await page.screenshot({ path: testInfo.outputPath('focus-desktop.png') })
  await page.clock.runFor(220)
  await expect(page.locator('.sprite-player')).toHaveCount(0)
  await page.clock.runFor(140)
  await expect(page.locator('.sprite-player')).toHaveCount(0)
  await expect(page.locator('.sprite-player')).toHaveCount(0)
  await chunk(page, 'event:done\ndata:\n\n')
  await expect(player).toHaveAttribute('data-animation-intent', 'static')
  await page.setViewportSize({ width: 390, height: 900 })
  await player.scrollIntoViewIfNeeded()
  await page.screenshot({ path: testInfo.outputPath('idle-mobile.png') })
  expect(state.errors).toEqual([])
})

test('Chat and Feed retain R06 precedence while artwork stays static', async ({ page }) => {
  const state = await fixture(page)
  const feed = gate()
  await page.route('**/api/companion/feed', async route => {
    await feed.promise
    await ok(route, { outcome: 'FAMILIARITY', companion: specimen, growth: { id: '9001', createdTime: '2026-09-27T00:00:00Z', lifeExperienceDelta: 0, skillExperienceDelta: {}, traitDelta: {} } })
  })
  const player = await loaded(page)
  await expect(player).toHaveAttribute('data-animation-intent', 'static')
  await page.getByRole('button', { name: /R07 图片/ }).click()
  await page.getByRole('button', { name: '喂给伙伴' }).click()
  await expect(page.locator('.companion-body')).toHaveAttribute('data-presentation-activity', 'feeding')
  await intent(page, 'static')
  await chat(page)
  await page.evaluate(() => globalThis.r07Open())
  await chunk(page, 'data:正在回应\n\n')
  await expect(page.locator('.companion-body')).toHaveAttribute('data-presentation-activity', 'responding')
  await expect(player).toHaveAttribute('data-animation-intent', 'static')
  await chunk(page, 'event:done\ndata:\n\n')
  await expect(page.locator('.companion-body')).toHaveAttribute('data-presentation-activity', 'feeding')
  feed.release()
  await expect(page.locator('.companion-body')).toHaveAttribute('data-presentation-activity', 'idle')
  await intent(page, 'static')
  await expect(player).toHaveAttribute('data-animation-intent', 'static')
  expect(state.errors).toEqual([])
})

test('hidden renderers retain current activity on return without animation catch-up', async ({ page }) => {
  const state = await fixture(page)
  await freezeClock(page)
  const player = await loaded(page)
  await expect(player).toHaveAttribute('data-animation-intent', 'static')
  await hidden(page, true)
  await expect(player).toHaveAttribute('data-animation-intent', 'static')
  await page.clock.runFor(60000)
  await chat(page)
  await expect(player).toHaveAttribute('data-animation-intent', 'static')
  await hidden(page, false)
  await expect(player).toHaveAttribute('data-animation-intent', 'static')
  await expect(page.locator('.sprite-player')).toHaveCount(0)
  await expect(page.getByRole('button', { name: /暂停动作|恢复动作/ })).toHaveCount(0)
  await page.evaluate(() => globalThis.r07Open())
  await chunk(page, 'event:error\ndata:失败\n\n')
  await hidden(page, true)
  await hidden(page, false)
  await expect(player).toHaveAttribute('data-animation-intent', 'static')
  await expect(page.getByRole('button', { name: /暂停动作|恢复动作/ })).toHaveCount(0)
  await expect(player).toHaveAttribute('data-animation-intent', 'static')
  expect(state.errors).toEqual([])
})

test('reduced-motion changes never request the disabled blink atlas', async ({ page }) => {
  const state = await fixture(page)
  await page.emulateMedia({ reducedMotion: 'reduce' })
  const player = await loaded(page)
  await chat(page)
  await expect(player).toHaveAttribute('data-animation-intent', 'static')
  expect(state.images.filter(url => url.includes('adult-idle'))).toHaveLength(0)
  await page.route('**/*adult-idle*.webp*', route => route.request().resourceType() === 'image' ? route.abort() : route.continue())
  await page.emulateMedia({ reducedMotion: 'no-preference' })
  await expect(page.getByRole('button', { name: '暂停动作' })).toHaveCount(0)
  expect(state.images.filter(url => url.includes('adult-idle'))).toHaveLength(0)
  await page.evaluate(() => globalThis.r07Open())
  await chunk(page, 'event:done\ndata:\n\n')
  await hidden(page, true)
  await hidden(page, false)
  await page.emulateMedia({ reducedMotion: 'reduce' })
  await page.emulateMedia({ reducedMotion: 'no-preference' })
  await expect(player).toHaveAttribute('data-animation-intent', 'static')
  await expect(player.locator('.body-static img')).toHaveCSS('visibility', 'visible')
  expect(state.images.filter(url => url.includes('adult-idle'))).toHaveLength(0)
  expect(state.errors).toEqual([])
})
