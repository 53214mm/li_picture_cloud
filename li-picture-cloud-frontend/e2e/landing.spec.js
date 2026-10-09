import { test, expect } from '@playwright/test'

const member = { id: '42', userAccount: 'landing-test', userName: '测试用户', userRole: 'user' }
const runtimeErrors = new WeakMap()

test.afterEach(async ({ page }) => {
  expect(runtimeErrors.get(page) || []).toEqual([])
})

async function fixture(page, user) {
  const errors = []
  runtimeErrors.set(page, errors)
  page.on('pageerror', error => errors.push(error.message))
  await page.route(url => url.pathname.startsWith('/api/'), route => {
    const path = new URL(route.request().url()).pathname
    const data = path === '/api/user/current' ? user : []
    return route.fulfill({ json: { code: 0, data } })
  })
}

async function expectNoHorizontalOverflow(page) {
  expect(await page.evaluate(() => {
    // eslint-disable-next-line no-undef
    return document.documentElement.scrollWidth <= window.innerWidth
  })).toBe(true)
}

async function expectCompanionEntry(page, testInfo, name, href) {
  const section = page.locator('[aria-labelledby="companion-title"]')
  if (testInfo.project.name === 'production-default') {
    await expect(section.getByRole('link')).toHaveCount(0)
    await expect(section.getByText('伙伴功能暂未开放。你可以先使用图片和空间功能。')).toBeVisible()
  } else await expect(section.getByRole('link', { name })).toHaveAttribute('href', href)
}

test('guest landing uses guest destinations and respects the companion build flag', async ({ page }, testInfo) => {
  await fixture(page, null)
  await page.goto('/')

  const hero = page.locator('.landing-hero')
  await expect(hero.getByRole('heading', { name: '管理图片， 也用图片培养伙伴。' })).toBeVisible()
  await expect(hero.getByRole('link', { name: '注册', exact: true })).toHaveAttribute('href', '/register')
  await expect(hero.getByRole('link', { name: '登录', exact: true })).toHaveAttribute('href', '/login')

  await expectCompanionEntry(page, testInfo, '登录后查看伙伴 →', '/login?redirect=/companion')
})

test('member landing leads to existing space and respects the companion build flag', async ({ page }, testInfo) => {
  await fixture(page, member)
  await page.goto('/')

  const hero = page.locator('.landing-hero')
  await expect(hero.getByRole('link', { name: '进入空间', exact: true })).toHaveAttribute('href', '/space/my')
  await expect(hero.getByRole('link', { name: '图库', exact: true })).toHaveAttribute('href', '/gallery')
  await expect(hero.getByRole('link', { name: '注册', exact: true })).toHaveCount(0)

  await expectCompanionEntry(page, testInfo, '进入伙伴 →', '/companion')
})

for (const viewport of [
  { width: 1440, height: 900 },
  { width: 768, height: 1024 },
  { width: 390, height: 844 },
  { width: 320, height: 700 },
  { width: 844, height: 390 }
]) {
  test(`landing remains image-first and usable at ${viewport.width}x${viewport.height}`, async ({ page }, testInfo) => {
    await fixture(page, null)
    await page.setViewportSize(viewport)
    await page.goto('/')

    await expectNoHorizontalOverflow(page)
    await expect(page.locator('.landing-habitat')).toBeVisible()
    await expect(page.locator('.landing-canvas__photo--main img')).toBeVisible()
    expect(await page.locator('main img').evaluateAll(images => images.every(image => image.complete && image.naturalWidth > 0))).toBe(true)

    const primary = page.locator('.landing-hero .landing-action--primary')
    expect((await primary.boundingBox())?.height).toBeGreaterThanOrEqual(44)

    if (viewport.width === 390) {
      await expect(page.locator('.landing-canvas__photo--city')).toBeHidden()
      await page.screenshot({ path: testInfo.outputPath('landing-mobile.png'), fullPage: true })
    }
    if (viewport.width === 1440) {
      await page.screenshot({ path: testInfo.outputPath('landing-desktop.png'), fullPage: true })
    }
  })
}
