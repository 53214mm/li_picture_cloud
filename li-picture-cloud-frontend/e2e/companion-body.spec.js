import { test, expect } from '@playwright/test'
import { freezeClock } from './helpers/clock.js'
import { writeFile } from 'node:fs/promises'

const member = { id: '42', userAccount: 'body-fixture', userName: '测试用户', userRole: 'user' }
const specimen = {
  id: '7001', userId: '42', lifeStage: 'LIGHT', lifeExperience: '42', level: 1,
  levelStartExperience: '0', nextLevelExperience: '100', revision: '9',
  traits: { curiosity: 12, empathy: -8 }, skills: []
}

async function fixture(page, { stage = 'LIGHT', companion = true } = {}) {
  const writes = []
  const errors = []
  const assetRequests = []
  const snapshot = {
    companion: companion ? { ...specimen, lifeStage: stage } : null,
    mood: null, relationship: null, recentGrowth: [], chatPolicy: 'DEMO',
    nutrition: { notice: '测试模式' }
  }
  page.on('pageerror', error => errors.push(error.message))
  page.on('request', request => {
    if (request.resourceType() === 'image' && /adult-.*\.webp/.test(request.url())) assetRequests.push(request.url())
    if (request.url().includes('/api/companion/') && request.method() !== 'GET') writes.push(request.url())
  })
  await page.route(url => url.pathname.startsWith('/api/'), route => {
    const path = new URL(route.request().url()).pathname
    let data = { records: [], total: 0 }
    if (path === '/api/user/current') data = member
    else if (path === '/api/companion/me') data = snapshot
    else if (path === '/api/companion/proposals/active') data = null
    else if (path === '/api/companion/contract') data = { active: false, quietStart: '22:00', quietEnd: '08:00', maxFrequencyHours: 12 }
    else if (path.includes('tag_category')) data = { tagList: [], categoryList: [] }
    return route.fulfill({ json: { code: 0, data } })
  })
  return { writes, errors, assetRequests, snapshot }
}

async function loaded(page) {
  const player = page.locator('.companion-body')
  await expect(player).toBeVisible()
  await expect(player.locator('.body-static img')).toHaveJSProperty('naturalWidth', 576)
  return player
}

for (const [stage, label] of [['LIGHT', '初始阶段'], ['SEEDLING', '成长阶段'], ['COMPANION', '成熟阶段'], ['FUTURE', '阶段待确认']]) {
  test(`${stage} retains its business stage and snapshot with adult artwork and no writes`, async ({ page }) => {
    const state = await fixture(page, { stage })
    const before = JSON.stringify(state.snapshot)
    await page.goto('/companion')
    await expect(page.locator('.companion-body')).toHaveAttribute('data-visual-stage', 'adult')
    await expect(page.locator('#companion-stats-title')).toHaveText('绫页')
    await expect(page.getByTestId('growth-stage')).toHaveText(label)
    await expect(page.getByText('42 / 100 生命经验')).toBeVisible()
    await page.locator('.chat-card').scrollIntoViewIfNeeded()
    await expect(page.locator('.chat-card .companion-portrait img')).toHaveJSProperty('naturalWidth', 384)
    await expect(page.getByLabel('对伙伴说的话')).toBeEditable()
    await expect(page.locator('.sprite-player')).toHaveCount(0)
    expect(JSON.stringify(state.snapshot)).toBe(before)
    expect(state.writes).toEqual([])
    expect(state.errors).toEqual([])
  })
}

test('static artwork survives visibility, modal and reduced-motion changes and cleans up on navigation', async ({ page }) => {
  await fixture(page)
  await freezeClock(page)
  await page.goto('/companion')
  const player = await loaded(page)
  await expect(player).toHaveAttribute('data-animation-intent', 'static')
  // Exercise the browser visibility event deterministically; real-device background
  // throttling remains a separate manual check, not inferred from this fixture.
  await page.evaluate(() => {
    // eslint-disable-next-line no-undef
    Object.defineProperty(document, 'hidden', { configurable: true, value: true })
    // eslint-disable-next-line no-undef
    document.dispatchEvent(new Event('visibilitychange'))
  })
  await expect(player).toHaveAttribute('data-animation-intent', 'static')
  await page.clock.runFor(60000)
  await page.evaluate(() => {
    // eslint-disable-next-line no-undef
    delete document.hidden
    // eslint-disable-next-line no-undef
    document.dispatchEvent(new Event('visibilitychange'))
  })
  await expect(player).toHaveAttribute('data-animation-intent', 'static')
  await expect(page.locator('.sprite-player')).toHaveCount(0)
  await page.clock.runFor(4200)
  await expect(page.locator('.sprite-player')).toHaveCount(0)
  await page.clock.runFor(140)
  await expect(page.locator('.sprite-player')).toHaveCount(0)
  await expect(page.getByRole('button', { name: /暂停动作|恢复动作/ })).toHaveCount(0)
  await expect(player).toHaveAttribute('data-animation-intent', 'static')
  await page.getByRole('button', { name: '打开账户菜单' }).click()
  await page.keyboard.press('Escape')
  await expect(player).toHaveAttribute('data-animation-intent', 'static')
  await expect(page.getByRole('button', { name: /暂停动作|恢复动作/ })).toHaveCount(0)
  await expect(player).toHaveAttribute('data-animation-intent', 'static')
  await page.getByRole('button', { name: '打开账户菜单' }).click()
  await expect(player).toHaveAttribute('data-animation-intent', 'static')
  await page.keyboard.press('Escape')
  await expect(player).toHaveAttribute('data-animation-intent', 'static')
  await page.emulateMedia({ reducedMotion: 'reduce' })
  await expect(player).toHaveAttribute('data-animation-intent', 'static')
  await expect(page.locator('.sprite-player')).toHaveCount(0)
  await expect(page.getByRole('button', { name: '暂停动作' })).toHaveCount(0)
  await page.emulateMedia({ reducedMotion: 'no-preference' })
  await expect(player).toHaveAttribute('data-animation-intent', 'static')
  await page.getByLabel('对伙伴说的话').scrollIntoViewIfNeeded()
  await expect(player).toHaveAttribute('data-animation-intent', 'static')
  await player.scrollIntoViewIfNeeded()
  await expect(player).toHaveAttribute('data-animation-intent', 'static')
  // Route transitions use their own timeout lifecycle; release the
  // deterministic test clock before exercising normal navigation cleanup.
  await page.clock.resume()
  for (let i = 0; i < 3; i++) {
    await page.locator('.sidebar').getByRole('link', { name: '图库', exact: true }).click()
    await expect(page.locator('.companion-body')).toHaveCount(0)
    await page.locator('.sidebar').getByRole('link', { name: '伙伴空间', exact: true }).click()
    await expect(page.locator('.companion-body')).toHaveCount(1)
    await expect(page.locator('.companion-body')).toHaveAttribute('data-animation-intent', 'static')
  }
})

test('legacy overlays leave the existing static artwork stable', async ({ page }) => {
  await fixture(page)
  await page.goto('/companion')
  const player = await loaded(page)
  await expect(player).toHaveAttribute('data-animation-intent', 'static')
  await page.evaluate(() => {
    // eslint-disable-next-line no-undef
    const overlay = document.createElement('div')
    overlay.className = 'modal-overlay'
    overlay.textContent = 'Legacy overlay test fixture'
    // eslint-disable-next-line no-undef
    document.body.append(overlay)
  })
  await expect(player).toHaveAttribute('data-animation-intent', 'static')
  // eslint-disable-next-line no-undef
  await page.evaluate(() => document.querySelector('.modal-overlay').remove())
  await expect(player).toHaveAttribute('data-animation-intent', 'static')
})

test('static artwork never requests the blink atlas and records cold resource timing', async ({ page }, testInfo) => {
  const state = await fixture(page)
  const started = Date.now()
  await page.goto('/companion')
  const body = await loaded(page)
  const firstFrameReadyUpperBoundMs = Date.now() - started
  await expect(body).toHaveAttribute('data-animation-intent', 'static')
  await expect(page.locator('.sprite-player')).toHaveCount(0)
  expect(state.assetRequests.some(url => url.includes('adult-idle'))).toBe(false)
  const resources = await page.evaluate(() => performance.getEntriesByType('resource')
    .filter(entry => /adult-.*\.webp/.test(entry.name) && entry.initiatorType === 'img')
    .map(entry => ({ url: entry.name, durationMs: entry.duration, transferBytes: entry.transferSize })))
  const reportPath = testInfo.outputPath('body-performance.json')
  await writeFile(reportPath, JSON.stringify({ firstFrameReadyUpperBoundMs, resources }, null, 2))
  await testInfo.attach('body-performance.json', { path: reportPath, contentType: 'application/json' })
  expect(state.errors).toEqual([])
})

test('motion preference changes never request the atlas and keep static artwork', async ({ page }) => {
  const state = await fixture(page)
  await page.emulateMedia({ reducedMotion: 'reduce' })
  await page.goto('/companion')
  const player = await loaded(page)
  await expect(player).toHaveAttribute('data-animation-intent', 'static')
  expect(state.assetRequests.some(url => url.includes('adult-idle'))).toBe(false)
  await page.route('**/*adult-idle*.webp*', route => route.request().resourceType() === 'image' ? route.abort() : route.continue())
  await page.emulateMedia({ reducedMotion: 'no-preference' })
  await expect(player.locator('.sprite-atlas')).toHaveCount(0)
  await expect(player.locator('.body-static img')).toBeVisible()
  await expect(page.getByRole('button', { name: '暂停动作' })).toHaveCount(0)
})

test('all image failures preserve labels, navigation, geometry and chat', async ({ page }) => {
  const state = await fixture(page)
  await page.route('**/*adult-*.webp*', route => route.request().resourceType() === 'image' ? route.abort() : route.continue())
  await page.goto('/companion')
  await expect(page.locator('.body-static')).toHaveAttribute('data-artwork-state', 'fallback')
  await expect(page.locator('.sidebar .presence')).toBeVisible()
  await page.locator('.chat-card').scrollIntoViewIfNeeded()
  await expect(page.locator('.chat-card .companion-portrait')).toHaveAttribute('data-artwork-state', 'fallback')
  await page.getByLabel('对伙伴说的话').fill('头像失败时仍可输入')
  await expect(page.getByRole('button', { name: '发送', exact: true })).toBeEnabled()
  expect(state.writes).toEqual([])
  expect(state.errors).toEqual([])
})

test('loading, failed and absent companion never manufacture a body or request an idle', async ({ page }) => {
  const state = await fixture(page, { companion: false })
  let release
  const held = new Promise(resolve => { release = resolve })
  await page.route('**/api/companion/me', async route => {
    await held
    await route.fulfill({ status: 503, json: { code: 50000, message: '模拟暂时不可用' } })
  })
  await page.goto('/companion')
  await expect(page.getByText('正在进入伙伴空间…')).toBeVisible()
  await expect(page.locator('.companion-body')).toHaveCount(0)
  release()
  await expect(page.getByText('暂时没能找到伙伴')).toBeVisible()
  await page.unroute('**/api/companion/me')
  await page.getByRole('button', { name: '重新加载' }).click()
  await expect(page.getByRole('button', { name: '唤醒我的伙伴' })).toBeVisible()
  await expect(page.locator('.companion-body')).toHaveCount(0)
  await expect(page.locator('main .companion-portrait')).toHaveCount(0)
  // The shell's opt-in site-presence widget has a static neutral thumbnail;
  // it does not create an owned companion, Home body or idle animation.
  await expect(page.getByTestId('site-presence')).toContainText('默认关闭')
  expect(state.assetRequests.some(url => /adult-(home|idle)/.test(url))).toBe(false)
  expect(state.writes).toEqual([])
})

test('Shell and body fit desktop, tablet and narrow phones', async ({ page }, testInfo) => {
  await fixture(page)
  await page.emulateMedia({ reducedMotion: 'reduce' })
  await page.goto('/companion')
  for (const width of [1440, 1024, 768, 390, 320]) {
    await page.setViewportSize({ width, height: 900 })
    await page.locator('.companion-body').scrollIntoViewIfNeeded()
    await expect(page.locator('.body-static img')).toHaveJSProperty('naturalWidth', 576)
    // eslint-disable-next-line no-undef
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
    if (width >= 768) await expect(page.locator('.sidebar .presence__mark')).toHaveCSS('width', '40px')
    await page.screenshot({ path: testInfo.outputPath(`body-${width}.png`) })
    await page.locator('.chat-card').scrollIntoViewIfNeeded()
    await expect(page.locator('.chat-card .companion-portrait')).toHaveCSS('width', width < 768 ? '72px' : '104px')
    await page.screenshot({ path: testInfo.outputPath(`portrait-${width}.png`) })
  }
})
