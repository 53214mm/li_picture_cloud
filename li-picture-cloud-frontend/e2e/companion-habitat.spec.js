import { test, expect } from '@playwright/test'

const member = { id: '42', userName: '小屋测试', userRole: 'user' }
const specimen = { id: '7001', lifeStage: 'LIGHT', lifeExperience: '42', level: 1, levelStartExperience: '0', nextLevelExperience: '100', revision: '9', traits: {}, skills: [] }
const sampleImage = `data:image/svg+xml,${encodeURIComponent('<svg xmlns="http://www.w3.org/2000/svg" width="320" height="220"><rect width="320" height="220" fill="#d5dfcf"/><circle cx="230" cy="56" r="23" fill="#eee7be"/><path d="M0 220V140L110 75L240 220M120 220L265 115L320 180V220" fill="#80947c"/></svg>')}`
const photo = { id: '11', spaceId: '51', name: '绿色窗边', url: sampleImage }
const space = { id: '51', userId: '42', spaceType: 0, spaceName: '我的照片' }
async function fixture(page, companion = specimen) {
  const calls = []
  const errors = []
  page.on('pageerror', error => errors.push(error.message))
  const home = { companion, mood: null, relationship: null, recentGrowth: [], chatPolicy: 'DEMO', nutrition: { notice: '演示营养：未读取图片内容，也未调用视觉模型' } }
  await page.route(url => url.pathname.startsWith('/api/'), route => {
    const path = new URL(route.request().url()).pathname
    calls.push({ path, method: route.request().method() })
    const data = path === '/api/user/current' ? member
      : path === '/api/companion/me' ? home
        : path === '/api/companion/proposals/active' ? null
          : path === '/api/companion/contract' ? { active: false, quietStart: '23:00', quietEnd: '08:00', maxFrequencyHours: 24 }
            : path === '/api/space/list/page/vo' ? { records: [space] }
              : path === '/api/picture/list/page/vo' ? { records: [photo] }
                : path === '/api/picture/get/vo' ? photo
                  : path === '/api/space/get/vo' ? space
                    : { records: [], total: 0 }
    return route.fulfill({ json: { code: 0, data } })
  })
  return { calls, errors, home }
}
const noWrites = state => expect(state.calls.filter(call => call.path.startsWith('/api/companion/') && call.method !== 'GET')).toEqual([])

test('the room and resident lead the first screen while data remains in its own area', async ({ page }, testInfo) => {
  const state = await fixture(page)
  await page.setViewportSize({ width: 1440, height: 900 })
  await page.goto('/companion')
  await expect(page.getByRole('heading', { name: '留影小屋' })).toBeVisible()
  await expect(page.locator('.sprite-still')).toHaveJSProperty('naturalWidth', 576)
  await expect(page.locator('.companion-body')).toBeInViewport({ ratio: 1 })
  await expect(page.locator('.room-status')).toHaveText('此刻在小屋里')
  expect((await page.locator('.stats-card').boundingBox()).y).toBeGreaterThan(900)
  await expect(page.locator('.photo-nook')).toContainText('从你的图库选一张')
  await page.screenshot({ path: testInfo.outputPath('habitat-desktop.png') })
  noWrites(state)
  expect(state.errors).toEqual([])
})

test('keyboard room navigation preserves the player, chat draft and request observers', async ({ page }) => {
  const state = await fixture(page)
  await page.goto('/companion')
  await expect(page.locator('.sprite-player')).toHaveAttribute('data-playback', 'playing')
  await page.locator('.sprite-player').evaluate(el => { el.dataset.habitatRetained = 'true' })
  await page.getByRole('button', { name: '坐下聊聊' }).focus()
  await page.keyboard.press('Enter')
  const input = page.getByLabel('对伙伴说的话')
  await expect(input).toBeFocused()
  expect((await input.boundingBox()).y).toBeGreaterThanOrEqual((await page.locator('.app-toolbar').boundingBox()).height)
  await input.fill('仍在编辑的草稿')
  await page.getByRole('navigation', { name: '小屋区域' }).getByRole('link', { name: /照片桌/ }).click()
  await expect(page.locator('#feeding-title')).toBeFocused()
  expect((await page.locator('#feeding-title').boundingBox()).y).toBeGreaterThanOrEqual((await page.locator('.app-toolbar').boundingBox()).height)
  await page.getByRole('navigation', { name: '小屋区域' }).getByRole('link', { name: /留影手记/ }).click()
  await expect(page.locator('#journal-title')).toBeFocused()
  await page.getByRole('navigation', { name: '小屋区域' }).getByRole('link', { name: /相处与成长/ }).click()
  await expect(page.locator('#habitat-growth-title')).toBeFocused()
  await page.getByRole('link', { name: '回到小屋' }).click()
  await expect(page.locator('#habitat-title')).toBeFocused()
  await expect(page.locator('.sprite-player')).toHaveAttribute('data-habitat-retained', 'true')
  await expect(page.locator('.sprite-player')).toHaveAttribute('data-playback', 'playing')
  await expect(input).toHaveValue('仍在编辑的草稿')
  expect(state.calls.filter(call => call.path === '/api/companion/me')).toHaveLength(1)
  expect(state.calls.filter(call => call.path === '/api/companion/proposals/active')).toHaveLength(1)
  noWrites(state)
  expect(state.errors).toEqual([])
})

test('the photo nook mirrors the current selection without inventing a keepsake or feed', async ({ page }) => {
  const state = await fixture(page)
  await page.goto('/companion')
  await page.getByRole('button', { name: '带一张照片来' }).click()
  await expect(page.locator('#feeding-title')).toBeFocused()
  await page.getByRole('button', { name: /绿色窗边/ }).click()
  await page.getByRole('link', { name: '回到小屋' }).click()
  await expect(page.locator('.photo-nook')).toContainText('当前选择')
  await expect(page.locator('.photo-nook img')).toHaveJSProperty('naturalWidth', 320)
  await page.locator('.photo-nook').click()
  await expect(page.locator('#feeding-title')).toBeFocused()
  await expect(page.getByRole('button', { name: /绿色窗边/ })).toHaveAttribute('aria-pressed', 'true')
  noWrites(state)
  await page.reload()
  await expect(page.locator('.photo-nook')).toContainText('从你的图库选一张')
  expect(state.errors).toEqual([])
})

test('returning to a busy conversation focuses its heading without interrupting the request', async ({ page }) => {
  const state = await fixture(page)
  let release
  const held = new Promise(resolve => { release = resolve })
  await page.route('**/api/companion/chat/stream', async route => {
    await held
    await route.fulfill({ contentType: 'text/event-stream', body: 'event: message\ndata: 回复仍然抵达\n\nevent: done\ndata: {}\n\n' })
  })
  await page.goto('/companion')
  await page.getByLabel('对伙伴说的话').fill('请求还在进行')
  await page.getByRole('button', { name: '发送', exact: true }).click()
  await expect(page.getByLabel('对伙伴说的话')).toBeDisabled()
  await page.getByRole('link', { name: '回到小屋' }).click()
  await page.getByRole('button', { name: '坐下聊聊' }).focus()
  try {
    await page.keyboard.press('Enter')
    await expect(page.locator('#window-title')).toBeFocused()
    expect((await page.locator('#window-title').boundingBox()).y).toBeGreaterThanOrEqual((await page.locator('.app-toolbar').boundingBox()).height)
    await expect(page.locator('.room-status')).toHaveText('正在等待回复')
  } finally { release() }
  await expect(page.getByLabel('对伙伴说的话')).toBeEnabled()
  await expect(page.getByText('回复仍然抵达')).toBeVisible()
  expect(state.errors).toEqual([])
})

test('an empty home offers only awakening and does not fabricate a resident or a memory', async ({ page }, testInfo) => {
  const state = await fixture(page, null)
  await page.goto('/companion')
  await expect(page.getByRole('heading', { name: '留影小屋' })).toBeVisible()
  await expect(page.getByRole('button', { name: '唤醒我的伙伴' })).toBeEnabled()
  await expect(page.locator('.companion-body, .room-status, .photo-nook')).toHaveCount(0)
  await expect(page.locator('.habitat-navigation')).toHaveCount(0)
  await expect(page.getByText('实际来源会逐条写入成长档案')).toBeVisible()
  noWrites(state)
  await page.screenshot({ path: testInfo.outputPath('habitat-empty.png') })
})

test('an unavailable selected preview leaves its name and navigation intact', async ({ page }) => {
  const state = await fixture(page)
  await page.route('**/api/picture/list/page/vo', route => route.fulfill({ json: { code: 0, data: { records: [{ ...photo, url: '/missing-habitat-image.webp' }] } } }))
  await page.route('**/missing-habitat-image.webp', route => route.abort())
  await page.goto('/companion')
  await page.getByRole('button', { name: /绿色窗边/ }).click()
  await expect(page.locator('.photo-nook')).toContainText('预览暂不可用')
  await expect(page.locator('.photo-nook')).toContainText('绿色窗边')
  await page.locator('.photo-nook').click()
  await expect(page.locator('#feeding-title')).toBeFocused()
  noWrites(state)
})

test.describe('touch habitat', () => {
  test.use({ viewport: { width: 390, height: 844 }, hasTouch: true, isMobile: true })
  test('room shortcuts and the existing body interaction remain reachable', async ({ page }) => {
    const state = await fixture(page)
    await page.goto('/companion')
    await page.getByRole('button', { name: '坐下聊聊' }).tap()
    await expect(page.getByLabel('对伙伴说的话')).toBeFocused()
    expect((await page.getByLabel('对伙伴说的话').boundingBox()).y).toBeGreaterThanOrEqual((await page.locator('.app-toolbar').boundingBox()).height)
    await page.getByRole('link', { name: '回到小屋' }).tap()
    await page.locator('.body-ground').tap()
    await expect(page.getByRole('dialog', { name: '和绫页互动' })).toBeVisible()
    await expect(page.locator('.sprite-player')).toHaveAttribute('data-playback', 'static')
    await page.getByRole('button', { name: '关闭菜单' }).tap()
    await expect(page.locator('.body-ground')).toBeFocused()
    noWrites(state)
    expect(state.errors).toEqual([])
  })
})

test('room, resident and every existing area fit tablet and narrow phones', async ({ page }, testInfo) => {
  const state = await fixture(page)
  await page.emulateMedia({ reducedMotion: 'reduce' })
  await page.goto('/companion')
  for (const width of [1024, 768, 390, 320]) {
    await page.setViewportSize({ width, height: 900 })
    await page.locator('#habitat-title').scrollIntoViewIfNeeded()
    await expect(page.locator('.sprite-still')).toHaveJSProperty('naturalWidth', 576)
    await expect(page.locator('.companion-body')).toBeInViewport({ ratio: 1 })
    expect(await page.evaluate(() => globalThis.document.documentElement.scrollWidth <= globalThis.innerWidth)).toBe(true)
    await page.screenshot({ path: testInfo.outputPath(`habitat-${width}.png`) })
  }
  for (const area of ['chat', 'feed', 'journal', 'growth']) {
    await page.locator(`#habitat-${area}`).scrollIntoViewIfNeeded()
    expect(await page.evaluate(() => globalThis.document.documentElement.scrollWidth <= globalThis.innerWidth)).toBe(true)
    await page.screenshot({ path: testInfo.outputPath(`narrow-${area}.png`) })
  }
  await expect(page.locator('.chat-card, .proposal-card, .memory-card, .story-card, .emoji-card, .fusion-card, .mood-card, .relationship-card, .stats-card, .timeline-card')).toHaveCount(10)
  noWrites(state)
  expect(state.errors).toEqual([])
})
