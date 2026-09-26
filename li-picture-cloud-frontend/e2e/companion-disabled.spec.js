import { test, expect } from '@playwright/test'

// This check must run against a production build, never Vite's DEV override.
test.skip(process.env.R05_DISABLED_BUILD !== 'true', 'Use e2e/config/companion-disabled.config.js for the production-off check')
test('production feature-off has no Companion route, images, player or API requests', async ({ page }) => {
  const companionRequests = []
  await page.route(url => url.pathname.startsWith('/api/'), route => {
    const path = new URL(route.request().url()).pathname
    const data = path === '/api/user/current'
      ? { id: '42', userAccount: 'disabled-fixture', userRole: 'user' }
      : path.includes('tag_category') ? { tagList: [], categoryList: [] } : { records: [], total: 0 }
    return route.fulfill({ json: { code: 0, data } })
  })
  page.on('request', request => {
    if (/adult-.*\.webp|\/api\/companion\//.test(request.url())) companionRequests.push(request.url())
  })
  await page.goto('/space/my')
  await expect(page.locator('.app-layout')).toBeVisible()
  await expect(page.locator('a[href="/companion"]')).toHaveCount(0)
  await page.goto('/companion')
  await expect(page.locator('.companion-page, .sprite-player, .companion-presence')).toHaveCount(0)
  await page.goto('/gallery')
  await expect(page.locator('.gallery-page')).toBeVisible()
  expect(companionRequests).toEqual([])
})
