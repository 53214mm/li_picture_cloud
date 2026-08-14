import { test, expect } from '@playwright/test'

// 表情草稿纵向切片：选图 → 生成候选 → 选中 → 保存（E2E 语言 stub 不发外网）。
test.describe.configure({ mode: 'serial' })

async function login(page) {
  const response = await page.request.post('/api/user/login', {
    data: { userAccount: 'companion_e2e', userPassword: 'LocalUser123!' }
  })
  expect(response.ok(), `login failed: ${response.status()} ${await response.text()}`).toBeTruthy()
}

test('creates emoji candidates from an authorized picture and saves the picked one', async ({ page }) => {
  await login(page)
  await page.goto('/companion')
  await expect(page).toHaveURL(/\/companion$/)
  const awaken = page.getByRole('button', { name: '唤醒我的伙伴' })
  if (await awaken.isVisible()) {
    await awaken.click()
    await expect(page.getByText('光点', { exact: true })).toBeVisible()
  }
  await expect(page.getByText('让伙伴从图片里挑一句俏皮话')).toBeVisible()
  await expect(page.getByText('还没有表情草稿。选一张图片，生成第一批候选吧。')).toBeVisible()

  // 单选授权图片并开始生成（幂等键重试沿用）。
  await page.locator('.emoji-picture-choice').filter({ hasText: '旅行样片' })
    .locator('input').check()
  await page.getByRole('button', { name: '开始生成表情' }).click()

  const emojiList = page.getByTestId('emoji-list')
  await expect(emojiList.getByText('待开始')).toBeVisible()
  await emojiList.getByRole('button', { name: '生成候选' }).click()
  await expect(emojiList.getByText('等待确认')).toBeVisible()

  // 候选来自 E2E 语言 stub；选中第一条。
  const candidate = emojiList.locator('.emoji-candidate').first()
  await expect(candidate).toContainText('在晨光里，伙伴轻轻翻开了这些画面')
  await candidate.locator('input').check()
  await emojiList.getByRole('button', { name: '选中保存' }).click()
  await expect(emojiList.getByRole('button', { name: '保存作品' })).toBeVisible()

  // 保存作品 → SAVED 终态，作品文本可见。
  await emojiList.getByRole('button', { name: '保存作品' }).click()
  await expect(emojiList.getByText('已保存')).toBeVisible()
  await expect(emojiList.getByTestId('emoji-result')).toContainText('在晨光里，伙伴轻轻翻开了这些画面')

  // 刷新后保持已保存状态。
  await page.reload()
  await expect(page.getByTestId('emoji-list').getByText('已保存')).toBeVisible()

  const listResponse = await page.request.get('/api/creation/emoji')
  expect(listResponse.ok()).toBeTruthy()
  const body = await listResponse.json()
  expect(body.data).toHaveLength(1)
  expect(body.data[0].status).toBe('SAVED')
  expect(body.data[0].kind).toBe('EMOJI_DRAFT')
})
