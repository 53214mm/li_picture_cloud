import { test, expect } from '@playwright/test'

test('awakens a companion and recovers one private-picture feed without double growth', async ({ page }) => {
  const login = await page.request.post('/api/user/login', {
    data: { userAccount: 'companion_e2e', userPassword: 'LocalUser123!' }
  })
  expect(login.ok(), `login failed: ${login.status()} ${await login.text()}`).toBeTruthy()

  await page.goto('/companion')
  await expect(page).toHaveURL(/\/companion$/)
  await expect(page.getByText('演示营养（确定性）')).toBeVisible()
  await expect(page.getByText('未读取图片内容，也未调用视觉模型')).toBeVisible()
  await page.getByRole('button', { name: '唤醒我的伙伴' }).click()
  await expect(page.getByText('光点', { exact: true })).toBeVisible()

  await page.getByRole('button', { name: /旅行样片/ }).click()
  const keys = []
  let loseFirstResponse = true
  await page.route('**/api/companion/feed', async route => {
    const payload = route.request().postDataJSON()
    keys.push(payload.idempotencyKey)
    if (loseFirstResponse) {
      loseFirstResponse = false
      const backendResponse = await route.fetch()
      expect(backendResponse.ok()).toBeTruthy()
      await route.abort('failed')
      return
    }
    await route.continue()
  })

  await page.getByRole('button', { name: '喂给伙伴' }).evaluate(button => {
    button.click()
    button.click()
  })
  await expect(page.getByRole('button', { name: '重试这次喂养' })).toBeVisible()
  expect(keys).toHaveLength(1)
  await page.getByRole('button', { name: '重试这次喂养' }).click()

  await expect(page.getByText('这次喂养已安全完成，没有重复成长。')).toBeVisible()
  expect(keys).toHaveLength(2)
  expect(keys[1]).toBe(keys[0])
  await expect(page.getByText('42 / 100 生命经验')).toBeVisible()
  await expect(page.getByText('+42 生命经验')).toBeVisible()
  await expect(page.getByTestId('growth-trait-curiosity').first()).toContainText('+0.60')
  await expect(page.getByTestId('skill-IMAGE_OBSERVATION')).toContainText('18 / 100')
  await expect(page.getByTestId('skill-STORY_CREATION')).toContainText('12 / 100')
  await expect(page.getByRole('link', { name: '图片 #102' }).first())
    .toHaveAttribute('href', '/picture/102')
  await expect(page.getByText('未进行内容理解').first()).toBeVisible()

  const homeResponse = await page.request.get('/api/companion/me')
  expect(homeResponse.ok()).toBeTruthy()
  const homeBody = await homeResponse.json()
  expect(homeBody.data.companion.lifeExperience).toBe('42')
  expect(homeBody.data.companion.revision).toBe('1')
  expect(homeBody.data.recentGrowth).toHaveLength(1)

  await page.reload()
  await expect(page.getByText('42 / 100 生命经验')).toBeVisible()
  await expect(page.getByText('+42 生命经验')).toBeVisible()
  await expect(page.getByTestId('skill-IMAGE_OBSERVATION')).toContainText('18 / 100')
  await expect(page.getByRole('link', { name: '图片 #102' }).first())
    .toHaveAttribute('href', '/picture/102')
  await expect(page.getByText('未读取图片内容，也未调用视觉模型')).toBeVisible()

  const sourceResponse = await page.request.get('/api/picture/get/vo?id=102')
  expect(sourceResponse.ok()).toBeTruthy()
  const sourceBody = await sourceResponse.json()
  expect(sourceBody.data).toMatchObject({
    id: '102',
    name: '旅行样片',
    url: '/images/mosaic/travel.jpg',
    originalUrl: '/images/mosaic/travel.jpg',
    spaceId: '10'
  })
})
