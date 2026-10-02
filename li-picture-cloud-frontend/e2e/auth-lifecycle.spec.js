import { test, expect } from '@playwright/test'

const member = { id: '42', userAccount: 'auth-fixture', userName: '测试用户', userRole: 'user' }

async function fixture(page) {
  const writes = []
  const errors = []
  page.on('pageerror', error => errors.push(error.message))
  await page.route(url => url.pathname.startsWith('/api/'), route => {
    const path = new URL(route.request().url()).pathname
    if (['/api/user/login', '/api/user/register'].includes(path)) writes.push(path)
    const data = path === '/api/user/current' ? null
      : path === '/api/user/login' ? member
        : path === '/api/user/register' ? '43'
          : path.includes('/list/page') ? { records: [], total: 0 }
            : path.includes('tag_category') ? { tagList: [], categoryList: [] } : []
    return route.fulfill({ json: { code: 0, data } })
  })
  return { writes, errors }
}

async function fillForm(page, register = false) {
  await page.getByLabel('账号', { exact: true }).fill('auth-fixture')
  await page.getByLabel('密码', { exact: true }).fill('fixture-only')
  if (register) await page.getByLabel('确认密码', { exact: true }).fill('fixture-only')
}

async function holdRequest(page, path, json) {
  let release
  let acknowledge
  const requested = new Promise(resolve => { acknowledge = resolve })
  const held = new Promise(resolve => { release = resolve })
  await page.route(`**/api/user/${path}`, async route => {
    acknowledge()
    await held
    await route.fulfill({ json })
  })
  return { requested, release }
}

for (const succeeds of [true, false]) {
  test(`late login ${succeeds ? 'success' : 'failure'} respects navigation to registration`, async ({ page }) => {
    const { errors } = await fixture(page)
    const pending = await holdRequest(page, 'login', succeeds
      ? { code: 0, data: member }
      : { code: 40000, message: '旧登录请求失败' })
    await page.goto('/login?redirect=/space/my')
    await fillForm(page)
    await page.getByRole('button', { name: '登录', exact: true }).click()
    await pending.requested
    await page.getByRole('link', { name: /立即注册/ }).click()
    await expect(page).toHaveURL(/\/register$/)
    const response = page.waitForResponse('**/api/user/login')
    pending.release()
    await response
    // Allow the obsolete handler to finish; this is an interrupted UI flow,
    // not a claim that navigation cancels server authentication.
    await expect(page.getByRole('heading', { name: '注册', exact: true })).toBeVisible()
    await page.waitForTimeout(100)
    await expect(page).toHaveURL(/\/register$/)
    await expect(page.locator('.form-error')).toHaveCount(0)
    expect(errors).toEqual([])
  })
}

test('registration completed after leaving never installs a delayed redirect', async ({ page }) => {
  const { errors } = await fixture(page)
  const pending = await holdRequest(page, 'register', { code: 0, data: '43' })
  await page.clock.install()
  await page.clock.pauseAt(new Date())
  await page.goto('/register')
  await fillForm(page, true)
  await page.getByRole('button', { name: '注册', exact: true }).click()
  await pending.requested
  await page.getByRole('link', { name: /去登录/ }).click()
  await expect(page).toHaveURL(/\/login$/)
  // Return to a fresh registration instance before the old request resolves.
  await page.getByRole('link', { name: /立即注册/ }).click()
  const response = page.waitForResponse('**/api/user/register')
  pending.release()
  await response
  await page.waitForTimeout(100)
  await page.clock.runFor(2000)
  await expect(page).toHaveURL(/\/register$/)
  await expect(page.getByLabel('账号', { exact: true })).toHaveValue('')
  await expect(page.locator('.form-success')).toHaveCount(0)
  expect(errors).toEqual([])
})

test('registration countdown is submit-locked and cancelled by Back/Forward navigation', async ({ page }) => {
  const { writes, errors } = await fixture(page)
  await page.clock.install()
  await page.clock.pauseAt(new Date())
  await page.goto('/login')
  await page.getByRole('link', { name: /立即注册/ }).click()
  await fillForm(page, true)
  await page.getByRole('button', { name: '注册', exact: true }).click()
  await expect(page.locator('.form-success')).toBeVisible()
  await expect(page.locator('button[type="submit"]')).toBeDisabled()
  await page.getByLabel('确认密码', { exact: true }).press('Enter')
  await page.goBack()
  await expect(page).toHaveURL(/\/login$/)
  await page.goForward()
  await expect(page).toHaveURL(/\/register$/)
  await page.clock.runFor(2000)
  await expect(page).toHaveURL(/\/register$/)
  expect(writes).toEqual(['/api/user/register'])
  expect(errors).toEqual([])
})

test('ordinary registration still navigates once after the existing delay', async ({ page }) => {
  const { writes, errors } = await fixture(page)
  await page.clock.install()
  await page.clock.pauseAt(new Date())
  await page.goto('/register')
  await fillForm(page, true)
  await page.getByRole('button', { name: '注册', exact: true }).click()
  await expect(page.locator('.form-success')).toBeVisible()
  await page.clock.runFor(1499)
  await expect(page).toHaveURL(/\/register$/)
  await page.clock.runFor(1)
  await expect(page).toHaveURL(/\/login$/)
  expect(writes).toEqual(['/api/user/register'])
  expect(errors).toEqual([])
})
