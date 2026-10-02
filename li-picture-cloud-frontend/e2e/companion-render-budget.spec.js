import { test, expect } from '@playwright/test'

const specimen = { id: '7001', lifeStage: 'LIGHT', lifeExperience: '42', level: 1, levelStartExperience: '0', nextLevelExperience: '100', revision: '9', traits: {}, skills: [] }
const ok = (route, data) => route.fulfill({ json: { code: 0, data } })
async function fixture(page, proposal = false) {
  const state = { images: [], writes: [], errors: [] }
  page.on('request', request => {
    if (request.resourceType() === 'image') state.images.push(request.url())
    if (request.method() !== 'GET') state.writes.push(request.url())
  })
  page.on('pageerror', error => state.errors.push(error.message))
  await page.route(url => url.pathname.startsWith('/api/'), route => {
    const path = new URL(route.request().url()).pathname
    if (path === '/api/user/current') return ok(route, { id: '42', userName: 'R13', userRole: 'user' })
    if (path === '/api/companion/me') return ok(route, { companion: specimen, mood: null, relationship: null, recentGrowth: [], nutrition: {}, chatPolicy: 'DEMO' })
    if (path === '/api/companion/proposals/active') return ok(route, proposal ? { id: '91', status: 'PENDING', content: '来看看照片？', opportunityType: 'WEEKLY_REVIEW', impulseScore: 2 } : null)
    if (path === '/api/companion/contract') return ok(route, { active: false, quietStart: '23:00', quietEnd: '08:00', maxFrequencyHours: 1 })
    if (path.includes('tag_category')) return ok(route, { tagList: [], categoryList: [] })
    return ok(route, { records: [], total: 0 })
  })
  return state
}
const player = page => page.locator('.sprite-player')
const body = page => page.locator('.companion-body')
function noAtlas(state) { expect(state.images.some(url => url.includes('adult-idle'))).toBe(false) }
async function connection(page, settings) {
  await page.addInitScript(value => {
    const info = new globalThis.EventTarget()
    Object.assign(info, value)
    Object.defineProperty(globalThis.navigator, 'connection', { configurable: true, value: info })
    globalThis.r13Connection = info
  }, settings)
}

test('R13 mobile starts with a compact clickable still and no atlas, leaving photo actions reachable', async ({ page }, testInfo) => {
  await page.setViewportSize({ width: 320, height: 700 })
  const state = await fixture(page)
  await page.goto('/companion')
  await expect(body(page)).toHaveAttribute('data-render-mode', 'compact')
  await expect(player(page)).toHaveAttribute('data-playback', 'static')
  await expect(player(page).locator('.sprite-still')).toHaveJSProperty('naturalWidth', 576)
  await expect(page.locator('.body-ground')).toHaveCSS('width', '96px')
  await expect(page.getByRole('button', { name: '暂停动作' })).toHaveCount(0)
  noAtlas(state)
  const scene = await page.locator('.habitat-scene').boundingBox()
  expect(scene.height).toBeLessThan(600)
  expect(await page.evaluate(() => globalThis.document.documentElement.scrollWidth <= globalThis.innerWidth)).toBe(true)
  await page.screenshot({ path: testInfo.outputPath('r13-compact-home.png') })
  await page.locator('.body-ground').click()
  await expect(page.getByRole('dialog', { name: '和绫页互动' })).toBeVisible()
  await page.keyboard.press('Escape')
  await page.getByRole('button', { name: '照片留位，前往照片桌' }).click()
  await expect(page.locator('#feeding-title')).toBeFocused()
  expect(state.writes).toEqual([])
  expect(state.errors).toEqual([])
})

test('R13 breakpoint changes preserve the same player, consumed proposal hint and user pause', async ({ page }) => {
  await page.setViewportSize({ width: 1280, height: 900 })
  await fixture(page, true)
  await page.clock.install()
  await page.clock.pauseAt(new Date())
  await page.goto('/companion')
  await expect(player(page)).toHaveAttribute('data-animation-state', 'attention')
  await player(page).evaluate(element => { element.dataset.r13Identity = 'same-player' })
  await page.clock.runFor(1060)
  await expect(player(page)).toHaveAttribute('data-animation-state', 'idle')
  await page.setViewportSize({ width: 390, height: 844 })
  await expect(player(page)).toHaveAttribute('data-playback', 'static')
  await page.setViewportSize({ width: 1280, height: 900 })
  await player(page).scrollIntoViewIfNeeded()
  await expect(player(page)).toHaveAttribute('data-animation-state', 'idle')
  await expect(player(page)).toHaveAttribute('data-r13-identity', 'same-player')
  await page.getByRole('button', { name: '暂停动作' }).click()
  await page.setViewportSize({ width: 390, height: 844 })
  await page.setViewportSize({ width: 1280, height: 900 })
  await expect(page.getByRole('button', { name: '恢复动作' })).toHaveAttribute('aria-pressed', 'true')
  await expect(player(page)).toHaveAttribute('data-playback', 'static')
})

for (const [settings, reason] of [[{ saveData: true, effectiveType: '4g' }, 'save-data'], [{ saveData: false, effectiveType: '2g' }, 'slow-connection']]) {
  test(`R13 ${reason} prevents initial atlas IO and resumes only after the budget changes`, async ({ page }) => {
    await connection(page, settings)
    const state = await fixture(page)
    await page.goto('/companion')
    await expect(body(page)).toHaveAttribute('data-motion-policy', reason)
    await expect(player(page)).toHaveAttribute('data-playback', 'static')
    await expect(player(page).locator('.sprite-still')).toHaveJSProperty('naturalWidth', 576)
    noAtlas(state)
    await page.evaluate(() => { Object.assign(globalThis.r13Connection, { saveData: false, effectiveType: '4g' }); globalThis.r13Connection.dispatchEvent(new globalThis.Event('change')) })
    await expect(player(page)).toHaveAttribute('data-playback', 'playing')
    await page.evaluate(() => { globalThis.r13Connection.saveData = true; globalThis.r13Connection.dispatchEvent(new globalThis.Event('change')) })
    await expect(player(page)).toHaveAttribute('data-playback', 'static')
    expect(state.writes).toEqual([])
    expect(state.errors).toEqual([])
  })
}

test('R13 viewport changes cannot override reduced-motion or resource failure', async ({ page }) => {
  const state = await fixture(page)
  await page.emulateMedia({ reducedMotion: 'reduce' })
  await page.goto('/companion')
  for (const width of [390, 1280]) {
    await page.setViewportSize({ width, height: 900 })
    await expect(player(page)).toHaveAttribute('data-playback', 'static')
  }
  noAtlas(state)
  await page.route('**/*adult-idle*.webp*', route => route.request().resourceType() === 'image' ? route.abort() : route.continue())
  await page.emulateMedia({ reducedMotion: 'no-preference' })
  await expect(page.locator('.body-static-label')).toBeVisible()
  for (const width of [390, 1280]) {
    await page.setViewportSize({ width, height: 900 })
    await expect(player(page)).toHaveAttribute('data-playback', 'static')
  }
  expect(state.errors).toEqual([])
})

test('R13 touch-sized navigation keeps upload and gallery access without a floating body', async ({ page }, testInfo) => {
  await page.setViewportSize({ width: 320, height: 568 })
  const state = await fixture(page)
  await page.goto('/gallery')
  await expect(body(page)).toHaveCount(0)
  await expect(page.locator('.bottom-navigation .companion-tab')).toBeVisible()
  await expect(page.getByRole('link', { name: '上传', exact: true })).toBeVisible()
  await page.getByRole('link', { name: '上传', exact: true }).click()
  await expect(page).toHaveURL(/\/upload/)
  await expect(body(page)).toHaveCount(0)
  await page.screenshot({ path: testInfo.outputPath('r13-upload-mobile.png') })
  expect(state.writes).toEqual([])
  expect(state.errors).toEqual([])
})
