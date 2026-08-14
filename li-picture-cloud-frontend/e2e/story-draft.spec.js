import { test, expect } from '@playwright/test'

// 图片故事纵向切片：选图 → 大纲 → 草稿 → 保存（E2E 语言 stub 返回固定文本，不发外网）。
test.describe.configure({ mode: 'serial' })

async function login(page) {
  const response = await page.request.post('/api/user/login', {
    data: { userAccount: 'companion_e2e', userPassword: 'LocalUser123!' }
  })
  expect(response.ok(), `login failed: ${response.status()} ${await response.text()}`).toBeTruthy()
}

test('creates a story draft from an authorized picture through outline draft and save', async ({ page }) => {
  await login(page)
  await page.goto('/companion')
  await expect(page).toHaveURL(/\/companion$/)
  // 故事面板随伙伴存在而出现；必要时先唤醒。
  const awaken = page.getByRole('button', { name: '唤醒我的伙伴' })
  if (await awaken.isVisible()) {
    await awaken.click()
    await expect(page.getByText('光点', { exact: true })).toBeVisible()
  }
  await expect(page.getByText('让伙伴为图片写一段故事')).toBeVisible()
  await expect(page.getByText('还没有故事。选好图片，开始第一次创作吧。')).toBeVisible()

  // 选择授权图片并开始创作（幂等键由前端生成，服务端按唯一键去重）。
  await page.locator('.story-picture-choice').filter({ hasText: '旅行样片' })
    .locator('input').check()
  await page.getByRole('button', { name: '开始创作故事' }).click()

  // PENDING → 生成大纲 → 等待确认，展示固定 stub 大纲文本。
  const storyList = page.getByTestId('story-list')
  await expect(storyList.getByText('待开始')).toBeVisible()
  await page.getByRole('button', { name: '生成大纲' }).click()
  await expect(page.getByText('等待确认')).toBeVisible()
  await expect(page.getByText('在晨光里，伙伴轻轻翻开了这些画面，把安静的清晨讲成了一段小小的故事。'))
    .toBeVisible()

  // 生成草稿 → 等待确认，展示草稿。
  await page.getByRole('button', { name: '生成草稿' }).click()
  await expect(page.getByRole('button', { name: '保存作品' })).toBeVisible()

  // 保存作品 → SAVED 终态，作品文本可见。
  await page.getByRole('button', { name: '保存作品' }).click()
  await expect(page.getByText('已保存')).toBeVisible()
  await expect(page.getByTestId('story-result')).toContainText('在晨光里，伙伴轻轻翻开了这些画面')

  // 刷新后保持已保存状态。
  await page.reload()
  await expect(page.getByText('已保存')).toBeVisible()

  // 血缘只含安全字段：接口返回幂等键而不是任何模型原文或密钥。
  const listResponse = await page.request.get('/api/creation/story')
  expect(listResponse.ok()).toBeTruthy()
  const body = await listResponse.json()
  expect(body.data).toHaveLength(1)
  expect(body.data[0].status).toBe('SAVED')
  expect(body.data[0].sourcePictureIds).toContain('102')
})
