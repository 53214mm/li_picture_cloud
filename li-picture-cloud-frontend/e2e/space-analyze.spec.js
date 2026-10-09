import { test, expect } from '@playwright/test'

test('real space statistics mount charts and remain interactive across scope and period changes', async ({ page }, testInfo) => {
  const errors = []
  page.on('pageerror', error => errors.push(error.message))
  const login = await page.request.post('/api/user/login', {
    data: { userAccount: 'companion_e2e', userPassword: 'LocalUser123!' }
  })
  expect(login.ok()).toBe(true)
  expect((await login.json()).code).toBe(0)

  const initialCategory = page.waitForResponse(response => response.url().endsWith('/api/space/analyze/category'))
  await page.goto('/space/analyze')
  const category = await (await initialCategory).json()
  expect(category.code).toBe(0)
  expect(category.data.length).toBeGreaterThan(0)
  expect(category.data.every(item => item.count > 0 && item.totalSize > 0)).toBe(true)
  await expect(page.getByRole('heading', { name: '图库分析', exact: true })).toBeVisible()
  await expect(page.locator('.chart-box canvas')).toHaveCount(4)
  await expect(page.getByRole('button', { name: '全部空间', exact: true })).toHaveCount(0)
  for (const [dimension, label] of [['week', '周'], ['month', '月']]) {
    const response = page.waitForResponse(response => response.url().endsWith('/api/space/analyze/user')
      && response.request().postDataJSON()?.timeDimension === dimension)
    await page.getByRole('button', { name: label, exact: true }).click()
    expect((await (await response).json()).code).toBe(0)
    await expect(page.getByRole('heading', { name: `上传趋势（按${label}）` })).toBeVisible()
    await expect(page.locator('.chart-box canvas')).toHaveCount(4)
  }

  const publicRead = page.waitForResponse(response => response.url().endsWith('/api/space/analyze/category')
    && response.request().postDataJSON()?.queryPublic === true)
  await page.getByRole('button', { name: '公共图库', exact: true }).click()
  expect((await (await publicRead).json()).code).toBe(0)
  await expect(page.getByRole('button', { name: '公共图库', exact: true })).toHaveClass('active')
  await page.getByRole('button', { name: '我的空间', exact: true }).click()
  await expect(page.locator('.chart-box canvas')).toHaveCount(4)
  await page.reload()
  await expect(page.locator('.chart-box canvas')).toHaveCount(4)
  // Functional assertions above wait on API/DOM state. This delay is solely for
  // the visual artifact: ECharts' finite enter animation otherwise shows frame 1.
  await page.waitForTimeout(1500)
  await page.screenshot({ path: testInfo.outputPath('analysis-api-desktop.png'), fullPage: true })
  expect(errors).toEqual([])
})
