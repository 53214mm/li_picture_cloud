import { test, expect } from '@playwright/test'

// 同一后端进程内的串行故事线：凭据 → 连接 → 探测 → 路由 → 轮换 → 清理。
test.describe.configure({ mode: 'serial' })

async function login(page) {
  const response = await page.request.post('/api/user/login', {
    data: { userAccount: 'companion_e2e', userPassword: 'LocalUser123!' }
  })
  expect(response.ok(), `login failed: ${response.status()} ${await response.text()}`).toBeTruthy()
}

test('control center manages credentials connections routing and usage', async ({ page }) => {
  await login(page)
  await page.goto('/model-gateway')
  await expect(page).toHaveURL(/\/model-gateway$/)
  await expect(page.getByText('你的模型连接，由你决定')).toBeVisible()
  await expect(page.getByText('还没有保存凭据。添加后，连接才能绑定它。')).toBeVisible()
  // 普通用户看不到平台管理的 MCP 白名单区。
  await expect(page.getByTestId('mcp-section')).toHaveCount(0)

  // 1. 保存凭据：明文只提交一次，界面只回显尾号。
  await page.getByLabel('供应商').first().selectOption('DEEPSEEK')
  await page.getByPlaceholder('sk-…').fill('sk-e2e-test-key-1234')
  await page.getByRole('button', { name: '保存凭据' }).click()
  await expect(page.getByTestId('credential-list').getByText('尾号 1234')).toBeVisible()

  // 2. 添加连接并绑定凭据。
  await page.getByLabel('供应商').nth(1).selectOption('DEEPSEEK')
  await page.getByLabel('连接名称').fill('E2E 主力')
  await page.getByLabel('端点（仅 HTTPS）').fill('https://api.deepseek.com/v1')
  await page.getByLabel('模型编码').fill('deepseek-chat')
  await page.getByLabel('绑定凭据（可选）').selectOption({ label: 'DeepSeek · 尾号 1234' })
  await page.getByRole('button', { name: '添加连接' }).click()
  const connectionList = page.getByTestId('connection-list')
  await expect(connectionList.getByText('E2E 主力')).toBeVisible()
  await expect(connectionList.getByText(/已停用/)).toBeVisible()

  // 3. 启用后测试连接：E2E 后端使用固定成功 stub，不发真实外网请求。
  await page.getByRole('button', { name: '启用' }).click()
  await expect(connectionList.getByText(/已启用/)).toBeVisible()
  await page.getByRole('button', { name: '测试连接' }).click()
  await expect(page.getByText('探测通过')).toBeVisible()
  // 探测成功写能力画像：DeepSeek 语言模型只展示明确支持的能力，视觉按不支持处理不展示。
  await expect(page.getByTestId('capability-chips')).toContainText('文本')
  await expect(page.getByTestId('capability-chips')).toContainText('工具调用')
  await expect(page.getByTestId('capability-chips')).toContainText('结构化输出')
  await expect(page.getByTestId('capability-chips')).toContainText('上下文 64000')
  await expect(page.getByTestId('capability-chips')).not.toContainText('视觉理解')
  // 探测写一条使用记录，只含安全字段。
  const usage = page.getByTestId('usage-table')
  await expect(usage).toBeVisible()
  await expect(usage.getByText('CONNECTIVITY_CHECK')).toBeVisible()
  await expect(usage.getByText('成功')).toBeVisible()
  await expect(usage.getByText('用户自带密钥')).toBeVisible()

  // 4. 语言任务路由绑定到用户连接。
  await page.locator('.routing-row').first().locator('select')
    .selectOption({ label: 'E2E 主力（DeepSeek）' })
  await expect(page.locator('.routing-row').first().locator('select')).toHaveValue(/^\d+$/)
  await page.reload()
  await expect(page.locator('.routing-row').first().locator('select')).toHaveValue(/^\d+$/)

  // 5. 轮换凭据：生成新保险库条目并绑定到连接，旧条目保留。
  await page.getByRole('button', { name: '轮换凭据' }).click()
  await page.getByRole('form', { name: '轮换凭据' }).getByLabel('新 API Key').fill('sk-e2e-rotated-5678')
  await page.getByRole('button', { name: '确认轮换' }).click()
  await expect(page.getByTestId('credential-list').getByText('尾号 5678')).toBeVisible()
  await expect(page.getByTestId('credential-list').getByText('尾号 1234')).toBeVisible()

  // 6. 清理：删除连接与全部凭据，回到空状态。
  await page.locator('.connection-row').first().getByRole('button', { name: '删除' }).click()
  await expect(page.getByText('还没有模型连接。添加连接后才能启用与测试。')).toBeVisible()
  for (const tail of ['1234', '5678']) {
    const row = page.getByTestId('credential-list').locator('li', { hasText: `尾号 ${tail}` })
    await row.getByRole('button', { name: '删除' }).click()
  }
  await expect(page.getByText('还没有保存凭据。添加后，连接才能绑定它。')).toBeVisible()
})
