import { test, expect } from '@playwright/test'
import { FEED_RECOVERY_KEY } from '../src/presentation/companionFeeding.js'

const member = { id: '42', userName: '照片桌测试', userRole: 'user' }
const specimen = { id: '7001', lifeStage: 'LIGHT', lifeExperience: '42', level: 1, levelStartExperience: '0', nextLevelExperience: '100', revision: '9', traits: {}, skills: [] }
const image = `data:image/svg+xml,${encodeURIComponent('<svg xmlns="http://www.w3.org/2000/svg" width="320" height="220"><rect width="320" height="220" fill="#d5dfcf"/><circle cx="230" cy="56" r="23" fill="#eee7be"/><path d="M0 220V140L110 75L240 220M120 220L265 115L320 180V220" fill="#80947c"/></svg>')}`
const photo = { id: '11', spaceId: '51', name: '窗边的绿意', url: image }
const space = { id: '51', userId: '42', spaceType: 0, spaceName: '我的照片' }
const recovery = { version: 1, actor: '42', companionId: '7001', pictureId: '11', idempotencyKey: 'restored-safe-key' }
const ok = (route, data) => route.fulfill({ json: { code: 0, data } })
function gate() { let release; const promise = new Promise(resolve => { release = resolve }); return { promise, release } }
function receipt(overrides = {}) {
  return { outcome: 'GROWN', companion: { ...specimen, revision: '10' }, growth: { id: '9001', sourcePictureId: '11', eventType: 'PICTURE_FED', createdTime: '2026-09-30T00:00:00Z', lifeExperienceDelta: '42', traitDelta: {}, skillExperienceDelta: {}, nutritionMode: 'VISUAL_MODEL', nutritionLabel: '视觉营养 · 已分析图片内容', contentUnderstood: true }, ...overrides }
}
async function fixture(page) {
  const state = { home: { companion: specimen, recentGrowth: [], mood: null, relationship: null, chatPolicy: 'DEMO', nutrition: { notice: '以实际回执为准。' } }, photos: [photo, { ...photo, id: '12', name: '另一张照片' }], writes: [], errors: [] }
  page.on('pageerror', error => state.errors.push(error.message))
  page.on('request', request => { if (request.url().endsWith('/api/companion/feed')) state.writes.push(request.postDataJSON()) })
  await page.route(url => url.pathname.startsWith('/api/'), route => {
    const path = new URL(route.request().url()).pathname
    const data = path === '/api/user/current' ? member
      : path === '/api/companion/me' ? state.home
        : path === '/api/companion/proposals/active' ? null
          : path === '/api/companion/contract' ? { active: false, quietStart: '23:00', quietEnd: '08:00', maxFrequencyHours: 24 }
            : path === '/api/space/list/page/vo' ? { records: [space] }
              : path === '/api/picture/list/page/vo' ? { records: state.photos }
                : path === '/api/picture/get/vo' ? photo
                  : path === '/api/space/get/vo' ? space
                    : { records: [], total: 0 }
    return ok(route, data)
  })
  return state
}
const card = page => page.locator('.feeding-card')
const choose = page => page.locator('.picture-choice').filter({ hasText: photo.name })
async function seedRecovery(page, value = recovery) {
  await page.addInitScript(({ key, value }) => sessionStorage.setItem(key, JSON.stringify(value)), { key: FEED_RECOVERY_KEY, value })
}

test('keyboard selection and confirmation send once, show the real receipt, and return to the picker', async ({ page }, testInfo) => {
  const state = await fixture(page); const held = gate()
  await page.setViewportSize({ width: 1440, height: 1000 })
  await page.route('**/api/companion/feed', async route => { await held.promise; await ok(route, receipt()) })
  await page.goto('/companion')
  await expect(page.getByRole('link', { name: '去私有空间挑更多' })).toHaveAttribute('href', '/space/51')
  await choose(page).focus(); await page.keyboard.press('Enter')
  await expect(card(page).getByRole('heading', { name: photo.name })).toBeVisible()
  expect(state.writes).toHaveLength(0)
  await card(page).getByRole('button', { name: '喂给伙伴' }).evaluate(button => { button.click(); button.click() })
  await expect(card(page)).toHaveAttribute('data-phase', 'submitting')
  await expect(page.locator('.picture-choice').last()).toBeDisabled()
  await expect(page.locator('.companion-body')).toHaveAttribute('data-presentation-activity', 'feeding')
  expect(state.writes).toHaveLength(1)
  held.release()
  await expect(card(page).getByRole('button', { name: '再选一张' })).toBeEnabled()
  await expect(card(page).getByText('视觉营养 · 已分析图片内容', { exact: true })).toBeVisible()
  await expect(card(page).getByText('本次已分析图片内容', { exact: true })).toBeVisible()
  await expect(card(page).getByText('+42', { exact: true })).toBeVisible()
  await card(page).scrollIntoViewIfNeeded()
  await page.screenshot({ path: testInfo.outputPath('feeding-receipt-desktop.png') })
  await card(page).getByRole('button', { name: '查看成长档案' }).click()
  await expect(page.locator('#growth-title')).toBeFocused()
  await card(page).getByRole('button', { name: '再选一张' }).click()
  await expect(page.locator('#picture-picker-title')).toBeFocused()
  await expect(card(page)).toHaveAttribute('data-phase', 'empty')
  expect(state.writes).toHaveLength(1)
  expect(state.errors).toEqual([])
})

test('lost response survives reload, locks the original picture, and reports zero-growth fallback honestly', async ({ page }) => {
  const state = await fixture(page)
  await page.route('**/api/companion/feed', route => route.abort('failed'))
  await page.goto('/companion'); await choose(page).click()
  await card(page).getByRole('button', { name: '喂给伙伴' }).click()
  await expect(card(page)).toHaveAttribute('data-phase', 'uncertain')
  const original = state.writes[0]
  const saved = await page.evaluate(key => JSON.parse(sessionStorage.getItem(key)), FEED_RECOVERY_KEY)
  expect(saved).toEqual({ ...recovery, idempotencyKey: original.idempotencyKey })
  await page.reload()
  await expect(card(page).getByText('已找回上次未确认的喂养。请主动重试以确认结果。')).toBeVisible()
  await expect(page.locator('.picture-choice').last()).toBeDisabled()
  expect(state.writes).toHaveLength(1)
  const result = receipt({ outcome: 'FAMILIARITY' })
  result.growth = { ...result.growth, lifeExperienceDelta: 0, contentUnderstood: false, nutritionMode: 'METADATA_DETERMINISTIC', nutritionLabel: '本次使用图片元数据营养', fallbackReasonCode: 'VISION_TIMEOUT' }
  await page.route('**/api/companion/feed', route => ok(route, result))
  await card(page).getByRole('button', { name: '重试这次喂养' }).click()
  await expect(card(page).getByText('再次遇见熟悉的图片')).toBeVisible()
  await expect(card(page).getByText('本次生命经验没有增加，具体变化以成长档案为准。')).toBeVisible()
  await expect(card(page).getByText('本次未读取图片内容')).toBeVisible()
  expect(state.writes).toEqual([original, original])
  expect(await page.evaluate(key => sessionStorage.getItem(key), FEED_RECOVERY_KEY)).toBeNull()
  expect(state.errors).toEqual([])
})

test('leaving during submission preserves recovery and ignores the late page completion', async ({ page }) => {
  const state = await fixture(page); const held = gate()
  await page.route('**/api/companion/feed', async route => { await held.promise; await ok(route, receipt()) })
  await page.goto('/companion'); await choose(page).click()
  await card(page).getByRole('button', { name: '喂给伙伴' }).click()
  await expect(card(page)).toHaveAttribute('data-phase', 'submitting')
  await page.locator('.app-toolbar a[href="/gallery"]').click()
  await expect(page).toHaveURL(/\/gallery$/)
  const response = page.waitForResponse('**/api/companion/feed')
  held.release(); await response
  await page.goBack()
  await expect(card(page)).toHaveAttribute('data-phase', 'uncertain')
  expect(state.writes).toHaveLength(1)
  await card(page).getByRole('button', { name: '重试这次喂养' }).click()
  await expect(card(page)).toHaveAttribute('data-phase', 'succeeded')
  expect(state.writes[1]).toEqual(state.writes[0])
  expect(state.errors).toEqual([])
})

test('a recovered inaccessible picture retains the key until a definitive denial, then allows another choice', async ({ page }) => {
  const state = await fixture(page); state.photos = [state.photos[1]]
  await seedRecovery(page)
  await page.route('**/api/picture/get/vo?id=11', route => route.fulfill({ status: 403, json: { code: 40101, message: '图片不可访问' } }))
  await page.route('**/api/companion/feed', route => route.fulfill({ status: 403, json: { code: 40101, message: '当前没有这张图片的查看权限' } }))
  await page.goto('/companion')
  await expect(card(page).getByRole('heading', { name: '图片 #11' })).toBeVisible()
  await expect(card(page).getByText('预览暂不可用')).toBeVisible()
  expect(state.writes).toHaveLength(0)
  await card(page).getByRole('button', { name: '重试这次喂养' }).click()
  await expect(card(page)).toHaveAttribute('data-phase', 'rejected')
  await expect(card(page).getByRole('alert')).toContainText('当前没有这张图片的查看权限')
  expect(state.writes[0].idempotencyKey).toBe(recovery.idempotencyKey)
  expect(await page.evaluate(key => sessionStorage.getItem(key), FEED_RECOVERY_KEY)).toBeNull()
  await page.locator('.picture-choice').click()
  await expect(card(page).getByRole('heading', { name: '另一张照片' })).toBeVisible()
  expect(state.errors).toEqual([])
})

test('successful feed and failed Home refresh remain distinct', async ({ page }) => {
  const state = await fixture(page)
  await page.route('**/api/companion/feed', route => ok(route, receipt()))
  await page.goto('/companion'); await choose(page).click()
  await page.route('**/api/companion/me', route => route.fulfill({ status: 503, json: { code: 50000, message: '刷新暂不可用' } }))
  await card(page).getByRole('button', { name: '喂给伙伴' }).click()
  await expect(card(page).getByText('回执已收到；小屋状态尚未刷新，不需要重新喂养。')).toBeVisible()
  await expect(card(page).getByRole('alert')).toHaveCount(0)
  await expect(card(page).getByRole('button', { name: '再选一张' })).toBeEnabled()
  expect(state.writes).toHaveLength(1)
})

test('familiar-image analysis is skipped rather than misreported as degradation; Home refresh serializes the next feed', async ({ page }) => {
  const state = await fixture(page); const held = gate()
  const result = receipt({ outcome: 'FAMILIARITY' })
  result.growth = { ...result.growth, lifeExperienceDelta: 0, contentUnderstood: false, nutritionLabel: '图片元数据营养', fallbackReasonCode: 'SKIPPED_FAMILIAR' }
  await page.route('**/api/companion/feed', route => ok(route, result))
  await page.goto('/companion'); await choose(page).click()
  await page.route('**/api/companion/me', async route => { await held.promise; await ok(route, state.home) })
  await card(page).getByRole('button', { name: '喂给伙伴' }).click()
  try {
    await expect(card(page).getByText('这是一张熟悉的图片，本次跳过视觉分析。')).toBeVisible()
    await expect(card(page).getByText(/本次使用了降级来源/)).toHaveCount(0)
    await expect(card(page).getByRole('button', { name: '再选一张' })).toBeDisabled()
    await expect(page.locator('.picture-choice').last()).toBeDisabled()
    expect(state.writes).toHaveLength(1)
  } finally { held.release() }
  await expect(card(page).getByRole('button', { name: '再选一张' })).toBeEnabled()
})

test('storage denial is visible while same-page recovery still uses one request key', async ({ page }) => {
  const state = await fixture(page)
  await page.addInitScript(() => Object.defineProperty(globalThis, 'sessionStorage', { get: () => { throw new Error('storage disabled') } }))
  await page.route('**/api/companion/feed', route => route.abort('failed'))
  await page.goto('/companion'); await choose(page).click()
  await expect(card(page).getByText(/浏览器未能保存恢复信息/)).toBeVisible()
  await card(page).getByRole('button', { name: '喂给伙伴' }).click()
  await card(page).getByRole('button', { name: '重试这次喂养' }).click()
  await expect(card(page)).toHaveAttribute('data-phase', 'uncertain')
  expect(state.writes).toHaveLength(2)
  expect(state.writes[1]).toEqual(state.writes[0])
  expect(state.errors).toEqual([])
})

test('logout clears recovery and a late response cannot restore the previous actor session', async ({ page }) => {
  const state = await fixture(page); const held = gate()
  await page.route('**/api/companion/feed', async route => { await held.promise; await ok(route, receipt()) })
  await page.goto('/companion'); await choose(page).click()
  await card(page).getByRole('button', { name: '喂给伙伴' }).click()
  await expect(card(page)).toHaveAttribute('data-phase', 'submitting')
  await page.getByRole('button', { name: '打开账户菜单' }).click()
  await page.getByRole('button', { name: '退出登录', exact: true }).click()
  await expect(page).toHaveURL(/\/login$/)
  const response = page.waitForResponse('**/api/companion/feed')
  held.release(); await response
  expect(await page.evaluate(key => sessionStorage.getItem(key), FEED_RECOVERY_KEY)).toBeNull()
  await expect(card(page)).toHaveCount(0)
  expect(state.errors).toEqual([])
})

test('authentication bootstrap retry still initializes the feeding session', async ({ page }) => {
  const state = await fixture(page); let failed = false
  await page.route('**/api/user/current', route => {
    if (!failed) { failed = true; return route.fulfill({ status: 503, json: { code: 50000, message: '网络暂不可用' } }) }
    return ok(route, member)
  })
  await page.goto('/companion')
  await page.getByRole('button', { name: '重试', exact: true }).click()
  await choose(page).click()
  await expect(card(page)).toHaveAttribute('data-phase', 'selected')
  expect(state.errors).toEqual([])
})

test('another actor recovery is discarded without fetching its private image or feeding', async ({ page }) => {
  const state = await fixture(page); const reads = []
  await seedRecovery(page, { ...recovery, actor: '43', pictureId: '999' })
  page.on('request', request => { if (request.url().includes('/picture/get/vo')) reads.push(request.url()) })
  await page.goto('/companion')
  await expect(card(page)).toHaveAttribute('data-phase', 'empty')
  expect(await page.evaluate(key => sessionStorage.getItem(key), FEED_RECOVERY_KEY)).toBeNull()
  expect(state.writes).toHaveLength(0); expect(reads).toEqual([])
})

test('touch and reduced motion retain confirmation, withdrawal and image-failure fallback at narrow widths', async ({ browser }, testInfo) => {
  const context = await browser.newContext({ viewport: { width: 390, height: 844 }, hasTouch: true, isMobile: true, reducedMotion: 'reduce' })
  const page = await context.newPage()
  try {
    const state = await fixture(page); state.photos[0] = { ...photo, url: '/broken-feed-preview.png' }
    await page.route('**/broken-feed-preview.png', route => route.fulfill({ status: 404, body: '' }))
    await page.goto('/companion'); await choose(page).tap()
    await expect(card(page).getByText('预览暂不可用')).toBeVisible()
    await expect(card(page).getByRole('button', { name: '喂给伙伴' })).toBeEnabled()
    await expect(page.locator('.sprite-player')).toHaveAttribute('data-animation-state', 'static')
    for (const width of [390, 320]) {
      await page.setViewportSize({ width, height: 844 })
      expect(await page.evaluate(() => globalThis.document.documentElement.scrollWidth <= globalThis.innerWidth)).toBe(true)
    }
    await card(page).scrollIntoViewIfNeeded()
    await page.screenshot({ path: testInfo.outputPath('feeding-confirm-mobile.png') })
    await card(page).getByRole('button', { name: '收回这张' }).tap()
    await expect(page.locator('#picture-picker-title')).toBeFocused()
    await expect(card(page)).toHaveAttribute('data-phase', 'empty')
    expect(state.writes).toHaveLength(0); expect(state.errors).toEqual([])
  } finally { await context.close() }
})
