import { test, expect } from '@playwright/test'

// This check must run against a production build, never Vite's DEV override.
test.skip(process.env.R05_DISABLED_BUILD !== 'true', 'Use e2e/config/companion-disabled.config.js for the production-off check')
test('production feature-off has no Companion route, images, player or API requests', async ({ page }) => {
  const companionRequests = []
  await page.route(url => url.pathname.startsWith('/api/'), route => {
    const path = new URL(route.request().url()).pathname
    const data = path === '/api/user/current'
      ? { id: '42', userAccount: 'disabled-fixture', userRole: 'user' }
      : path === '/api/picture/get/vo' ? { id: '11', name: 'Feature-off picture', userId: '42', url: '/favicon.ico' }
      : path.includes('tag_category') ? { tagList: [], categoryList: [] } : { records: [], total: 0 }
    return route.fulfill({ json: { code: 0, data } })
  })
  page.on('request', request => {
    if (/adult-.*\.webp|\/api\/companion\//.test(request.url())) companionRequests.push(request.url())
  })
  await page.goto('/')
  const companionSection = page.locator('[aria-labelledby="companion-title"]')
  await expect(companionSection.getByText('伙伴功能暂未开放。你可以先使用图片和空间功能。')).toBeVisible()
  await expect(companionSection.getByRole('link', { name: /伙伴/ })).toHaveCount(0)
  await page.goto('/space/my')
  await expect(page.locator('.app-layout')).toBeVisible()
  await expect(page.locator('a[href="/companion"]')).toHaveCount(0)
  await expect(page.locator('.desktop-navigation').getByRole('button', { name: '工具' })).toHaveCount(0)
  await expect(page.locator('.sidebar .companion-presence')).toHaveCount(0)
  await expect(page.getByRole('button', { name: '和绫页互动' })).toHaveCount(0)
  await page.goto('/picture/11')
  await expect(page.getByRole('heading', { name: 'Feature-off picture' })).toBeVisible()
  await expect(page.getByRole('button', { name: '选给绫页' })).toHaveCount(0)
  for (const path of ['/companion', '/model-gateway', '/recipes', '/admin/companion-feed-runs']) {
    await page.goto(path)
    await expect(page.getByRole('heading', { name: '页面不可用' })).toBeVisible()
    await expect(page.locator('.companion-page, .sprite-player, .companion-presence')).toHaveCount(0)
  }
  await page.goto('/gallery')
  await expect(page.locator('.gallery-page')).toBeVisible()
  expect(companionRequests).toEqual([])
})
