import { test, expect } from '@playwright/test'

// 平台管理员专用故事线：登记 MCP 服务 → 加白名单 → 停用 → 移除。
test.describe.configure({ mode: 'serial' })

async function loginAsAdmin(page) {
  const response = await page.request.post('/api/user/login', {
    data: { userAccount: 'companion_admin_e2e', userPassword: 'LocalUser123!' }
  })
  expect(response.ok(), `admin login failed: ${response.status()} ${await response.text()}`).toBeTruthy()
}

test('admin registers an mcp service and gates tools through the whitelist', async ({ page }) => {
  await loginAsAdmin(page)
  await page.goto('/model-gateway')
  await expect(page).toHaveURL(/\/model-gateway$/)
  const mcpSection = page.getByTestId('mcp-section')
  await expect(mcpSection).toBeVisible()
  await expect(page.getByText('还没有登记的 MCP 服务。未登记即不可达，任意 URL 不开放。')).toBeVisible()

  // 登记服务：默认停用，工具白名单为空（fail-closed）。
  await mcpSection.getByLabel('服务代码').fill('mxai-mcp-server')
  await mcpSection.getByLabel('服务名称').fill('MxAI 服务')
  await mcpSection.getByLabel('端点（仅 HTTPS）').fill('https://mcp.mxai.cn')
  await mcpSection.getByRole('button', { name: '登记服务' }).click()
  await expect(mcpSection.getByText('MxAI 服务')).toBeVisible()
  await expect(mcpSection.getByText(/已停用/)).toBeVisible()
  await expect(mcpSection.getByText(/fail-closed/)).toBeVisible()

  // 加白名单并启用服务与工具。
  await mcpSection.getByPlaceholder('例如 generate_image').fill('generate_image')
  await mcpSection.getByRole('button', { name: '加白名单' }).click()
  await expect(mcpSection.getByText('generate_image', { exact: true })).toBeVisible()
  const toolRow = mcpSection.locator('.mcp-tool-row').first()
  await mcpSection.getByRole('button', { name: '启用', exact: true }).click()
  await expect(mcpSection.getByText(/已启用/)).toBeVisible()
  await expect(toolRow.getByText('启用中', { exact: true })).toBeVisible()

  // 停用工具：条目保留但不再开放。
  await toolRow.getByRole('button', { name: '停用' }).click()
  await expect(toolRow.getByText('已停用', { exact: true })).toBeVisible()

  // 移除工具后回到 fail-closed 提示。
  await toolRow.getByRole('button', { name: '移出白名单' }).click()
  await expect(mcpSection.getByText(/fail-closed/)).toBeVisible()

  // 普通用户仍不可见：换用户登录后 MCP 区消失。
  const logout = await page.request.post('/api/user/logout')
  expect(logout.ok()).toBeTruthy()
  const loginResponse = await page.request.post('/api/user/login', {
    data: { userAccount: 'companion_e2e', userPassword: 'LocalUser123!' }
  })
  expect(loginResponse.ok()).toBeTruthy()
  await page.goto('/model-gateway')
  await expect(page.getByTestId('mcp-section')).toHaveCount(0)
})
