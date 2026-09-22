import { test, expect } from '@playwright/test'

const member = { id: '42', userAccount: 'gallery-test', userName: '测试用户', userRole: 'user' }
const assets = ['nature', 'city', 'portrait', 'architecture', 'travel', 'bw', 'still-life', 'abstract']
const records = Array.from({ length: 120 }, (_, index) => ({
  id: String(1001 + index),
  name: `${index % 2 ? '城市光影' : '山间水岸'} ${String(index + 1).padStart(3, '0')}`,
  introduction: index % 2 ? '夜晚的街道' : '清晨的水面',
  url: `/images/mosaic/${assets[index % assets.length]}.jpg`,
  category: index % 2 ? '城市' : '风景',
  picFormat: index % 2 ? 'png' : 'jpg',
  tags: index % 2 ? ['城市', '建筑', '夜晚'] : ['自然', '旅行', '清晨'],
  createTime: '2026-09-22T08:00:00+08:00',
  user: member
}))
const runtimeErrors = new WeakMap()
test.afterEach(async ({ page }) => expect(runtimeErrors.get(page) || []).toEqual([]))

function resultFor(query) {
  const matches = records.filter(picture =>
    (!query.searchText || `${picture.name} ${picture.introduction}`.includes(query.searchText)) &&
    (!query.category || picture.category === query.category) &&
    (!query.picFormat || picture.picFormat === query.picFormat) &&
    (!query.tags?.length || query.tags.every(tag => picture.tags.includes(tag)))
  )
  if (query.sortOrder === 'ascend') matches.reverse()
  return { records: matches.slice((query.current - 1) * query.pageSize, query.current * query.pageSize), total: matches.length }
}

async function fixture(page, user = member) {
  const state = { failList: false, failMeta: false, requests: [] }
  const errors = []
  runtimeErrors.set(page, errors)
  page.on('pageerror', error => errors.push(error.message))
  await page.route(url => url.pathname.startsWith('/api/'), async route => {
    const url = new URL(route.request().url())
    let data = []
    if (url.pathname === '/api/user/current') data = user
    if (url.pathname === '/api/picture/tag_category') {
      if (state.failMeta) return route.fulfill({ status: 503, json: { code: 50000, message: '测试：元数据失败' } })
      data = { categoryList: ['风景', '城市'], tagList: ['自然', '旅行', '清晨', '城市', '建筑', '夜晚'] }
    }
    if (url.pathname === '/api/picture/list/page/vo/cache') {
      const query = route.request().postDataJSON()
      state.requests.push(query)
      if (state.failList) return route.fulfill({ status: 503, json: { code: 50000, message: '测试：列表失败' } })
      data = resultFor(query)
    }
    if (url.pathname === '/api/picture/get/vo') data = records.find(record => record.id === url.searchParams.get('id'))
    await route.fulfill({ json: { code: 0, data } })
  })
  return state
}

test('list failure is distinct from empty, clears stale pagination and can retry', async ({ page }) => {
  const state = await fixture(page)
  await page.goto('/gallery')
  const gallery = page.locator('.gallery-page')
  await expect(gallery.locator('.gallery-card')).toHaveCount(12)
  state.failList = true
  await gallery.getByRole('button', { name: '搜索', exact: true }).click()
  await expect(gallery.getByRole('heading', { name: '图库加载失败' })).toBeVisible()
  await expect(gallery.locator('.pagination')).toHaveCount(0)
  await expect(gallery.getByText('没有找到匹配的图片')).toHaveCount(0)
  state.failList = false
  await gallery.getByRole('button', { name: '重试', exact: true }).click()
  await expect(gallery.locator('.gallery-card')).toHaveCount(12)
  await expect(gallery.locator('.pagination')).toContainText('120 张')
})

test('superseded list responses cannot overwrite the newest search', async ({ page }) => {
  await fixture(page)
  await page.goto('/gallery')
  await expect(page.locator('.gallery-card')).toHaveCount(12)
  let release
  const held = new Promise(resolve => { release = resolve })
  await page.route('**/api/picture/list/page/vo/cache', async route => {
    const query = route.request().postDataJSON()
    if (query.searchText === '山间') await held
    await route.fulfill({ json: { code: 0, data: resultFor(query) } })
  })
  const search = page.getByPlaceholder('搜索图片名称或简介…')
  const oldRequest = page.waitForRequest(request => request.url().includes('/list/page/vo/cache') && request.postDataJSON().searchText === '山间')
  await search.fill('山间')
  await search.press('Enter')
  await oldRequest
  await search.fill('城市')
  await search.press('Enter')
  await expect(page.locator('.card-name').first()).toHaveText('城市光影 002')
  const oldResponse = page.waitForResponse(response => response.url().includes('/list/page/vo/cache') && response.request().postDataJSON().searchText === '山间')
  release()
  await (await oldResponse).finished()
  // Let the stale response's promise and Vue's DOM update settle, without a timed sleep.
  await page.evaluate(() => new Promise(resolve => {
    // eslint-disable-next-line no-undef
    requestAnimationFrame(() => requestAnimationFrame(resolve))
  }))
  await expect(page.locator('.card-name').first()).toHaveText('城市光影 002')
})

test('search, combined filters, visible AND tags and clear use the existing API', async ({ page }) => {
  const state = await fixture(page)
  await page.goto('/gallery')
  const gallery = page.locator('.gallery-page')
  const search = gallery.getByRole('searchbox', { name: '搜索图片名称或简介' })
  await search.fill('清晨')
  await search.press('Enter')
  await expect(gallery.locator('.applied-summary')).toContainText('60 张')
  await gallery.getByLabel('分类', { exact: true }).selectOption('风景')
  await gallery.getByLabel('格式', { exact: true }).selectOption('jpg')
  await gallery.getByLabel('排序', { exact: true }).selectOption('ascend')
  await gallery.locator('.tag-filters').getByRole('button', { name: '自然', exact: true }).click()
  await gallery.locator('.tag-filters').getByRole('button', { name: '旅行', exact: true }).click()
  await expect(gallery.locator('.applied-summary')).toContainText('标签同时满足：自然、旅行')
  await expect(gallery.locator('.card-name').first()).toHaveText('山间水岸 119')
  expect(state.requests.at(-1)).toMatchObject({ current: 1, pageSize: 12, searchText: '清晨', category: '风景', picFormat: 'jpg', sortField: 'createTime', sortOrder: 'ascend', tags: ['自然', '旅行'] })
  await expect(gallery.locator('.tag-chip[aria-pressed="true"]')).toHaveCount(2)
  await expect(gallery.locator('.tag-chip')).toHaveCount(6)
  await gallery.locator('.tag-filters').getByRole('button', { name: '城市', exact: true }).click()
  await expect(gallery.getByRole('heading', { name: '没有找到匹配的图片' })).toBeVisible()
  await gallery.locator('.filter-row').getByRole('button', { name: '清除筛选' }).click()
  await expect(gallery.locator('.gallery-card')).toHaveCount(12)
  expect(state.requests.at(-1)).toMatchObject({ current: 1, pageSize: 12, searchText: '', category: '', picFormat: '', sortOrder: 'descend', tags: null })
  await expect(search).toHaveValue('')
  await expect(gallery.locator('.pagination')).toContainText('120 张')
})

test('mobile Enter and GO jump, boundaries and 12 per page are preserved', async ({ page }) => {
  const state = await fixture(page)
  await page.setViewportSize({ width: 390, height: 844 })
  await page.emulateMedia({ reducedMotion: 'reduce' })
  await page.goto('/gallery')
  const gallery = page.locator('.gallery-page')
  const jump = gallery.getByRole('textbox', { name: '跳转页码' })
  await expect(gallery.locator('.gallery-card')).toHaveCount(12)
  await expect(jump).toBeVisible()
  await jump.fill('8')
  await jump.press('Enter')
  await expect(gallery.locator('.card-name').first()).toHaveText('山间水岸 085')
  await expect(page).toHaveURL(/page=8/)
  await jump.fill('10')
  // Input blur restores the Shell bottom bar. GO must still receive the click.
  await gallery.getByRole('button', { name: 'GO', exact: true }).click()
  await expect(gallery.locator('.card-name').first()).toHaveText('山间水岸 109')
  await expect(gallery.getByRole('button', { name: '下一页' })).toBeDisabled()
  await jump.fill('999')
  await jump.press('Enter')
  await expect(gallery.locator('.pagination')).toContainText('第 10 / 10 页 (120 张)')
  await jump.fill('非法')
  await jump.press('Enter')
  await expect(gallery.locator('.card-name').first()).toHaveText('山间水岸 001')
  await expect(gallery.getByRole('button', { name: '上一页' })).toBeDisabled()
  expect(state.requests.every(query => query.pageSize === 12 && Number.isInteger(query.current))).toBe(true)
})

test('metadata failure does not block pictures; loading reserves 12 frames', async ({ page }) => {
  const state = await fixture(page)
  state.failMeta = true
  let release
  const held = new Promise(resolve => { release = resolve })
  await page.route('**/api/picture/list/page/vo/cache', async route => {
    await held
    await route.fulfill({ json: { code: 0, data: resultFor(route.request().postDataJSON()) } })
  })
  await page.goto('/gallery')
  await expect(page.locator('.gallery-skeleton')).toHaveCount(12)
  await expect(page.getByText('分类和标签暂时无法加载。')).toBeVisible()
  state.failMeta = false
  await page.getByRole('button', { name: '重试筛选项' }).click()
  await expect(page.locator('.tag-chip')).toHaveCount(6)
  release()
  await expect(page.locator('.gallery-card')).toHaveCount(12)
})

test('image failure keeps its frame and metadata; keyboard link still opens detail', async ({ page }) => {
  await fixture(page)
  await page.route('**/images/mosaic/nature.jpg', route => route.abort())
  await page.goto('/gallery')
  const card = page.locator('.gallery-card').first()
  await expect(card.getByText('预览暂不可用')).toBeVisible()
  await expect(card.locator('.card-name')).toHaveText('山间水岸 001')
  await expect(card.locator('.card-meta')).toContainText('测试用户 · 2026/9/22')
  await expect(card.locator('.mini-tag')).toHaveCount(3)
  const box = await card.boundingBox()
  expect(Math.abs(box.width / box.height - 4 / 3)).toBeLessThan(0.01)
  await card.focus()
  await expect(card).toHaveCSS('outline-style', 'solid')
  await page.keyboard.press('Enter')
  await expect(page).toHaveURL(/\/picture\/1001$/)
  await expect(page.locator('.detail-page .pic-name')).toHaveText('山间水岸 001')
  await expect(page.locator('.fullscreen-overlay')).toHaveCount(0)
})

test('original hard hover feedback remains and reduce removes movement only', async ({ page }) => {
  await fixture(page)
  await page.goto('/gallery')
  const card = page.locator('.gallery-card').first()
  await card.hover()
  await expect(card).toHaveCSS('transform', 'matrix(1, 0, 0, 1, -2, -2)')
  await expect(card).toHaveCSS('box-shadow', 'rgb(33, 30, 26) 6px 6px 0px 0px')
  await expect(card.locator('img')).toHaveCSS('transform', 'matrix(1.05, 0, 0, 1.05, 0, 0)')
  await page.emulateMedia({ reducedMotion: 'reduce' })
  await expect(card).toHaveCSS('transform', 'none')
  await expect(card.locator('img')).toHaveCSS('transform', 'none')
  await expect(card).toHaveCSS('box-shadow', 'rgb(33, 30, 26) 6px 6px 0px 0px')
})

for (const role of ['guest', 'user', 'admin']) {
  test(`gallery keeps existing ${role} access and upload boundary`, async ({ page }) => {
    await fixture(page, role === 'guest' ? null : { ...member, userRole: role })
    await page.goto('/gallery')
    await expect(page.locator('.gallery-card')).toHaveCount(12)
    await expect(page.locator('.gallery-header').getByRole('button', { name: '+ 上传图片' })).toHaveCount(role === 'admin' ? 1 : 0)
    await expect(page.locator(role === 'guest' ? '.public-layout' : '.app-layout')).toBeVisible()
    await expect(page.locator('.gallery-card').first()).toHaveAttribute('href', '/picture/1001')
  })
}

test('an obsolete deep-link page clamps to the last available page', async ({ page }) => {
  const state = await fixture(page)
  await page.goto('/gallery?q=山间&page=999')
  await expect(page.locator('.pagination')).toContainText('第 5 / 5 页 (60 张)')
  await expect(page.locator('.gallery-card')).toHaveCount(12)
  await expect(page).toHaveURL(url => url.searchParams.get('page') === '5' && url.searchParams.get('q') === '山间')
  expect(state.requests.map(query => query.current)).toEqual([999, 5])
})

test('an empty collection is not an error or an invitation without upload access', async ({ page }) => {
  await fixture(page, null)
  await page.route('**/api/picture/list/page/vo/cache', route => route.fulfill({ json: { code: 0, data: { records: [], total: 0 } } }))
  await page.goto('/gallery')
  const state = page.locator('.gallery-state')
  await expect(state.getByRole('heading', { name: '图库暂时没有图片' })).toBeVisible()
  await expect(state.getByRole('button')).toHaveCount(0)
  await expect(page.locator('.pagination')).toHaveCount(0)
})

test.describe('touch gallery', () => {
  test.use({ hasTouch: true, isMobile: true, viewport: { width: 390, height: 844 } })
  test('a tap opens detail without needing hover to expose information', async ({ page }) => {
    await fixture(page)
    await page.goto('/gallery')
    const card = page.locator('.gallery-card').first()
    await expect(card.locator('.card-meta')).toBeVisible()
    await expect(card.locator('.mini-tag')).toHaveCount(3)
    await card.hover()
    await expect(card).toHaveCSS('transform', 'none')
    await expect(card.locator('img')).toHaveCSS('transform', 'none')
    await card.tap()
    await expect(page).toHaveURL(/\/picture\/1001$/)
  })
})

test('an extreme 1:12 source cannot increase the 4:3 grid row height', async ({ page }) => {
  await fixture(page)
  await page.route('**/images/mosaic/nature.jpg', route => route.fulfill({
    contentType: 'image/svg+xml', body: '<svg xmlns="http://www.w3.org/2000/svg" width="100" height="1200"><rect width="100" height="1200" fill="#80978a"/></svg>'
  }))
  await page.goto('/gallery')
  await expect(page.locator('.card-img').first()).toHaveJSProperty('naturalHeight', 1200)
  const first = await page.locator('.gallery-card').first().boundingBox()
  const second = await page.locator('.gallery-card').nth(1).boundingBox()
  // Equal fractional grid tracks can differ by Chromium's 1/64 px rounding.
  expect(Math.abs(first.height - second.height)).toBeLessThan(0.1)
  expect(Math.abs(first.width / first.height - 4 / 3)).toBeLessThan(0.01)
})

for (const width of [1440, 390, 768, 1920, 320]) {
  test(`equal frames, full metadata and controls at ${width}px`, async ({ page }, testInfo) => {
    await fixture(page)
    await page.setViewportSize({ width, height: width === 1440 ? 900 : width === 1920 ? 1080 : 844 })
    await page.goto('/gallery')
    await expect(page.locator('.gallery-card')).toHaveCount(12)
    await expect(page.locator('.card-img').first()).toBeVisible()
    const metrics = await page.locator('.gallery-page').evaluate(root => {
      /* global document, window, getComputedStyle */
      const cards = Array.from(root.querySelectorAll('.gallery-card'))
      const grid = root.querySelector('.gallery-grid')
      const bounds = cards.map(card => { const box = card.getBoundingClientRect(); return { width: box.width, height: box.height, top: box.top } })
      return {
        viewport: { width: window.innerWidth, height: window.innerHeight },
        overflow: document.documentElement.scrollWidth > window.innerWidth,
        columns: getComputedStyle(grid).gridTemplateColumns.split(' ').length,
        gap: getComputedStyle(grid).gap,
        gridHeight: grid.getBoundingClientRect().height,
        cards: bounds,
        metaSize: getComputedStyle(cards[0].querySelector('.card-meta')).fontSize,
        metaOpacity: getComputedStyle(cards[0].querySelector('.card-meta')).opacity
      }
    })
    expect(metrics.overflow).toBe(false)
    expect(metrics.metaSize).toBe('12px')
    expect(metrics.metaOpacity).toBe('0.85')
    for (const card of metrics.cards) expect(Math.abs(card.width / card.height - 4 / 3)).toBeLessThan(0.01)
    expect(new Set(metrics.cards.map(card => Math.round(card.height))).size).toBe(1)
    if (width <= 480) expect(metrics.columns).toBe(1)
    for (const control of ['.search-shell', '.search-submit', '.filter-select']) {
      expect((await page.locator(control).first().boundingBox()).height).toBe(48)
    }
    await testInfo.attach('layout-metrics', { body: JSON.stringify(metrics, null, 2), contentType: 'application/json' })
    for (const card of await page.locator('.gallery-card').all()) {
      await card.scrollIntoViewIfNeeded()
      await expect(card.locator('img')).not.toHaveJSProperty('naturalWidth', 0)
    }
    await page.evaluate(() => window.scrollTo({ top: 0, behavior: 'instant' }))
    await page.screenshot({ path: testInfo.outputPath(`gallery-${width}-viewport.png`) })
    await page.screenshot({ path: testInfo.outputPath(`gallery-${width}.png`), fullPage: true })
  })
}
