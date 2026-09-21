import { test, expect } from '@playwright/test'

const member = { id: '42', userAccount: 'shell-test', userName: '测试用户', userRole: 'user' }
const runtimeErrors = new WeakMap()
test.afterEach(async ({ page }) => { expect(runtimeErrors.get(page) || []).toEqual([]) })
async function fixture(page, { user = member, authStatus = 200 } = {}) {
  const requests = []
  const errors = []
  runtimeErrors.set(page, errors)
  page.on('pageerror', error => errors.push(error.message))
  await page.route(url => url.pathname.startsWith('/api/'), async route => {
    const path = new URL(route.request().url()).pathname
    requests.push(path)
    let data = []
    if (path === '/api/user/current') return route.fulfill({ status: authStatus, json: { code: authStatus === 200 ? 0 : 50000, data: user, message: '连接失败' } })
    if (path === '/api/user/login') data = member
    else if (path.includes('/list/page')) data = { records: [], total: 0 }
    else if (path.includes('tag_category')) data = { tagList: [], categoryList: [] }
    return route.fulfill({ json: { code: 0, data } })
  })
  return requests
}
async function expectNoHorizontalOverflow(page) {
  expect(await page.evaluate(() => {
    // eslint-disable-next-line no-undef
    return document.documentElement.scrollWidth <= window.innerWidth
  })).toBe(true)
}

test('protected refresh waits for authentication before mounting legacy workspace', async ({ page }) => {
  const requests = await fixture(page)
  let release
  const held = new Promise(resolve => { release = resolve })
  await page.route('**/api/user/current', async route => { await held; await route.fulfill({ json: { code: 0, data: member } }) })
  await page.goto('/space/my')
  await expect(page.getByText('正在确认登录状态')).toBeVisible()
  await expect(page.locator('.my-space-page')).toHaveCount(0)
  expect(requests.some(path => path.startsWith('/api/space'))).toBe(false)
  release()
  await expect(page.locator('.app-layout')).toBeVisible()
  await expect(page.getByRole('heading', { name: '我的空间', exact: true })).toBeVisible()
  await expect(page).toHaveURL(/\/space\/my$/)
})

test('bootstrap network failure is retryable and cannot mount a protected page', async ({ page }) => {
  const requests = await fixture(page, { authStatus: 503 })
  await page.goto('/space/my')
  await expect(page.getByText('无法确认登录状态')).toBeVisible()
  expect(requests.some(path => path.startsWith('/api/space'))).toBe(false)
  await expect(page).toHaveURL(/\/space\/my$/)
  await page.route('**/api/user/current', route => route.fulfill({ json: { code: 0, data: member } }))
  await page.getByRole('button', { name: '重试', exact: true }).click()
  await expect(page.locator('.my-space-page')).toBeVisible()
})

test('guest deep link returns to its original local URL after login', async ({ page }) => {
  await fixture(page, { user: null })
  await page.goto('/upload?spaceId=123')
  await expect(page).toHaveURL(/\/login\?redirect=/)
  await page.getByLabel('账号', { exact: true }).fill('shell-test')
  await page.getByLabel('密码', { exact: true }).fill('test-only')
  await page.getByRole('button', { name: '登录', exact: true }).click()
  await expect(page).toHaveURL(/\/upload\?spaceId=123$/)
  await expect(page.locator('.app-layout')).toBeVisible()
})

for (const url of ['/login', '/login?redirect=https://example.com']) {
  test(`login defaults safely to my space from ${url}`, async ({ page }) => {
    await fixture(page, { user: null })
    await page.goto(url)
    await page.getByLabel('账号', { exact: true }).fill('shell-test')
    await page.getByLabel('密码', { exact: true }).fill('test-only')
    await page.getByRole('button', { name: '登录', exact: true }).click()
    await expect(page).toHaveURL(/\/space\/my$/)
  })
}

test('regular user cannot mount admin page and has no management links', async ({ page }) => {
  const requests = await fixture(page)
  await page.goto('/admin/users')
  await expect(page.getByText('无权访问此页面')).toBeVisible()
  expect(requests.some(path => path === '/api/user/list/page')).toBe(false)
  await expect(page.getByRole('link', { name: '用户管理', exact: true })).toHaveCount(0)
  await expect(page).toHaveURL(/\/admin\/users$/)
})

test('public landing stays public; gallery is adaptive; account logout clears shell', async ({ page }) => {
  await fixture(page)
  await page.goto('/')
  await expect(page.locator('.public-layout')).toBeVisible()
  await page.getByRole('link', { name: '进入空间' }).click()
  await expect(page.locator('.app-layout')).toBeVisible()
  await page.locator('.desktop-navigation').getByRole('link', { name: '图库', exact: true }).click()
  await expect(page.locator('.app-main.workspace--fluid.frame--legacy')).toBeVisible()
  await page.getByRole('button', { name: '打开账户菜单' }).click()
  await page.getByRole('button', { name: '退出登录', exact: true }).click()
  await expect(page).toHaveURL(/\/login$/)
  await expect(page.locator('.app-layout')).toHaveCount(0)
  await page.goto('/gallery')
  // Reload fixture represents the authenticated session; guest behavior below uses a fresh current-user response.
  await page.route('**/api/user/current', route => route.fulfill({ json: { code: 0, data: null } }))
  await page.reload()
  await expect(page.locator('.public-layout .gallery-page')).toBeVisible()
})

test('scheme A widths and mobile drawer keyboard isolation, route close and scroll restoration', async ({ page }, testInfo) => {
  await fixture(page)
  await page.setViewportSize({ width: 1440, height: 900 })
  await page.goto('/space/my')
  await expect(page.locator('.sidebar')).toHaveCSS('width', '224px')
  await expect(page.locator('.app-main')).toHaveCSS('padding-left', '0px')
  await expectNoHorizontalOverflow(page)
  await page.screenshot({ path: testInfo.outputPath('desktop.png'), fullPage: true })
  await page.setViewportSize({ width: 768, height: 900 })
  await expect(page.locator('.sidebar')).toHaveCSS('width', '88px')
  await expectNoHorizontalOverflow(page)
  await page.screenshot({ path: testInfo.outputPath('tablet.png'), fullPage: true })
  await page.setViewportSize({ width: 390, height: 844 })
  await expect(page.locator('.sidebar')).not.toBeVisible()
  await expect(page.getByRole('navigation', { name: '快捷导航' })).toBeVisible()
  await expectNoHorizontalOverflow(page)
  await page.screenshot({ path: testInfo.outputPath('mobile.png'), fullPage: true })
  const opener = page.getByRole('button', { name: '打开导航菜单' })
  await opener.click()
  const dialog = page.getByRole('dialog', { name: '导航' })
  await expect(dialog).toBeVisible()
  await expect(dialog.getByRole('button', { name: '关闭菜单' })).toBeFocused()
  await expect(page.locator('body')).toHaveCSS('overflow', 'hidden')
  await page.keyboard.press('Shift+Tab')
  await expect(dialog.getByRole('link', { name: '图库搜索' })).toBeFocused()
  await page.keyboard.press('Tab')
  await expect(dialog.getByRole('button', { name: '关闭菜单' })).toBeFocused()
  // Native top-layer modal makes otherwise focusable background controls inert.
  await page.locator('.upload-link').evaluate(el => el.focus())
  await expect(dialog.getByRole('button', { name: '关闭菜单' })).toBeFocused()
  await page.keyboard.press('Escape')
  await expect(dialog).not.toBeVisible()
  await expect(opener).toBeFocused()
  await expect(page.locator('body')).not.toHaveCSS('overflow', 'hidden')
  await opener.click()
  await dialog.getByRole('link', { name: '图库', exact: true }).click()
  await expect(dialog).not.toBeVisible()
  await expect(page.locator('body')).not.toHaveCSS('overflow', 'hidden')
  await expect(page).toHaveURL(/\/gallery$/)
  await opener.click()
  await dialog.getByRole('link', { name: '图库', exact: true }).click()
  await expect(dialog).not.toBeVisible()
  await page.getByRole('navigation', { name: '快捷导航' }).getByRole('button', { name: '更多' }).click()
  await page.setViewportSize({ width: 800, height: 900 })
  await expect(dialog).not.toBeVisible()
  await expect(opener).toBeFocused()
  await opener.click()
  await page.setViewportSize({ width: 1024, height: 900 })
  await expect(dialog).not.toBeVisible()
  await expect(page.locator('.sidebar .brand')).toBeFocused()
  for (const width of [480, 320]) {
    await page.setViewportSize({ width, height: 844 })
    await expectNoHorizontalOverflow(page)
  }
})

test('existing page modal isolates shell chrome without making its own controls inert', async ({ page }) => {
  await fixture(page)
  await page.route('**/api/space/list/page/vo', route => route.fulfill({ json: { code: 0, data: {
    records: [{ id: '1', userId: '42', spaceName: '测试空间', spaceLevel: 0, spaceType: 0, totalCount: 0, maxCount: 100, totalSize: 0, maxSize: 1024 }], total: 1
  } } }))
  await page.goto('/spaces')
  await page.getByRole('button', { name: '编辑', exact: true }).click()
  const input = page.locator('.modal-overlay input')
  await input.focus()
  for (const selector of ['.sidebar', '.app-toolbar', '.bottom-navigation']) {
    await expect(page.locator(selector)).toHaveAttribute('inert', '')
  }
  await page.locator('.upload-link').evaluate(el => el.focus())
  await expect(input).toBeFocused()
  await page.locator('.modal-overlay').getByRole('button', { name: '取消', exact: true }).click()
  await expect(page.locator('.app-toolbar')).not.toHaveAttribute('inert')
  await page.locator('.upload-link').focus()
  await expect(page.locator('.upload-link')).toBeFocused()
})

test('a retry that establishes a guest session goes to login with the deep link', async ({ page }) => {
  await fixture(page, { authStatus: 503 })
  await page.goto('/space/my')
  await expect(page.getByText('无法确认登录状态')).toBeVisible()
  await page.route('**/api/user/current', route => route.fulfill({ status: 401, json: { code: 40100 } }))
  await page.getByRole('button', { name: '重试', exact: true }).click()
  await expect(page).toHaveURL(url => url.pathname === '/login' && url.searchParams.get('redirect') === '/space/my')
})

test('navigation disclosures align with primary entries, animate and remain keyboard-safe', async ({ page }, testInfo) => {
  await fixture(page, { user: { ...member, userRole: 'admin' } })
  await page.setViewportSize({ width: 1440, height: 900 })
  await page.goto('/space/my')
  const nav = page.locator('.desktop-navigation')
  const toggle = nav.getByRole('button', { name: '管理', exact: true })
  const controlled = await toggle.getAttribute('aria-controls')
  const panel = page.locator(`[id="${controlled}"]`)
  await expect(toggle).toHaveAttribute('aria-expanded', 'false')
  await expect(panel).toHaveAttribute('inert', '')
  const primaryIcon = await nav.getByRole('link', { name: '图库', exact: true }).locator('svg').boundingBox()
  const groupIcon = await toggle.locator('svg').first().boundingBox()
  expect(groupIcon.x).toBe(primaryIcon.x)
  await toggle.focus()
  await page.keyboard.press('Enter')
  await expect(toggle).toHaveAttribute('aria-expanded', 'true')
  await expect(panel).toHaveCSS('opacity', '1')
  await expect(panel).not.toHaveAttribute('inert')
  await expect(panel).toHaveCSS('transition-duration', '0.24s, 0.16s, 0s')
  await panel.getByRole('link', { name: '用户管理' }).click()
  await expect(page).toHaveURL(/\/admin\/users$/)
  await expect(toggle).toHaveAttribute('aria-expanded', 'true')
  await toggle.click()
  await expect(panel).toHaveAttribute('inert', '')
  await expect(panel).toHaveCSS('visibility', 'hidden')
  await panel.locator('a').first().evaluate(el => el.focus())
  await expect(toggle).toBeFocused()
  await page.emulateMedia({ reducedMotion: 'reduce' })
  await toggle.click()
  // R01's global reduced-motion fallback may retain a tiny duration; no property animates.
  await expect(panel).toHaveCSS('transition-property', 'none')
  await expect(panel).toHaveCSS('opacity', '1')
  await page.screenshot({ path: testInfo.outputPath('navigation-polish.png'), fullPage: true })
})

test('feature flags hide tools and companion together, disabled deep links are unavailable', async ({ page }, testInfo) => {
  await fixture(page, { user: { ...member, userRole: 'admin' } })
  await page.setViewportSize({ width: 1440, height: 900 })
  await page.goto('/space/my')
  const nav = page.locator('.desktop-navigation')
  await expect(nav.getByText('管理', { exact: true })).toBeVisible()
  if (testInfo.project.name === 'production-default') {
    await expect(nav.getByText('工具', { exact: true })).toHaveCount(0)
    await expect(page.locator('.sidebar .presence')).toHaveCount(0)
    for (const path of ['/companion', '/model-gateway', '/recipes', '/admin/companion-feed-runs']) {
      await page.goto(path)
      await expect(page.getByRole('heading', { name: '页面不可用' })).toBeVisible()
    }
  } else {
    await expect(page.locator('.sidebar .presence')).toBeVisible()
    await nav.getByText('工具', { exact: true }).click()
    await nav.getByRole('link', { name: '模型连接', exact: true }).click()
    await expect(page.locator('.gateway-hero h1')).toBeVisible()
    const colors = await page.locator('.gateway-hero h1').evaluate(el => ({
      // eslint-disable-next-line no-undef
      heading: getComputedStyle(el).color,
      // eslint-disable-next-line no-undef
      container: getComputedStyle(el.closest('.gateway-hero')).color
    }))
    expect(colors.heading).toBe(colors.container)
    await page.screenshot({ path: testInfo.outputPath('gateway.png'), fullPage: true })
  }
})
